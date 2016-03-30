package eu.evops.maven.pluins.cucumber.parallel;

import cucumber.api.cli.Main;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by n450777 on 29/03/2016.
 */
public class ProcessInThread extends Thread {
    private List<String> command;

    private List<String> classpath;

    private int status;

    private File stdout;

    private File stderr;

    public ProcessInThread(List<String> command, String... classpath) {
        this.command = command;
        this.classpath = Arrays.asList(classpath);
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
                javaBin, "-cp", classpathString, className);

        for (String argument : this.command) {
            builder.command().add(argument);
        }

        if(this.stderr != null) {
            builder.redirectError(stderr);
        }

        if(this.stdout != null) {
            builder.redirectOutput(this.stdout);
        }

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
}
