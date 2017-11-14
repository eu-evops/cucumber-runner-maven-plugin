package eu.evops.maven.plugins.cucumber.parallel;

import eu.evops.maven.plugins.cucumber.parallel.reporting.MergeException;
import eu.evops.maven.plugins.cucumber.parallel.reporting.Merger;
import eu.evops.maven.plugins.cucumber.parallel.reporting.formatters.StreamingJSONFormatter;
import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

    @Parameter(property = "cucumberRunner.outputFolder", defaultValue = "${project.build.directory}/cucumber")
    private File outputFolder;
    /**
     * Where are the feature files, by default src/test/resources
     */
    @Parameter(property = "cucumberRunner.features")
    private List<String> features = Collections.singletonList("src/test/resources");

    /**
     * Only execute scenarios with these tags
     */
    @Parameter(property = "cucumberRunner.includeTags")
    private List<String> includeTags = Collections.emptyList();

    /**
     * Exclude these tags from running, for instance @wip, @manual
     */
    @Parameter(property = "cucumberRunner.excludeTags")
    private List<String> excludeTags = Collections.emptyList();

    /**
     * JVM Arguments that will be passed to executing JVM
     */
    @Parameter(property = "cucumberRunner.jvmArgs")
    private String jvmArgs = "";

    /**
     * How many times to rerun failing tests
     */
    @Parameter(property = "cucumberRunner.rerunFailedTests")
    private int rerunFailedTests = 0;

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
     * With enhanced json reporting, reports are generated after each scenario
     * and saved to disk, this ensures that in case of a Java crash, you get
     * reports for executed tests
     */
    @Parameter(property = "cucumberRunner.enhancedJsonReporting")
    boolean enhancedJsonReporting = false;

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

    /**
     * Controls failing the build if their are test failures. Needed for CI systems such
     * as Bamboo that allow quarantining of failing tests.
     */
    @Parameter(property = "cucumberRunner.test.failure.ignore")
    boolean testFailureIgnore;

    private File threadFolder;
    private String streamingFormatterClassName = StreamingJSONFormatter.class.getName();

    public void execute() throws MojoExecutionException, MojoFailureException {
        setThreadCount();
        setOutputFolder();

        getLog().debug(format("Running cucumber in %s", features));
        List<String> threadedArgs = getThreadGeneratorArguments(getThreadFolder());

        try {
            getLog().debug("Generating thread files");

            List<String> classpath = project
                    .getTestClasspathElements();
            classpath.addAll(getPluginDependencies());

            // generates thread files
            Properties threadGeneratorProperties = new Properties();
            threadGeneratorProperties.putAll(project.getProperties());
            ProcessInThread main = createProcess(threadedArgs, classpath, threadGeneratorProperties);
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

            List<ProcessInThread> threads = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                int threadId = i;
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

                Properties threadProperties = new Properties();
                threadProperties.putAll(project.getProperties());
                ProcessInThread thread = createProcess(threadArguments, classpath, threadProperties, i);
                thread.setLog(getLog());
                thread.setStdout(stdout);
                thread.setStderr(stderr);

                threads.add(thread);

                thread.onFinish(t -> {
                    String status = t.getStatus() == 0 ? "passed" : "failed";
                    getLog().info(format("- Thread %d/%d has finished with status: %s",
                            threadId + 1, threadCount, status));
                });

                thread.start();
            }

            getLog().info("Cucumber execution configuration:");
            getLog().info(format("- Features:          %s", String.join(", ", this.features)));
            getLog().info(format("- Included tags:     %s", String.join(", ", this.includeTags)));
            getLog().info(format("- Excluded tags:     %s", String.join(", ", this.excludeTags)));
            getLog().info(format("- Thread timeout:    %d minutes", this.threadTimeout));
            getLog().info(format("- Number of threads: %d", this.threadCount));
            getLog().info(format("- Report folder:     %s", this.outputFolder.getAbsolutePath()));
            getLog().info("========================================================================");
            getLog().info(format("Running cucumber with %d threads, each thread will run up to 1 hour ...", threads.size()));

            while (hasRunningThreads(threads)) {
                Thread.sleep(1000);
            }

            if(combineReports) {
                getLog().info("Generating combined reports");
                combineReports();
                report();
            }

            if(!testFailureIgnore && !haveAllThreadsPassed(threads)) {
                throw new MojoFailureException(format("Some of the threads have failed, please inspect output folder: %s", getThreadFolder().getAbsolutePath()));
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

    private ProcessInThread createProcess(List<String> arguments, List<String> classpath, Properties properties) {
        return createProcess(arguments, classpath, properties, -1);
    }

    private void combineReports() throws MergeException, MojoFailureException {
        for (String plugin : plugins) {
            String pluginName = plugin.split(":")[0];
            if(pluginName.matches(String.format("(json|junit|%s)", streamingFormatterClassName))) {
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
        List<String> combinedJsonFiles = Collections.singletonList(new File(getThreadFolder(), "combined.json").getAbsolutePath());
        generateReportForJsonFiles(combinedReportOutputDirectory, combinedJsonFiles);
    }

    private void generateReportForJsonFiles(File reportOutputDirectory,
            List<String> jsonFiles) {
        String jenkinsBasePath = "";
        String buildNumber = "1";
        String projectName = project.getName();

        Configuration configuration = new Configuration(reportOutputDirectory, projectName);
        configuration.setParallelTesting(false);
        configuration.setJenkinsBasePath(jenkinsBasePath);
        configuration.setRunWithJenkins(false);
        configuration.setBuildNumber(buildNumber);

        ReportBuilder reportBuilder = new ReportBuilder(jsonFiles, configuration);
        reportBuilder.generateReports();

        getLog().info(format("Cucumber HTML report has been generated at: %s/cucumber-html-reports/overview-features.html", reportOutputDirectory.getAbsolutePath()));
    }

    private boolean hasRunningThreads(List<ProcessInThread> threads) {
        return threads
                .stream()
                .filter(Thread::isAlive)
                .toArray()
                .length > 0;
    }

    private boolean haveAllThreadsPassed(List<ProcessInThread> threads) {
        return threads
                .stream()
                .filter(t -> t.getStatus() != 0)
                .toArray()
                .length == 0;
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

        // check if json and streaming json are present
        if(plugins.contains("json:") && plugins.contains(streamingFormatterClassName + ":")) {
            throw new RuntimeException("Cannot use json and streaming json formatters together");
        }

        for (String plugin : plugins) {
            args.add(CucumberArguments.Plugin.getArg());

            // If plugin ends with semicolon, I will generate report name under thread folder
            if(plugin.endsWith(":")) {
                String[] pluginDetails = plugin.split(":");
                String pluginName = pluginDetails[0];

                // if streaming enabled
                if(pluginName.equalsIgnoreCase("json") && enhancedJsonReporting) {
                    pluginName = streamingFormatterClassName;
                }

                File threadedReportFile = new File(threadFolder, "reports/" +
                        getReportFileName(pluginName));
                args.add(format("%s:%s", pluginName, threadedReportFile.getAbsolutePath()));
            } else {
                args.add(plugin.replace("%thread%", String.valueOf(threadNumber)));
            }
        }

        if(rerunFailedTests > 0) {
            args.add(CucumberArguments.Plugin.getArg());
            args.add(format("rerun:%s", new File(threadFolder, "rerun.txt").getAbsolutePath()));
        }

        for (String gluePath : gluePaths) {
            args.add(CucumberArguments.Glue.getArg());
            args.add(gluePath);
        }

        getLog().debug(format("Thread arguments: %s", args));

        return args;
    }

    private String getReportFileName(String formatterName) {
        String streamingJsonFormatterClassName = StreamingJSONFormatter.class.getName();
        if (formatterName.equals("json") || formatterName.equals(streamingJsonFormatterClassName)) {
            return "report.json";
        } else if (formatterName.equals("junit")) {
            return "report.xml";
        } else if (formatterName.equals("rerun")) {
            return "rerun.txt";
        } else {
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

        try {
            FileUtils.deleteDirectory(threadFolder);
        } catch (IOException e) {
            throw new MojoFailureException(
                    format("Cannot delete thread folder: %s", threadFolder
                    .getAbsolutePath()), e);
        }

        if(!threadFolder.mkdirs()) {
            throw new MojoFailureException(
                    format("Could not create thread folder at: %s", threadFolder
                            .getAbsolutePath()));

        }

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

    private ProcessInThread createProcess(List<String> arguments, List<String> classpath, Properties properties, int threadNumber) {
        String workingDirectory = project.getBasedir().getAbsolutePath();

        HashMap<String, String> environmentVariables = new HashMap<>();
        if(threadNumber > -1) {
            properties.put("cucumberRunner.threadNumber", String.valueOf(threadNumber));
            properties.put("cucumberRunner.threadCount", String.valueOf(threadCount));
            environmentVariables.put("THREAD_NUMBER", String.valueOf(threadNumber));
            environmentVariables.put("THREAD_COUNT", String.valueOf(threadCount));
        }

        return new ProcessInThread(arguments, jvmArgs, classpath, properties,
                environmentVariables, workingDirectory, threadTimeout);
    }

    private List<String> getThreadGeneratorArguments(File threadFolder) {
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
            if(tag == null) {
                continue;
            }
            args.add(CucumberArguments.Tags.getArg());
            args.add(format("~%s", tag));
        }

        for (String tag : includeTags) {
            if(tag == null) {
                continue;
            }
            args.add(CucumberArguments.Tags.getArg());
            args.add(tag);
        }

        args.addAll(features);

        for (String scenarioName : scenarioNames) {
            args.add(CucumberArguments.ScenarioName.getArg());
            args.add(scenarioName);
        }

        return args;
    }

    private boolean isClassPathOrRerun(String feature) {
        return feature.startsWith("classpath:") || feature.startsWith("@");
    }

    private List<String> getFeatureFolders() {
        List<String> result = new ArrayList<>();

        for (String feature : features) {
            if(!isClassPathOrRerun(feature)) {
                result.add(feature.replace(".feature$", ""));
            }
        }

        return result;
    }
}
