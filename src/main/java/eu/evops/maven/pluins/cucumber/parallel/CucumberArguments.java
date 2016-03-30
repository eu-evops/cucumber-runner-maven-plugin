package eu.evops.maven.pluins.cucumber.parallel;

/**
 * Created by n450777 on 29/03/2016.
 */
public enum CucumberArguments {
    DryRun("--dry-run"),
    Plugin("--plugin"),
    Tags("--tags"),
    Monochrome("--monochrome"),
    Glue("--glue"),
    Strict("--strict"),
    ScenarioName("--name");

    private String commandLineArgument;
    CucumberArguments(String commandLineArgument) {
        this.commandLineArgument = commandLineArgument;
    }

    public String getArg() {
        return commandLineArgument;
    }
}
