package eu.evops.maven.pluins.cucumber.parallel;

import cucumber.api.cli.Main;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

import java.io.File;
import java.io.IOException;
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

    private String workingDirectory;
    private Consumer<ProcessInThread> finishCallback;


    public ProcessInThread(List<String> arguments,
                           List<String> classpath,
                           Properties properties,
                           String workingDirectory,
                           int threadTimeout) {
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
                javaBin, "-cp", classpathString);
        builder.directory(new File(workingDirectory));

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
            process.waitFor(threadTimeout, TimeUnit.SECONDS);
            status = process.exitValue();
        } catch (IOException e) {
            //
        } catch (InterruptedException e) {
            // e.printStackTrace();
        } finally {
            if(this.finishCallback != null) {
                this.finishCallback.accept(this);
            }
        }
    }

    public void onFinish(Consumer<ProcessInThread> predicate) {
        this.finishCallback = predicate;
    }

    public int getStatus() {
        return status;
    }

    public void setStdout(File stdout) {
        this.stdout = stdout;
    }

    public void setStderr(File stderr) {
        this.stderr = stderr;
    }

    public void setLog(org.apache.maven.plugin.logging.Log log) {
        this.log = log;
    }
}
