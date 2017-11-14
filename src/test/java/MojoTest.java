import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.readAllBytes;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Created by n450777 on 20/10/2016.
 */
public class MojoTest {
    private File testProjectDirectory = new File(".", "test-project").getAbsoluteFile();
    private File testProjectWithJvmArgs = new File(".", "test-project-jvm-args").getAbsoluteFile();
    private File testProjectToIgnoreTestFailures = new File(".", "test-project-testFailureIgnore").getAbsoluteFile();

    @Test
    public void testStreamingCombinedHtmlFolderIsGeneratedWhenAThreadIsStopped() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testProjectDirectory);
        processBuilder.command("mvn", "clean", "integration-test", "-DuseEnhancedJsonReporting=true");

        Process start = processBuilder.start();
        start.waitFor();

        File cucumberHtmlReports = new File(testProjectDirectory, "target/cucumber/combined-html/cucumber-html-reports");
        assertTrue("Streaming Combined Html Reports are not generated when all/one of the cucumber threads interrupted",
                cucumberHtmlReports.exists());
    }

    @Test
    public void testThreadNumberPropertyAndEnvironmentVariableIsPassed() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testProjectDirectory);
        processBuilder.command("mvn", "clean", "integration-test");
        ProcessBuilder.Redirect pipe = ProcessBuilder.Redirect.PIPE;
        processBuilder.redirectOutput(pipe);

        Process process = processBuilder.start();
        process.waitFor();

        int threadCount = 4;
        for (int i = 0; i < threadCount; i++) {
            String errOutPath = String.format("%s/test-project/target/cucumber/threads/thread-%d/stderr.log",
                    new File("").getAbsolutePath(), i);

            Path path = Paths.get(errOutPath);
            String errOutput = new String(readAllBytes(path));

            Assert.assertThat(errOutput, containsString(String.format("System property: thread number: %d", i)));
            Assert.assertThat(errOutput, containsString(String.format("System property: thread count: %d", threadCount)));
            Assert.assertThat(errOutput, containsString(String.format("Environment variable: thread number: %d", i)));
            Assert.assertThat(errOutput, containsString(String.format("Environment variable: thread count: %d", threadCount)));
        }

    }


    @Test
    public void testThatJvmArgsArePassed() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testProjectWithJvmArgs);
        processBuilder.command("mvn", "clean", "integration-test");
        ProcessBuilder.Redirect pipe = ProcessBuilder.Redirect.PIPE;
        processBuilder.redirectOutput(pipe);

        Process process = processBuilder.start();
        process.waitFor();

        String errOutPath = String.format("%s/test-project-jvm-args/target/cucumber/threads/generator-stderr.log",
                new File("").getAbsolutePath());

        Path path = Paths.get(errOutPath);
        String errOutput = new String(readAllBytes(path));

        assertThat(errOutput, containsString("java version"));
    }

    @Test
    public void testCombinedHtmlFolderIsGeneratedWhenAThreadIsStopped() throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testProjectDirectory);
        processBuilder.command("mvn", "clean", "integration-test");

        Process start = processBuilder.start();
        start.waitFor();

        String cucumberPath = String.format("%s/test-project/target/cucumber/combined-html/cucumber-html-reports",
                new File("").getAbsolutePath());
        File cucumberHtmlReports = new File(cucumberPath);
        assertTrue("Combined Html Reports are not generated when all/one of the cucumber threads interrupted",
                cucumberHtmlReports.exists());
    }

    @Test
    public void testThatTestFailuresAreNotIgnoredWhenTestFailureIgnoreIsFalse() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testProjectToIgnoreTestFailures);
        processBuilder.command("mvn", "clean", "integration-test", "-DcucumberRunner.test.failure.ignore=false");

        Process start = processBuilder.start();
        start.waitFor();

        String lines;

        try ( BufferedReader reader = new BufferedReader(new InputStreamReader(start.getInputStream())) ) {
            lines = String.join(System.lineSeparator(), reader.lines().collect(toList()));
        }

        assertTrue(lines.contains("BUILD FAILURE"));
        assertTrue(lines.contains("Some of the threads have failed, please inspect output folder:"));
    }

    @Test
    public void testThatTestFailuresAreIgnoredWhenTestFailureIgnoreIsTrue() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testProjectToIgnoreTestFailures);
        processBuilder.command("mvn", "clean", "integration-test", "-DcucumberRunner.test.failure.ignore=true");

        Process start = processBuilder.start();
        start.waitFor();

        String lines;

        try ( BufferedReader reader = new BufferedReader(new InputStreamReader(start.getInputStream())) ) {
            lines = String.join(System.lineSeparator(), reader.lines().collect(toList()));
        }

        System.out.println(lines);

        assertTrue(lines.contains("BUILD SUCCESS"));
    }
}
