package eu.evops.maven.pluins.cucumber.parallel;

import gherkin.deps.com.google.gson.Gson;
import gherkin.deps.com.google.gson.GsonBuilder;
import gherkin.deps.net.iharder.Base64;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.*;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by n471306 on 06/07/2017.
 */
public class CustomJSONFormatter implements Reporter, Formatter {

    private final List<Map<String, Object>> featureMaps = new ArrayList<Map<String, Object>>();
    private File fileOutputStream;
    private Map<String, Object> featureMap;
    private String uri;
    private List<Map> beforeHooks = new ArrayList<Map>();
    private enum Phase {step, match, embedding, output, result};

    public CustomJSONFormatter(File out) {
        this.fileOutputStream = out;
    }

    /**
     * In order to handle steps being added all at once, this method determines allows methods to
     * opperator correctly if
     *
     * step
     * match
     * embedding
     * output
     * result
     * step
     * match
     * embedding
     * output
     * result
     *
     * or if
     *
     * step
     * step
     * match
     * embedding
     * output
     * result
     * match
     * embedding
     * output
     * result
     *
     * is called
     *
     * @return the correct step for the current operation based on past method calls to the formatter interface
     */
    private Map getCurrentStep(Phase phase) {
        String target = phase.ordinal() <= Phase.match.ordinal()? Phase.match.name(): Phase.result.name();
        Map lastWithValue = null;
        for (Map stepOrHook : getSteps()) {
            if (stepOrHook.get(target) == null) {
                return stepOrHook;
            } else {
                lastWithValue = stepOrHook;
            }
        }
        return lastWithValue;
    }



    public void uri(String uri) {
        this.uri = uri;
    }

    public void feature(Feature feature) {
        featureMap = feature.toMap();
        featureMap.put("uri", uri);
        featureMaps.add(featureMap);
    }

    public void background(Background background) {
        getFeatureElements().add(background.toMap());
    }

    public void scenario(Scenario scenario) {
        getFeatureElements().add(scenario.toMap());
        if (beforeHooks.size() > 0) {
            getFeatureElement().put("before", beforeHooks);
            beforeHooks = new ArrayList<Map>();
        }
    }

    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        getFeatureElements().add(scenarioOutline.toMap());
    }

    public void examples(Examples examples) {
        getAllExamples().add(examples.toMap());
    }

    public void step(Step step) {
        getSteps().add(step.toMap());
    }

    public void match(Match match) {
        getCurrentStep(Phase.match).put("match", match.toMap());
    }

    public void embedding(String mimeType, byte[] data) {
        final Map<String, String> embedding = new HashMap<String, String>();
        embedding.put("mime_type", mimeType);
        embedding.put("data", Base64.encodeBytes(data));
        getEmbeddings().add(embedding);
    }

    public void write(String text) {
        getOutput().add(text);
    }

    public void result(Result result) {
        getCurrentStep(Phase.result).put("result", result.toMap());
    }

    public void before(Match match, Result result) {
        beforeHooks.add(buildHookMap(match,result));
    }

    public void after(Match match, Result result) {
        List<Map> hooks = getFeatureElement().get("after");
        if (hooks == null) {
            hooks = new ArrayList<Map>();
            getFeatureElement().put("after", hooks);
        }
        hooks.add(buildHookMap(match,result));
    }

    private Map buildHookMap(final Match match, final Result result) {
        final Map hookMap = new HashMap();
        hookMap.put("match", match.toMap());
        hookMap.put("result", result.toMap());
        return hookMap;
    }

    public void appendDuration(final int timestamp) {
        final Map result = (Map) getCurrentStep(Phase.result).get("result");
        // check to make sure result exists (scenario outlines do not have results yet)
        if (result != null) {
            //convert to nanoseconds
            final long nanos = timestamp * 1000000000L;
            result.put("duration", nanos);
        }
    }

    public void eof() {
    }

    public void done() {
    }

    public void close() {
    }

    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
        throw new UnsupportedOperationException();
    }


    public void startOfScenarioLifeCycle(Scenario scenario) {
        try {
            if(!fileOutputStream.exists()){
                fileOutputStream.createNewFile();
            }
        }
        catch (Exception ex) {
            //TODO
        }
    }

    public void endOfScenarioLifeCycle(Scenario scenario) {
        try {
                FileUtils.write(fileOutputStream, gson().toJson(featureMaps), "UTF-8", false);
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    private List<Map<String, Object>> getFeatureElements() {
        List<Map<String, Object>> featureElements = (List) featureMap.get("elements");
        if (featureElements == null) {
            featureElements = new ArrayList<Map<String, Object>>();
            featureMap.put("elements", featureElements);
        }
        return featureElements;
    }

    private Map<Object, List<Map>> getFeatureElement() {
        if (getFeatureElements().size() > 0) {
            return (Map) getFeatureElements().get(getFeatureElements().size() - 1);
        } else {
            return null;
        }
    }

    private List<Map> getAllExamples() {
        List<Map> allExamples = getFeatureElement().get("examples");
        if (allExamples == null) {
            allExamples = new ArrayList<Map>();
            getFeatureElement().put("examples", allExamples);
        }
        return allExamples;
    }

    private List<Map> getSteps() {
        List<Map> steps = getFeatureElement().get("steps");
        if (steps == null) {
            steps = new ArrayList<Map>();
            getFeatureElement().put("steps", steps);
        }
        return steps;
    }

    private List<Map<String, String>> getEmbeddings() {
        List<Map<String, String>> embeddings = (List<Map<String, String>>) getCurrentStep(Phase.embedding).get("embeddings");
        if (embeddings == null) {
            embeddings = new ArrayList<Map<String, String>>();
            getCurrentStep(Phase.embedding).put("embeddings", embeddings);
        }
        return embeddings;
    }

    private List<String> getOutput() {
        List<String> output = (List<String>) getCurrentStep(Phase.output).get("output");
        if (output == null) {
            output = new ArrayList<String>();
            getCurrentStep(Phase.output).put("output", output);
        }
        return output;
    }

    protected Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
