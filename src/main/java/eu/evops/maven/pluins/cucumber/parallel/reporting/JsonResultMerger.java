package eu.evops.maven.pluins.cucumber.parallel.reporting;

import org.codehaus.plexus.util.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by n450777 on 30/03/2016.
 */
public class JsonResultMerger implements ResultMerger {
    @Override
    public File merge(File outputFolder, List<String> paths) throws MergeException {
        File outputFile = new File(outputFolder, "combined.json");
        JSONArray combined = new JSONArray();

        for (String jsonFile : paths) {
            try {
                String contents = FileUtils.fileRead(jsonFile);
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

                org.apache.commons.io.FileUtils.write(outputFile, combined.toString(2));
            } catch (IOException e) {
                throw new MergeException("Could not write to combined json report file", e);
            } catch (JSONException e) {
                throw new MergeException("Could not parse json file", e);
            }
        }

        return outputFile;
    }
}
