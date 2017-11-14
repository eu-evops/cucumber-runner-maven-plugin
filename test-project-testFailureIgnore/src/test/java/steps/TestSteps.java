package steps;

import cucumber.api.java.en.Given;
import org.junit.Assert;

public class TestSteps {

    @Given("^I have a failing step$")
    public void iHaveAFailingStep() throws Throwable {
        Assert.assertTrue(false);
    }
}
