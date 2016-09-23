package eu.evops.test.steps;

import cucumber.api.java.en.Given;

/**
 * Created by n450777 on 23/09/2016.
 */
public class TestSteps {
    @Given("^something$")
    public void something() throws Throwable {
        Thread.sleep(5000);
    }
}
