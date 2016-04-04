import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import eu.evops.maven.pluins.cucumber.parallel.reporting.JunitResultMerger;
import eu.evops.maven.pluins.cucumber.parallel.reporting.MergeException;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

/**
 * Created by n450777 on 31/03/2016.
 */
public class JunitResultMergerTest {

    private JunitResultMerger junitResultMerger;

    private List<String> reports;

    private File outputFile;

    @Before
    public void setup() throws IOException, MergeException {
        junitResultMerger = new JunitResultMerger();
        reports = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            URL report = JunitResultMergerTest.class
                    .getClassLoader().getResource(format("junit/report%d.xml", i));
            reports.add(report.getFile());
        }

        File outputFolder = FileUtils.getTempDirectory();
        outputFile = junitResultMerger.merge(outputFolder, reports);
        outputFile.deleteOnExit();
    }

    @Test
    public void canGenerateCombinedReport() throws IOException, MergeException {
        FileUtils.readFileToString(outputFile);
    }

    @Test
    public void generatesValidXml()
            throws MergeException, IOException, SAXException {
        DOMParser parser = new DOMParser();
        parser.parse(new InputSource(new FileReader(outputFile)));
    }

    @Test
    public void validatesAgainstJunitSchema() throws SAXException, IOException {
        StreamSource streamSource = new StreamSource(outputFile);

        SchemaFactory schemaFactory = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory
                .newSchema(this.getClass().getResource("junit/schema.xsd"));

        Validator validator = schema.newValidator();
        validator.validate(streamSource);
    }
}
