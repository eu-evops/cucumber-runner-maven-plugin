package eu.evops.maven.pluins.cucumber.parallel;

import eu.evops.maven.pluins.cucumber.parallel.reporting.MergeException;
import eu.evops.maven.pluins.cucumber.parallel.reporting.Merger;
import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;
import net.masterthought.cucumber.ValidationException;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.logging.log4j.core.util.NullOutputStream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import static java.lang.String.format;

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

    @Parameter(property = "cucumberRunner.outputFolder")
    private File outputFolder;
    /**
     * Where are the feature files, by default src/test/resources
     */
    @Parameter(property = "cucumberRunner.features")
    private List<String> features = Arrays.asList("src/test/resources");

    /**
     * Only execute scenarios with these tags
     */
    @Parameter(property = "cucumberRunner.includeTags")
    private List<String> includeTags = Arrays.asList();

    /**
     * Exclude these tags from running, for instance @wip, @manual
     */
    @Parameter(property = "cucumberRunner.excludeTags")
    private List<String> excludeTags = Arrays.asList();

    /**
     * List of glue
     */
    @Parameter(property = "cucumberRunner.gluePaths")
    private List<String> gluePaths = new ArrayList<>();

    /**
     * List of plugins in a format of plugin[:path or url]
     */
    @Parameter(property = "cucumberRunner.plugins")
    private List<String> plugins = new ArrayList<>();

    /**
     * List of scenario name regex expressions to execute
     */
    @Parameter(property = "cucumberRunner.scenarioNames")
    private List<String> scenarioNames = new ArrayList<>();

    /**
     * Thread timeout in minutes
     */
    @Parameter(property = "cucumberRunner.threadTimeout")
    private int threadTimeout = 60;

    /**
     * Scenario generator timeout in seconds
     */
    @Parameter(property = "cucumberRunner.scenarioGeneratorTimeout")
    private int scenarioGeneratorTimeout = 10;

    /**
     * Whether to execute dry run
     */
    @Parameter(property = "cucumberRunner.dryRun")
    boolean dryRun = false;

    /**
     * Don't colour terminal output.
     */
    boolean monochrome = true;

    /**
     * Treat undefined and pending steps as errors.
     */
    @Parameter
    boolean strict = true;

    /**
     * Number of processes to fork in parallel
     */
    @Parameter(property = "cucumberRunner.threadCount")
    int threadCount;

    /**
     * Will use reporter merge facility to comine json and junit reports (only if
     * they were specified in the plugin section)
     */
    @Parameter(property = "cucumberRunner.combineReports")
    boolean combineReports;

    private File threadFolder;

    // By default it fails
    private int status = 1;

    public void execute() throws MojoExecutionException, MojoFailureException {
        setThreadCount();
        setOutputFolder();

        getLog().debug(format("Running cucumber in %s", features));
        List<String> threadedArgs = getThreadGeneratorArguments(getThreadFolder());

        try {
            getLog().debug(format("Generating thread files", threadedArgs));

            List<String> classpath = project
                    .getTestClasspathElements();
            classpath.addAll(getPluginDependencies());

            // generates thread files
            ProcessInThread main = createProcess(threadedArgs, classpath, project.getProperties());
            main.setLog(getLog());
            main.setStderr(new File(getThreadFolder(), "generator-stderr.log"));
            main.setStdout(new File(getThreadFolder(), "generator-stdout.log"));
            main.start();
            main.join(scenarioGeneratorTimeout * 1000);
            if(main.getState() != Thread.State.TERMINATED) {
                getLog().error(format("The generator thread timed out %s", main));
                main.interrupt();
            }

            if(main.getStatus() != 0) {
                throw new MojoFailureException("Failed to generate thread files");
            }

            getLog().debug("Thread files have been generated");

            classpath.addAll(getFeatureFolders());

            final List<ProcessInThread> threads = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                File threadFolder = getThreadFolder(getThreadFolder(), i);
                if(!threadFolder.exists()) {
                    getLog().warn(format(
                            "Thread folder does not exist: %s. It is possible that there were less scenarios than number of threads required", threadFolder.getAbsolutePath()));
                    continue;
                }
                List<String> threadArguments = getThreadArguments(
                        getThreadFolder(), i);

                File stdout = new File(format("%s/thread-%d/stdout.log",
                        getThreadFolder().getAbsolutePath(), i));
                File stderr = new File(format("%s/thread-%d/stderr.log",
                        getThreadFolder().getAbsolutePath(), i));

                ProcessInThread thread = createProcess(threadArguments, classpath, project.getProperties());
                thread.setLog(getLog());
                thread.setStdout(stdout);
                thread.setStderr(stderr);

                thread.start();
                threads.add(thread);
            }

            getLog().info(format("Running cucumber with %d threads, each thread will run up to 1 hour", threads.size()));

            // Force cucumber report classes to be loaded into memory as they won't be added in the shutdown hook
            Class dummy = Configuration.class;
            dummy = IOUtil.class;
            dummy = ValidationException.class;
            dummy = ReportBuilder.class;
            dummy = NullOutputStream.class;
            dummy = DirectoryFileFilter.class;
            dummy = NameFileFilter.class;
            dummy = IOCase.class;

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        System.out.println("Cucumber runner finished");
                        finalise(threads);
                    } catch (MergeException e) {
                        e.printStackTrace();
                    } catch (MojoFailureException e) {
                        e.printStackTrace();
                    }
                }
            });

            for (ProcessInThread thread : threads) {
                thread.join(threadTimeout * 60 * 1000);
                getLog().debug(format("Thread %s finished", thread));
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

        System.exit(status);
    }

    private void finalise(List<ProcessInThread> threads) throws MergeException, MojoFailureException {
        for(ProcessInThread process : threads) {
            if(process.isAlive()) {
                this.status = 1;
                process.stopRunning();
            }
        }

        getLog().info("Thread status:");
        boolean passing = true;
        for (ProcessInThread thread : threads) {
            String status = thread.getStatus() == 0 ? "passed" : "failed";
            getLog().info(format("   Status for %s: %s", thread, status));
            if(thread.getStatus() != 0) {
                passing = false;
                this.status = 1;
            }
        }

        if(combineReports) {
            getLog().info("Generating combined reports");
            combineReports();
            report();
        }

        if(!passing) {
            throw new MojoFailureException(format("Some of the threads have failed, please inspect output folder: %s", getThreadFolder().getAbsolutePath()));
        }
    }

    private void combineReports() throws MergeException, MojoFailureException {
        for (String plugin : plugins) {
            String pluginName = plugin.split(":")[0];
            if(pluginName.matches("^(json|junit)")) {
                Merger.get(pluginName).merge(getThreadFolder(), findReports(getReportFileName(pluginName)));
            }
        }
    }

    private List<String> findReports(String reportFileName)
            throws MojoFailureException {
        List<String> reportFiles = new ArrayList<>();

        NameFileFilter nameFileFilter = new NameFileFilter(reportFileName);
        Iterator<File> files = org.apache.commons.io.FileUtils
                .iterateFiles(getThreadFolder(),
                        nameFileFilter, DirectoryFileFilter.DIRECTORY);

        while(files.hasNext()) {
            reportFiles.add(files.next().getAbsolutePath());
        }

        return reportFiles;
    }

    private void setOutputFolder() {

    }

    private void report() throws MojoFailureException {
        File combinedReportOutputDirectory = new File(project.getBuild().getDirectory(), "cucumber/combined-html");
        List<String> combinedJsonFiles = Arrays.asList(new File(getThreadFolder(), "combined.json").getAbsolutePath());
        generateReportForJsonFiles(combinedReportOutputDirectory, combinedJsonFiles);
    }

    private void generateReportForJsonFiles(File reportOutputDirectory,
            List<String> jsonFiles) {
        String jenkinsBasePath = "";
        String buildNumber = "1";
        String projectName = project.getName();
        boolean skippedFails = true;
        boolean pendingFails = false;
        boolean undefinedFails = true;
        boolean missingFails = true;
        boolean runWithJenkins = false;
        boolean parallelTesting = false;


        Configuration configuration = new Configuration(reportOutputDirectory, projectName);
// optionally only if you need
        configuration.setStatusFlags(skippedFails, pendingFails, undefinedFails);
        configuration.setParallelTesting(parallelTesting);
        configuration.setJenkinsBasePath(jenkinsBasePath);
        configuration.setRunWithJenkins(runWithJenkins);
        configuration.setBuildNumber(buildNumber);

        ReportBuilder reportBuilder = new ReportBuilder(jsonFiles, configuration);
        reportBuilder.generateReports();
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
        File threadFolder = getThreadFolder(threadsFolder, threadNumber);

        args.add(format("@%s", new File(threadFolder, "run").getAbsolutePath()));

        args.add(CucumberArguments.Monochrome.getArg());

        if(strict) {
            args.add(CucumberArguments.Strict.getArg());
        }

        for (String plugin : plugins) {
            args.add(CucumberArguments.Plugin.getArg());

            // If plugin ends with semicolon, I will generate report name under thread folder
            if(plugin.endsWith(":")) {
                File threadedReportFile = new File(threadFolder, "reports/" +
                        getReportFileName(plugin.split(":")[0]));
                args.add(format("%s%s", plugin, threadedReportFile.getAbsolutePath()));
            } else {
                args.add(plugin.replace("%thread%", String.valueOf(threadNumber)));
            }
        }

        for (String gluePath : gluePaths) {
            args.add(CucumberArguments.Glue.getArg());
            args.add(gluePath);
        }

        getLog().debug(format("Thread arguments: %s", args));

        return args;
    }

    private String getReportFileName(String formatterName) {
        switch(formatterName) {
        case "json":
            return "report.json";
        case "junit":
            return "report.xml";
        case "rerun":
            return "rerun.txt";
        default:
            return formatterName;
        }
    }

    private File getThreadFolder(File threadsFolder, int threadNumber) {
        return new File(threadsFolder,
                format("thread-%d", threadNumber));
    }

    private File getThreadFolder() throws MojoFailureException {
        if(threadFolder != null) {
            return threadFolder;
        }

        threadFolder = new File(project.getBuild().getDirectory(), "cucumber/threads");
        if(outputFolder != null) {
            threadFolder = outputFolder;
            getLog().warn(format("This folder will be deleted in about 10 seconds, press CTRL+C to stop"));
            getLog().warn(format("- %s", threadFolder.getAbsolutePath()));
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new MojoFailureException(
                        format("Interrupted deletion of %s", threadFolder
                        .getAbsolutePath()));
            }
        }

        try {
            FileUtils.deleteDirectory(threadFolder);
        } catch (IOException e) {
            throw new MojoFailureException(
                    format("Cannot delete thread folder: %s", threadFolder
                    .getAbsolutePath()), e);
        }

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

    private ProcessInThread createProcess(List<String> arguments, List<String> classpath, Properties properties) {
        String workingDirectory = project.getBasedir().getAbsolutePath();
        return new ProcessInThread(arguments, classpath, properties, workingDirectory);
    }

    public List<String> getThreadGeneratorArguments(File threadFolder) {
        List<String> args = getCommonArguments();
        args.add(CucumberArguments.DryRun.getArg());

        args.add(CucumberArguments.Plugin.getArg());
        args.add(format(
                "%s:%s",
                Formatter.class.getCanonicalName(),
                threadFolder.getAbsolutePath()
        ));

        // We don't need to see color coding in the log files
        args.add(CucumberArguments.Monochrome.getArg());

        for (String tag : excludeTags) {
            args.add(CucumberArguments.Tags.getArg());
            args.add(format("~%s", tag));
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
