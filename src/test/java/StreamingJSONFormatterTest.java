import cucumber.api.java.Before;
import eu.evops.maven.pluins.cucumber.parallel.reporting.JsonResultMerger;
import eu.evops.maven.pluins.cucumber.parallel.reporting.MergeException;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jdom2.JDOMException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by n471306 on 19/07/2017.
 */
public class StreamingJSONFormatterTest {

    public File testProjectDirectory = new File(".", "test-project").getAbsoluteFile();
    public File pomFile = new File(testProjectDirectory, "pom.xml");
    public JsonResultMerger jsonResultMerger;
    public List<String> reports;
    public File outputFile;

    @Before
    public void buildProjectBySkippingTestCases() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(new File(".").getAbsoluteFile());
        processBuilder.command("mvn", "clean", "install","-DskipTests");

        Process start = processBuilder.start();
        start.waitFor();
    }

    public void setup() throws IOException, InterruptedException, XmlPullParserException, JDOMException, MergeException {
        executeTests();
        mergeJSONs();
    }

    @Test
    public void testReportsGeneratedUsingStreamingJSONFormatter() throws IOException, InterruptedException, XmlPullParserException, JDOMException, MergeException {
        setup();

        String jsonString = FileUtils.readFileToString(outputFile,"UTF-8");
        JSONArray json = new JSONArray(jsonString);
        assertEquals("The report generated does not contain 3 feature files",3, json.length());

        List<JSONObject> testCases = new ArrayList<>();

        for (Object o : json) {
            if(!(o instanceof JSONObject))
                throw new IllegalArgumentException();

            JSONObject jsonObject = (JSONObject) o;
            JSONArray elements = jsonObject.getJSONArray("elements");
            for (Object testCase : elements) {
                testCases.add((JSONObject) testCase);
            }
        }
        assertEquals("Report generated does not have data of 15 test cases. Streaming JSON results merger failed",15, testCases.size());
    }

    @Test
    public void validateCucumberReportsOnForcefullyKillingThreads() throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        File combinedJSONFile = new File(String.format("%s/target/cucumber/threads/combined.json", testProjectDirectory));
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testProjectDirectory);
        processBuilder.command("mvn", "clean", "integration-test","-DcucumberRunner.features=src/test/resources-1");

        Process start = processBuilder.start();
        start.waitFor();

        String cucumberPath = String.format("%s/test-project/target/cucumber/combined-html/cucumber-html-reports",
                new File("").getAbsolutePath());
        File cucumberHtmlReports = new File(cucumberPath);
        assertTrue("Combined Html Reports are not generated when process running threads are forcefully killed",
                cucumberHtmlReports.exists());

        String jsonString = FileUtils.readFileToString(combinedJSONFile,"UTF-8");
        JSONArray json = new JSONArray(jsonString);
        List<JSONObject> testCases = new ArrayList<>();

        for (Object o : json) {
            if(!(o instanceof JSONObject))
                throw new IllegalArgumentException();

            JSONObject jsonObject = (JSONObject) o;
            JSONArray elements = jsonObject.getJSONArray("elements");
            for (Object testCase : elements) {
                testCases.add((JSONObject) testCase);
            }
        }
        assertEquals("Report generated does not have data of 2 test cases. Streaming JSON results merger failed",2, testCases.size());
    }

    public void executeTests() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();

        processBuilder.directory(testProjectDirectory);
        processBuilder.command("mvn",
                "clean",
                "integration-test",
                "-DuseEnhancedJsonReporting=true");

        Process start = processBuilder.start();
        start.waitFor();
    }

    public int getThreadCount() throws JDOMException, IOException, XmlPullParserException {
        int threadCount=0;
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pomFile));

        ArrayList<Plugin> pluginsList = (ArrayList<Plugin>) model.getBuild().getPlugins();
        for (Plugin plugin: pluginsList) {
            if(plugin.getArtifactId().equals("cucumber-runner-maven-plugin")){
                Xpp3Dom xpp = (Xpp3Dom) plugin.getConfiguration();
                threadCount= Integer.parseInt(xpp.getChild("threadCount").getValue());
            }
        }
        return threadCount;
    }

    public void mergeJSONs() throws MergeException, JDOMException, XmlPullParserException, IOException {
        jsonResultMerger = new JsonResultMerger("combined.json");
        reports = new ArrayList<>();

        for (int i = 0; i < getThreadCount(); i++) {
            File reportFile = new File(String.format("%s/target/cucumber/threads/thread-%d/reports/report.json", testProjectDirectory, i));
            reports.add(reportFile.getPath());
        }
        File outputFolder = FileUtils.getTempDirectory();
        outputFile = jsonResultMerger.merge(outputFolder, reports);
        outputFile.deleteOnExit();
    }
}
