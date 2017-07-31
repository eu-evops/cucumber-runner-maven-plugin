import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * Created by n450777 on 20/10/2016.
 */
public class MojoTest {
    File testProjectDirectory = new File(".", "test-project").getAbsoluteFile();

    @Test
    public void  testStreamingCombinedHtmlFolderIsGeneratedWhenAThreadIsStopped() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testProjectDirectory);
        processBuilder.command("mvn", "integration-test", "-DuseEnhancedJsonReporting=true");

        Process start = processBuilder.start();
        start.waitFor();

        File cucumberHtmlReports = new File(testProjectDirectory, "target/cucumber/combined-html/cucumber-html-reports");
        assertTrue("Streaming Combined Html Reports are not generated when all/one of the cucumber threads interrupted",
                cucumberHtmlReports.exists());
    }

    @Test
    public void testCombinedHtmlFolderIsGeneratedWhenAThreadIsStopped() throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testProjectDirectory);
        processBuilder.command("mvn", "integration-test");

        Process start = processBuilder.start();
        start.waitFor();

        System.out.print("Validate Combined-html folder");
        String cucumberPath = String.format("%s/test-project/target/cucumber/combined-html/cucumber-html-reports",
                new File("").getAbsolutePath());
        File cucumberHtmlReports = new File(cucumberPath);
        assertTrue("Combined Html Reports are not generated when all/one of the cucumber threads interrupted",
                cucumberHtmlReports.exists());
    }
}
