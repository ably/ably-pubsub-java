package io.ably.pubsub.types;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import io.ably.pubsub.debug.DebugOptions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.Test;

public class ClientOptionsTest {

    private final ClientOptions clientOptions = new ClientOptions();

    @Test
    public void should_support_idempotent_http_publishing() {
        // Then
        assertTrue(clientOptions.idempotentHttpPublishing);
    }

    @Test
    public void copy_carries_headers_fallbackHosts_transportParams_and_agents() {
        // Given
        clientOptions.headers = new HashMap<>();
        clientOptions.headers.put("X-Custom", "value");
        clientOptions.fallbackHosts = new String[]{"a.example.com", "b.example.com"};
        clientOptions.transportParams = new Param[]{new Param("remainPresentFor", "1000")};
        clientOptions.agents = new HashMap<>();
        clientOptions.agents.put("some-sdk", "1.2.3");

        // When
        ClientOptions copied = clientOptions.copy();

        // Then
        assertEquals(clientOptions.headers, copied.headers);
        assertArrayEquals(clientOptions.fallbackHosts, copied.fallbackHosts);
        assertArrayEquals(clientOptions.transportParams, copied.transportParams);
        assertEquals(clientOptions.agents, copied.agents);
    }

    @Test
    public void copy_isolates_collections_from_later_changes_to_the_original() {
        // Given
        clientOptions.headers = new HashMap<>();
        clientOptions.agents = new HashMap<>();
        clientOptions.agents.put("some-sdk", "1.2.3");
        clientOptions.fallbackHosts = new String[]{"a.example.com"};
        clientOptions.transportParams = new Param[]{new Param("remainPresentFor", "1000")};
        ClientOptions copied = clientOptions.copy();

        // When
        clientOptions.agents.put("later", "9");
        clientOptions.headers.put("X-Later", "v");
        clientOptions.fallbackHosts[0] = "changed.example.com";
        clientOptions.transportParams[0] = new Param("other", "1");

        // Then
        assertEquals(1, copied.agents.size());
        assertTrue(copied.headers.isEmpty());
        assertEquals("a.example.com", copied.fallbackHosts[0]);
        assertEquals("remainPresentFor", copied.transportParams[0].key);
    }

    /**
     * Fails when an option is added to {@link ClientOptions} (or its auth superclass) without being
     * added to {@code copy()}: every public instance field is set to a non-default value, and the
     * copy must carry an equal one.
     */
    @Test
    public void copy_carries_every_public_field() throws Exception {
        assertCopyCarriesEveryField(new ClientOptions());
    }

    /** As above for the debug subclass, which must also keep its runtime type and every hook. */
    @Test
    public void debugOptions_copy_carries_every_public_field_and_keeps_its_type() throws Exception {
        DebugOptions copied = assertCopyCarriesEveryField(new DebugOptions());
        assertEquals(DebugOptions.class, copied.getClass());
    }

    @SuppressWarnings("unchecked")
    private static <T extends ClientOptions> T assertCopyCarriesEveryField(T original) throws Exception {
        Object defaults = original.getClass().newInstance();
        for (Field field : original.getClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                field.set(original, nonDefaultValue(field, defaults));
            }
        }
        ClientOptions copied = original.copy();
        assertEquals(original.getClass(), copied.getClass());
        assertNotSame(original, copied);
        for (Field field : original.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            assertNotNull(field.getName() + " has no test value", field.get(original));
            assertTrue("copy() does not carry " + field.getDeclaringClass().getSimpleName() + "." + field.getName(),
                Objects.deepEquals(field.get(original), field.get(copied)));
        }
        return (T) copied;
    }

    private static Object nonDefaultValue(Field field, Object defaults) throws Exception {
        Class<?> type = field.getType();
        if (type == boolean.class) return !field.getBoolean(defaults);
        if (type == int.class) return 7;
        if (type == long.class) return 7L;
        if (type == String.class) return "value";
        if (type == String[].class) return new String[]{"value"};
        if (type == Param[].class) return new Param[]{new Param("k", "v")};
        if (type == Map.class) {
            Map<String, String> map = new HashMap<>();
            map.put("k", "v");
            return map;
        }
        if (type.isInterface()) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> null);
        }
        // Any other option type must have a no-arg constructor; add a case above if one does not.
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
