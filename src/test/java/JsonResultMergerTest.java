import eu.evops.maven.pluins.cucumber.parallel.reporting.JsonResultMerger;
import eu.evops.maven.pluins.cucumber.parallel.reporting.MergeException;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

/**
 * Created by n450777 on 31/03/2016.
 */
public class JsonResultMergerTest {

    private JsonResultMerger jsonResultMerger;

    private List<String> reports;

    private File outputFile;

    @Before
    public void setup() throws IOException, MergeException {
        jsonResultMerger = new JsonResultMerger();
        reports = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            URL report = JsonResultMergerTest.class
                    .getClassLoader().getResource(format("json/report%d.json", i));
            reports.add(report.getFile());
        }

        File outputFolder = FileUtils.getTempDirectory();
        outputFile = jsonResultMerger.merge(outputFolder, reports);
        outputFile.deleteOnExit();
    }

    @Test
    public void canGenerateCombinedReport() throws IOException, MergeException {
        FileUtils.readFileToString(outputFile);
    }

    @Test
    public void generatesValidJson()
            throws MergeException, IOException, SAXException {
        String jsonString = FileUtils.readFileToString(outputFile);
        JSONArray json = new JSONArray(jsonString);
        assertEquals(5, json.length());

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

        assertEquals(5, json.length());
        assertEquals(12, testCases.size());
    }

    @Test
    public void validatesAgainstJunitSchema() throws SAXException, IOException {
    }
}
