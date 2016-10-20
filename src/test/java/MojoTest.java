import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

/**
 * Created by n450777 on 20/10/2016.
 */
public class MojoTest {

    @Test
    public void TestMojo() throws IOException, InterruptedException {
        String resource = new File(".").getAbsoluteFile().getParent() + "/test-project/pom.xml";
        System.out.println(resource);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("mvn", "clean", "test-compile", "cucumber-runner:run", "-f", resource);

        processBuilder.redirectError(new File("/tmp/mytest.err.log"));
        processBuilder.redirectOutput(new File("/tmp/mytest.out.log"));

        Process start = processBuilder.start();
        start.waitFor();
    }
}
