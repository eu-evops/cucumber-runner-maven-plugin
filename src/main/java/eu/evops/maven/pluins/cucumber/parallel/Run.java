package eu.evops.maven.pluins.cucumber.parallel;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Say hi to the user
 * Created by n450777 on 29/03/2016.
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.TEST)
public class Run extends AbstractMojo {
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter( readonly = true, defaultValue = "${plugin.artifacts}" )
    private List<Artifact> pluginDependencies;


    @Parameter
    private File outputFolder;
    /**
     * Where are the feature files, by default src/test/resources
     */
    @Parameter
    private List<String> features = Arrays.asList("src/test/resources");

    /**
     * Only execute scenarios with these tags
     */
    @Parameter
    private List<String> includeTags = Arrays.asList();

    /**
     * Exclude these tags from running, for instance @wip, @manual
     */
    @Parameter
    private List<String> excludeTags = Arrays.asList();

    /**
     * List of glue
     */
    @Parameter
    private List<String> gluePaths = new ArrayList<>();

    /**
     * List of plugins in a format of plugin[:path or url]
     */
    @Parameter
    private List<String> plugins = new ArrayList<>();

    /**
     * List of scenario name regex expressions to execute
     */
    @Parameter
    private List<String> scenarioNames = new ArrayList<>();

    /**
     * Whether to execute dry run
     */
    @Parameter
    boolean dryRun = false;

    /**
     * Don't colour terminal output.
     */
    @Parameter
    boolean monochrome = true;

    /**
     * Treat undefined and pending steps as errors.
     */
    @Parameter
    boolean strict = true;

    /**
     * Number of processes to fork in parallel
     */
    @Parameter
    int threadCount;

    public void execute() throws MojoExecutionException, MojoFailureException {
        setThreadCount();

        getLog().debug(String.format("Running cucumber in %s", features));
        List<String> threadedArgs = getThreadGeneratorArguments(getThreadFolder());

        try {
            getLog().debug(String.format("Generating thread files", threadedArgs));

            List<String> classpath = project
                    .getTestClasspathElements();
            classpath.addAll(getPluginDependencies());


            // generates thread files
            ProcessInThread main = runAsProcess(threadedArgs, classpath.toArray(new String[0]));
            main.setStderr(new File(getThreadFolder(), "generator-stderr.log"));
            main.setStdout(new File(getThreadFolder(), "generator-stdout.log"));
            main.start();
            main.join(10000);

            if(main.getStatus() != 0) {
                throw new MojoFailureException("Failed to generate thread files");
            }

            getLog().debug("Thread files have been generated");

            classpath.addAll(getFeatureFolders());

            List<ProcessInThread> threads = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                List<String> threadArguments = getThreadArguments(
                        getThreadFolder(), i);

                File stdout = new File(String.format("%s/thread-%d/stdout.log",
                        getThreadFolder().getAbsolutePath(), i));
                File stderr = new File(String.format("%s/thread-%d/stderr.log",
                        getThreadFolder().getAbsolutePath(), i));

                ProcessInThread thread = runAsProcess(threadArguments, classpath.toArray(new String[0]));
                thread.setStdout(stdout);
                thread.setStderr(stderr);

                thread.start();
                threads.add(thread);
            }

            getLog().info(String.format("Running cucumber with %d threads, each thread will run up to 1 hour", threads.size()));

            for (ProcessInThread thread : threads) {
                thread.join(1 * 60 * 60 * 1000);
                getLog().debug(String.format("Thread %s finished", thread));
            }

            for (ProcessInThread thread : threads) {
                if(thread.getStatus() != 0) {
                    throw new MojoFailureException(String.format("Some of the threads have failed, please inspect output folder: %s", getThreadFolder().getAbsolutePath()));
                }
            }
        }
        // Just rethrow mojo exceptions
        catch(MojoFailureException mf) {
            throw mf;
        }
        catch (Throwable throwable) {
            throw new MojoFailureException("Error generating cucumber sets",
                    throwable);
        }
    }

    private List<String> getPluginDependencies() {
        ArrayList<String> pluginClasspath = new ArrayList<>();
        for (Artifact pluginDependency : pluginDependencies) {
            pluginClasspath.add(pluginDependency.getFile().getAbsolutePath());
        }
        return pluginClasspath;
    }

    private List<String> getThreadArguments(File threadsFolder, int threadNumber) {
        List<String> args = getCommonArguments();
        File threadFolder = new File(threadsFolder,
                String.format("thread-%d", threadNumber));

        args.add(String.format("@%s", new File(threadFolder, "run").getAbsolutePath()));

        args.add(CucumberArguments.Monochrome.getArg());

        if(strict) {
            args.add(CucumberArguments.Strict.getArg());
        }

        for (String plugin : plugins) {
            args.add(CucumberArguments.Plugin.getArg());
            if(plugin.endsWith(":")) {
                File threadedReportFile = new File(threadFolder, "reports/" + plugin.split(":")[0]);
                args.add(String.format("%s%s", plugin, threadedReportFile.getAbsolutePath()));
            } else {
                args.add(plugin.replace("%thread%", String.valueOf(threadNumber)));
            }
        }

        for (String gluePath : gluePaths) {
            args.add(CucumberArguments.Glue.getArg());
            args.add(gluePath);
        }

        getLog().debug(String.format("Thread arguments: %s", args));

        return args;
    }

    private File getThreadFolder() throws MojoFailureException {
        File threadFolder = new File(project.getBuild().getDirectory(), "cucumber/threads");
        if(outputFolder != null) {
            threadFolder = outputFolder;
            getLog().warn(String.format("This folder will be deleted in about 10 seconds, press CTRL+C to stop"));
            getLog().warn(String.format("- %s", threadFolder.getAbsolutePath()));
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new MojoFailureException(String.format("Interrupted deletion of %s", threadFolder.getAbsolutePath()));
            }
        }

        threadFolder.delete();
        threadFolder.mkdirs();
        return threadFolder;
    }

    private void setThreadCount() {
        if (threadCount == 0) {
            threadCount = Runtime.getRuntime().availableProcessors();
        }

        // Set number of threads to user specified value, otherwise use default which is set in the formatter
        System.setProperty(
                "cucumber-parallel-execution.threads",
                String.valueOf(threadCount));
    }

    private List<String> getCommonArguments() {
        ArrayList<String> arguments = new ArrayList<>();
        if(dryRun) {
            arguments.add(CucumberArguments.DryRun.getArg());
        }

        return arguments;
    }

    private ProcessInThread runAsProcess(List<String> arguments, String... classpath) {
        return new ProcessInThread(arguments, classpath);
    }

    public List<String> getThreadGeneratorArguments(File threadFolder) {
        List<String> args = getCommonArguments();
        args.add(CucumberArguments.DryRun.getArg());

        args.add(CucumberArguments.Plugin.getArg());
        args.add(String.format(
                "%s:%s",
                Formatter.class.getCanonicalName(),
                threadFolder.getAbsolutePath()
        ));

        for (String tag : excludeTags) {
            args.add(CucumberArguments.Tags.getArg());
            args.add(String.format("~%s", tag));
        }

        for (String tag : includeTags) {
            args.add(CucumberArguments.Tags.getArg());
            args.add(tag);
        }

        for (String feature : features) {
            args.add(feature);
        }

        for (String scenarioName : scenarioNames) {
            args.add(CucumberArguments.ScenarioName.getArg());
            args.add(scenarioName);
        }

        return args;
    }

    private boolean isClassPathOrRerun(String feature) {
        return feature.startsWith("classpath:") || feature.startsWith("@");
    }

    public List<String> getFeatureFolders() {
        List<String> result = new ArrayList<>();

        for (String feature : features) {
            if(!isClassPathOrRerun(feature)) {
                result.add(feature.replace(".feature$", ""));
            }
        }

        return result;
    }
}
