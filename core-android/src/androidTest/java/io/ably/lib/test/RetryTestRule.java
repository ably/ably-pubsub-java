package io.ably.lib.test;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;


public class RetryTestRule implements TestRule {

    private final int timesToRunTestCount;

    /**
     * If `times` is 0, then we should run the test once.
     */
    public RetryTestRule(int times) {
        this.timesToRunTestCount = times + 1;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return statement(base, description);
    }

    private Statement statement(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                Throwable latestException = null;

                for (int runCount = 0; runCount < timesToRunTestCount; runCount++) {
                    try {
                        base.evaluate();
                        return;
                    } catch (Throwable t) {
                        latestException = t;
                        System.err.printf("%s: test failed on run: `%d`. Will run a maximum of `%d` times.%n", description.getDisplayName(), runCount, timesToRunTestCount);
                        t.printStackTrace();
                    }
                }

                if (latestException != null) {
                    System.err.printf("%s: giving up after `%d` failures%n", description.getDisplayName(), timesToRunTestCount);
                    throw latestException;
                }
            }
        };
    }
}
