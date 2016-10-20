package steps;

import cucumber.api.PendingException;
import cucumber.api.java.en.Given;

/**
 * Created by n450777 on 20/10/2016.
 */
public class TestSteps {
    @Given("^I kill thread if (\\d+) is more than (\\d+)$")
    public void iKillThreadIfValueIsMoreThan(int currentValue, int threshold) throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        if(currentValue > threshold) {
            System.out.println("We should be killing this thread");
        }
    }
}
