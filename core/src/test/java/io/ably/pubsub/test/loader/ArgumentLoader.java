package io.ably.pubsub.test.loader;

public class ArgumentLoader {
    public String getTestArgument(String name) {
        return System.getenv(name);
    }
}
