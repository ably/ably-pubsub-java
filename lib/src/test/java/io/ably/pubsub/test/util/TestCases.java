package io.ably.pubsub.test.util;

import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.util.Log;

import java.util.ArrayList;

public class TestCases {
    final ArrayList<Base> testCases;

    public TestCases() {
        testCases = new ArrayList<Base>();
    }

    public void add(Base testCase) {
        testCases.add(testCase);
    }

    public void run() throws Exception {
        for (final Base testCase : testCases) {
            try {
                Log.i("TestCase", "starting: " + testCase.name);
                Helpers.expectedError(new Helpers.AblyFunction<Void, Void>() {
                    @Override
                    public Void apply(Void aVoid) throws AblyException {
                        try {
                            testCase.run();
                        } catch (Exception e) {
                            if (e instanceof AblyException) {
                                throw (AblyException) e;
                            }
                            throw new RuntimeException(e);
                        }
                        return null;
                    }
                }, testCase.expectedError, testCase.expectedStatusCode, testCase.expectedCode);
            } catch (Exception e) {
                throw new Exception("in test case \"" + testCase.name + "\"", e);
            } catch (Error e) {
                throw new Error("in test case \"" + testCase.name + "\"", e);
            } finally {
                Log.i("TestCase", "ended: " + testCase.name);
            }
        }
    }

    public static abstract class Base {
        final protected String name;
        final protected String expectedError;
        final protected int expectedCode;
        final protected int expectedStatusCode;

        public Base(String name, String expectedError) {
            this(name, expectedError, 0);
        }

        public Base(String name, String expectedError, int expectedStatusCode) {
            this(name, expectedError, expectedStatusCode, 0);
        }

        public Base(String name, String expectedError, int expectedStatusCode, int expectedCode) {
            this.name = name;
            this.expectedError = expectedError;
            this.expectedStatusCode = expectedStatusCode;
            this.expectedCode = expectedCode;
        }

        public abstract void run() throws Exception;
    }
}
