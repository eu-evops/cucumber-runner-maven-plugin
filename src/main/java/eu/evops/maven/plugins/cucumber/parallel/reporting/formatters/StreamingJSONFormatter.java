package eu.evops.maven.plugins.cucumber.parallel.reporting.formatters;

import cucumber.runtime.formatter.CucumberJSONFormatter;
import gherkin.deps.com.google.gson.Gson;
import gherkin.formatter.Formatter;
import gherkin.formatter.JSONFormatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Created by n471306 on 06/07/2017.
 */
public class StreamingJSONFormatter implements Formatter, Reporter {
    private final JSONFormatter jsonFormatter;
    private File file;

    public StreamingJSONFormatter(File file) {
        this.file = file;
        this.jsonFormatter = new CucumberJSONFormatter(new StringBuffer());
    }

    @Override
    public void syntaxError(String s, String s1, List<String> list, String s2, Integer integer) {
        this.jsonFormatter.syntaxError(s, s1, list, s2, integer);
    }

    @Override
    public void uri(String s) {
        this.jsonFormatter.uri(s);
    }

    @Override
    public void feature(Feature feature) {
        this.jsonFormatter.feature(feature);
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        this.jsonFormatter.scenarioOutline(scenarioOutline);
    }

    @Override
    public void examples(Examples examples) {
        this.jsonFormatter.examples(examples);
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        this.jsonFormatter.startOfScenarioLifeCycle(scenario);
    }

    @Override
    public void background(Background background) {
        this.jsonFormatter.background(background);
    }

    @Override
    public void scenario(Scenario scenario) {
        this.jsonFormatter.scenario(scenario);
    }

    @Override
    public void step(Step step) {
        this.jsonFormatter.step(step);
    }

    private Gson getGson() {
        try {
            Method gsonMethod = JSONFormatter.class.getDeclaredMethod("gson");
            gsonMethod.setAccessible(true);
            return (Gson) gsonMethod.invoke(this.jsonFormatter);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<Map<String, Object>> getFeatureMaps() {
        try {
            Field field = JSONFormatter.class.getDeclaredField("featureMaps");
            field.setAccessible(true);
            return (List<Map<String, Object>>) field.get(this.jsonFormatter);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        this.jsonFormatter.endOfScenarioLifeCycle(scenario);
        done();
    }

    @Override
    public void done() {
        String json = getGson().toJson(getFeatureMaps());
        try {
            Path filePath = Paths.get(file.getAbsolutePath());
            Files.write(filePath, json.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        this.jsonFormatter.close();
    }

    @Override
    public void eof() {
        this.jsonFormatter.eof();
    }

    @Override
    public void before(Match match, Result result) {
        this.jsonFormatter.before(match, result);
    }

    @Override
    public void result(Result result) {
        this.jsonFormatter.result(result);
    }

    @Override
    public void after(Match match, Result result) {
        this.jsonFormatter.after(match, result);
    }

    @Override
    public void match(Match match) {
        this.jsonFormatter.match(match);
    }

    @Override
    public void embedding(String s, byte[] bytes) {
        this.jsonFormatter.embedding(s, bytes);
    }

    @Override
    public void write(String s) {
        this.jsonFormatter.write(s);
    }
}
