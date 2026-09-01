package io.ably.lib.test.loader;

import android.os.Bundle;
import android.support.test.InstrumentationRegistry;

public class ArgumentLoader {
    public String getTestArgument(String name) {
        Bundle arguments = InstrumentationRegistry.getArguments();
        return arguments.getString(name);
    }
}
