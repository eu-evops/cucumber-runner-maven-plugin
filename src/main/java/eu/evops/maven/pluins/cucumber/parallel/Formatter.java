package eu.evops.maven.pluins.cucumber.parallel;

import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Created by n450777 on 29/03/2016.
 */
public class Formatter implements gherkin.formatter.Formatter {

    private static final Logger LOG = Logger.getLogger(Formatter.class.getName());

    private final File destination;

    private final Map<String, List<Integer>> featuresAndLineNumbers = new HashMap<String, List<Integer>>();

    private List<Integer> currentFeature;

    public Formatter(File destination) {
        this.destination = destination;
    }

    @Override
    public void syntaxError(String s, String s1, List<String> list, String s2,
            Integer integer) {
        // Not required in the implementation
    }

    @Override
    public void uri(String s) {
        currentFeature = new ArrayList<>();
        featuresAndLineNumbers.put(s, currentFeature);
    }

    @Override
    public void feature(Feature feature) {
        // Not required in the implementation
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        // Not required in the implementation
    }

    @Override
    public void examples(Examples examples) {
        // Not required in the implementation
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        currentFeature.add(scenario.getLine());
    }

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        // Not required in the implementation
    }

    @Override
    public void background(Background background) {
        // Not required in the implementation
    }

    @Override
    public void scenario(Scenario scenario) {
        // Not required in the implementation
    }

    @Override
    public void step(Step step) {
        // Not required in the implementation
    }

    @Override
    public void done() {
        List<HashMap<String, List<Integer>>> threadedGroups = new ArrayList<HashMap<String, List<Integer>>>();
        Set<Map.Entry<String, List<Integer>>> entries = featuresAndLineNumbers
                .entrySet();

        int numberOfThreads = getNumberOfThreads();
        int counter = 0;

        Iterator<Map.Entry<String, List<Integer>>> iterator = entries
                .iterator();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, List<Integer>> entry = iterator.next();
            String feature = entry.getKey();
            List<Integer> lineNumbers = entry.getValue();

            for (Integer lineNumber : lineNumbers) {
                int threadGroupIndex = counter % numberOfThreads;

                if (threadedGroups.size() < threadGroupIndex + 1) {
                    threadedGroups
                            .add(new HashMap<String, List<Integer>>());
                }
                HashMap<String, List<Integer>> threadGroup = threadedGroups
                        .get(threadGroupIndex);

                if (!threadGroup.containsKey(feature)) {
                    threadGroup.put(feature, new ArrayList<Integer>());
                }

                threadGroup.get(feature).add(lineNumber);

                counter++;
            }
        }

        for (int i = 0; i < threadedGroups.size(); i++) {
            HashMap<String, List<Integer>> scenarioList = threadedGroups
                    .get(i);

            // Remove destination folder
            if(destination.exists()) {
                destination.delete();
            }
            destination.mkdirs();

            File threadFolder = new File(destination, String.format("thread-%d", i));
            File threadFile = new File(threadFolder, "run");

            if (threadFolder.exists()) {
                threadFolder.delete();
            }

            // Recreate thread folder
            threadFolder.mkdirs();

            FileWriter fileWriter = null;

            try {
                fileWriter = new FileWriter(threadFile, true);

                for (Map.Entry<String, List<Integer>> stringArrayListEntry : scenarioList
                        .entrySet()) {

                    fileWriter.write(stringArrayListEntry.getKey());

                    List<Integer> lineNumbers = stringArrayListEntry
                            .getValue();
                    for (Integer lineNumber : lineNumbers) {
                        fileWriter.write(String.format(":%d",
                                lineNumber));
                    }
                    fileWriter.write(" ");
                }

            } catch (IOException e) {
                LOG.severe(e.getMessage());
            }

            try {
                if(fileWriter != null) {
                    fileWriter.close();
                }
            } catch (IOException e) {
                LOG.severe(e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        // Not required in the implementation
    }

    @Override
    public void eof() {
        // Not required in the implementation
    }

    private int getNumberOfThreads() {
        return Integer.getInteger("cucumber-parallel-execution.threads", Runtime.getRuntime().availableProcessors());
    }

}
