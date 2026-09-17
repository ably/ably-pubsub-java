package io.ably.lib.test.loader;

public class ArgumentLoader {
    public String getTestArgument(String name) {
        return System.getenv(name);
    }
}
