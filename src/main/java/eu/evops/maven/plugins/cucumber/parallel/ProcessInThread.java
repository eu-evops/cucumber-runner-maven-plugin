package eu.evops.maven.plugins.cucumber.parallel;

import cucumber.api.cli.Main;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Created by n450777 on 29/03/2016.
 */
public class ProcessInThread extends Thread {
    private Log log = new SystemStreamLog();

    private List<String> command;

    private Properties properties;

    private List<String> classpath;
    private int threadTimeout;

    private int status;

    private File stdout;

    private File stderr;

    private String jvmArgs;
    private HashMap<String, String> environmentVariables;
    private String workingDirectory;

    private Consumer<ProcessInThread> finishCallback;

    ProcessInThread(List<String> arguments,
                    String jvmArgs,
                    List<String> classpath,
                    Properties properties,
                    HashMap<String, String> environmentVariables,
                    String workingDirectory,
                    int threadTimeout) {
        this.jvmArgs = jvmArgs;
        this.environmentVariables = environmentVariables;
        this.workingDirectory = workingDirectory;
        this.command = arguments;
        this.properties = properties;
        this.classpath = classpath;
        this.threadTimeout = threadTimeout;
        this.status = -1;
    }

    /**
     * If this thread was constructed using a separate
     * <code>Runnable</code> run object, then that
     * <code>Runnable</code> object's <code>run</code> method is called;
     * otherwise, this method does nothing and returns.
     * <p>
     * Subclasses of <code>Thread</code> should override this method.
     *
     * @see #start()
     * @see #stop()
     */
    @Override
    public void run() {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome +
                File.separator + "bin" +
                File.separator + "java";
        String className = Main.class.getCanonicalName();

        String classpathString;
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < this.classpath.size(); i++) {
            if (i != 0) {
                stringBuilder.append(File.pathSeparator);
            }

            stringBuilder.append(this.classpath.get(i));
        }

        classpathString = stringBuilder.toString();

        ProcessBuilder builder = new ProcessBuilder(
                javaBin);
        builder.directory(new File(workingDirectory));

        // Add environment variables to the process
        builder.environment().putAll(environmentVariables);

        if(!jvmArgs.equalsIgnoreCase("")) {
            for (String jvmArg : jvmArgs.split("\\s+")) {
                builder.command().add(jvmArg);
            }
        }

        builder.command().add("-cp");
        builder.command().add(classpathString);

        Properties properties = this.properties;
        properties.putAll(System.getProperties());
        for (Map.Entry<Object, Object> entry : properties
                .entrySet()) {
            // we don't want to copy java properties
            if(entry.getKey().toString().startsWith("java.")) {
                continue;
            }
            builder.command().add(String.format("-D%s=%s", entry.getKey(), entry.getValue()));
        }

        builder.command().add(className);

        for (String argument : this.command) {
            builder.command().add(argument);
        }

        if(this.stderr != null) {
            builder.redirectError(stderr);
        }

        if(this.stdout != null) {
            builder.redirectOutput(this.stdout);
        }

        log.debug(String.format("Running command: %s", builder.command()));

        Process process;
        try {
            process = builder.start();
            if(process.waitFor(threadTimeout, TimeUnit.MINUTES)) {
                status = process.exitValue();
            } else {
                // Process timed out, terminate and set error code to 2
                this.log.error("Cucumber process timed out, terminating...");
                process.destroy();
                status = 2;
            }
        } catch (IOException | InterruptedException e) {
            this.log.error("Error running thread");
            status = 1;
        } finally {
            if(this.finishCallback != null) {
                this.finishCallback.accept(this);
            }
        }
    }

    void onFinish(Consumer<ProcessInThread> predicate) {
        this.finishCallback = predicate;
    }

    public int getStatus() {
        return status;
    }

    void setStdout(File stdout) {
        this.stdout = stdout;
    }

    void setStderr(File stderr) {
        this.stderr = stderr;
    }

    public void setLog(org.apache.maven.plugin.logging.Log log) {
        this.log = log;
    }
}
