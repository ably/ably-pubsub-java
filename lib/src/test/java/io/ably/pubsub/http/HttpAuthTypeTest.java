package io.ably.pubsub.http;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HttpAuthTypeTest {
    @Test
    public void parseSuccess() {
        // The expected form in `www-authenticate` HTTP header in server response.
        // See: https://github.com/ably/ably-java/issues/711
        //
        // The test for "basic" has been observed to fail under the following conditions:
        //   1. Add `import java.util.Locale;` to this file.
        //   2. Call `Locale.setDefault(new Locale("tr", "TR"));` as the first statement in this test method.
        //   3. Use `toUpperCase()`, without `Locale.ROOT`, in the implementation of `HttpAuth.Type.parse(String)`.
        // The observed failure is:
        //   java.lang.IllegalArgumentException: Failed to parse conformed form 'BASİC' of raw value 'basic'.
        assertEquals(HttpAuth.Type.BASIC, HttpAuth.Type.parse("basic"));
        assertEquals(HttpAuth.Type.DIGEST, HttpAuth.Type.parse("digest"));
        assertEquals(HttpAuth.Type.X_ABLY_TOKEN, HttpAuth.Type.parse("x-ably-token"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseFailure() {
        HttpAuth.Type.parse("Früli");
    }

    @Test(expected = NullPointerException.class)
    public void parseFailureNullValue() {
        HttpAuth.Type.parse(null);
    }
}
