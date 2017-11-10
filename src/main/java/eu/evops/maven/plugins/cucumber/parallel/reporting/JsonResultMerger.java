package eu.evops.maven.plugins.cucumber.parallel.reporting;

import org.codehaus.plexus.util.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Created by n450777 on 30/03/2016.
 */
public class JsonResultMerger implements ResultMerger {
    private String outputFileName;

    public JsonResultMerger(String outputFileName) {
        this.outputFileName = outputFileName;
    }

    @Override
    public File merge(File outputFolder, List<String> paths) throws MergeException {
        File outputFile = new File(outputFolder, outputFileName);
        JSONArray combined = new JSONArray();
        String contents = "";
        for (String jsonFile : paths) {
            try {
                contents = FileUtils.fileRead(jsonFile);
                JSONArray threadReport = new JSONArray(contents);

                // for each of test suites
                for (int i = 0; i < threadReport.length(); i++) {
                    String suiteId = threadReport.getJSONObject(i).getString("id");
                    // find test suite in the combined report
                    boolean matchFound = false;
                    for (int j = 0; j < combined.length(); j++) {
                        if(combined.getJSONObject(j).getString("id").equals(suiteId)) {
                            matchFound = true;
                            JSONArray combinedElements = combined.getJSONObject(j).getJSONArray("elements");
                            JSONArray elements = threadReport
                                    .getJSONObject(i).getJSONArray("elements");

                            for (int k = 0; k < elements.length(); k++) {
                                combinedElements.put(elements.get(k));
                            }
                        }
                    }
                    if(!matchFound) {
                        combined.put(threadReport.get(i));
                    }
                }

                Files.write(
                        Paths.get(outputFile.getAbsolutePath()),
                        combined.toString(2).getBytes(),
                        StandardOpenOption.CREATE
                );
            } catch (IOException e) {
                throw new MergeException("Could not write to combined json report file", e);
            } catch (JSONException e) {
                System.out.println(String.format("Ignoring the report.json file [%s] due to json parsing issue", jsonFile));
                System.out.println(String.format("Ignored Report Content: \n [%s]", contents));
            }
        }

        return outputFile;
    }
}
