package steps;

import cucumber.api.java.en.Given;
import cucumber.runtime.ClassFinder;
import cucumber.runtime.Reflections;
import cucumber.runtime.io.ResourceLoaderClassFinder;
import org.apache.commons.io.FileUtils;

import java.io.File;

/**
 * Created by n450777 on 20/10/2016.
 */
public class TestSteps {
    @Given("^I kill thread if (\\d+) is more than (\\d+)$")
    public void iKillThreadIfValueIsMoreThan(int currentValue, int threshold) throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        if(currentValue > threshold) {
            System.out.println("Killing this thread");
            Thread.currentThread().stop();
        }
    }

    @Given("^I Forcefully kill thread if (\\d+) is more than (\\d+)$")
    public void iForcefullyKillThreadIfValueIsMoreThan(int currentValue, int threshold) throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        if(currentValue > threshold) {
            String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            String processID = processName.split("@")[0];
            System.out.println("Forcefully Killing this thread");
            Runtime.getRuntime().exec("kill "+ processID);
        }
    }
}
