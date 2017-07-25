import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * Created by n450777 on 20/10/2016.
 */
public class MojoTest {

    @Test
    public void  testStreamingCombinedHtmlFolderIsGeneratedWhenAThreadIsStopped() throws IOException, InterruptedException {
        String resource = String.format("%s/test-project/pom.xml", new File(".").getAbsoluteFile().getParent());
        System.out.println(resource);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("mvn", "clean", "integration-test", "-f", resource);

        processBuilder.redirectError(new File("/tmp/mytest.err.log"));
        processBuilder.redirectOutput(new File("/tmp/mytest.out.log"));

        Process start = processBuilder.start();
        start.waitFor();

        System.out.print("Validate Streaming Combined-html folder");
        String cucumberPath = String.format("%s/test-project/target/cucumber/streaming-combined-html/cucumber-html-reports",
                new File("").getAbsolutePath());
        File cucumberHtmlReports = new File(cucumberPath);
        assertTrue("Streaming Combined Html Reports are not generated when all/one of the cucumber threads interrupted",
                cucumberHtmlReports.exists());
    }

    @Test
    public void testCombinedHtmlFolderIsGeneratedWhenAThreadIsStopped() throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        String resource = String.format("%s/test-project/pom.xml", new File(".").getAbsoluteFile().getParent());
        System.out.println(resource);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("mvn", "clean", "integration-test", "-f", resource,"-DcucumberRunner.streamingFormatter=false");

        processBuilder.redirectError(new File("/tmp/mytest.err.log"));
        processBuilder.redirectOutput(new File("/tmp/mytest.out.log"));

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
