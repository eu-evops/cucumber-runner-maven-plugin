package eu.evops.maven.plugins.cucumber.parallel.reporting;

import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import com.sun.org.apache.xml.internal.dtm.ref.DTMNodeList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Created by n450777 on 30/03/2016.
 */
public class JunitResultMerger implements ResultMerger {
    @Override
    public File merge(File outputFolder, List<String> paths) throws MergeException {
        File outputFile = new File(outputFolder, "combined.xml");

        DOMParser domParser = new DOMParser();

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                .newInstance();

        DocumentBuilder documentBuilder;
        try {
            documentBuilder = documentBuilderFactory
                    .newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new MergeException("Could not create document factory", e);
        }

        Document combinedDocument = documentBuilder.newDocument();

        Element rootElement = combinedDocument.createElement("testsuite");
        rootElement.setAttribute("name", "combined-xml-report");
        combinedDocument.appendChild(rootElement);


        for (String path : paths) {
            try {
                domParser.parse(new InputSource(new FileReader(path)));
                Document document = domParser.getDocument();

                NodeList testcases = document.getElementsByTagName("testcase");
                for (int i = 0; i < testcases.getLength(); i++) {
                    Node testCase = testcases.item(i);
                    Node importedTestCase = combinedDocument.importNode(testCase, true);
                    rootElement.appendChild(importedTestCase);
                }

            } catch (IOException e) {
                throw new MergeException("Could not read file: " + path, e);
            } catch (SAXException e) {
                System.out.println(String.format("Ignoring the report.xml file [%s] due to xml parsing issue", path));
            }
        }

        XPath xpath = XPathFactory.newInstance().newXPath();

        try {
            DTMNodeList failures = (DTMNodeList) xpath
                    .evaluate("//testcase[failure]", combinedDocument,
                            XPathConstants.NODESET);
            rootElement.setAttribute("failures", String.valueOf(failures.getLength()));

            DTMNodeList tests = (DTMNodeList) xpath
                    .evaluate("//testcase", combinedDocument,
                            XPathConstants.NODESET);
            rootElement.setAttribute("tests", String.valueOf(tests.getLength()));

            DTMNodeList skipped = (DTMNodeList) xpath
                    .evaluate("//testcase[skipped]", combinedDocument,
                            XPathConstants.NODESET);
            rootElement.setAttribute("skipped", String.valueOf(skipped.getLength()));


        } catch (XPathExpressionException e) {
            throw new MergeException("Could not find nodes using xpath", e);
        }

        try {
            String documentContents = toString(combinedDocument);
            Files.write(
                    Paths.get(outputFile.getAbsolutePath()),
                    documentContents.getBytes(),
                    StandardOpenOption.CREATE
                    );
        } catch (IOException e) {
            throw new MergeException("Could not save output file", e);
        }

        return outputFile;
    }

    private static String toString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error converting to String", ex);
        }
    }
}
