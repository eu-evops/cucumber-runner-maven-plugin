package eu.evops.maven.pluins.cucumber.parallel;

import cucumber.api.cli.Main;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Created by n450777 on 29/03/2016.
 */
public class ProcessInThread extends Thread {
    private Log log = new SystemStreamLog();

    private List<String> command;

    private Properties properties;

    private List<String> classpath;

    private int status;

    private File stdout;

    private File stderr;

    private String jvmArgs;
    private String workingDirectory;


    public ProcessInThread(List<String> arguments,
            String jvmArgs,
            List<String> classpath,
            Properties properties,
            String workingDirectory) {
        this.jvmArgs = jvmArgs;
        this.workingDirectory = workingDirectory;
        this.command = arguments;
        this.properties = properties;
        this.classpath = classpath;
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

        if(!jvmArgs.equalsIgnoreCase("")) {
            for (String jvmArg : jvmArgs.split("\\s+")) {
                System.out.println("Adding argument: " + jvmArg);
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
            status = process.waitFor();
        } catch (IOException e) {
            //
        } catch (InterruptedException e) {
            // e.printStackTrace();
        }
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
