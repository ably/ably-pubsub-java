package io.ably.pubsub.network;

import java.lang.reflect.InvocationTargetException;

/**
 * The <code>WebSocketEngineFactory</code> is a utility class that produces a common WebSocket Engine API
 * for different implementations. Currently, it supports:
 * - TooTallNate/Java-WebSocket ({@link  EngineType#DEFAULT})
 * - OkHttp ({@link  EngineType#OKHTTP})
 */
public interface WebSocketEngineFactory {
    static WebSocketEngineFactory getFirstAvailable() {
        WebSocketEngineFactory okWebSocketFactory = tryGetOkWebSocketFactory();
        if (okWebSocketFactory != null) return okWebSocketFactory;
        WebSocketEngineFactory defaultFactory = tryGetDefaultFactory();
        if (defaultFactory != null) return defaultFactory;
        throw new IllegalStateException("No engines are available");
    }

    static WebSocketEngineFactory tryGetOkWebSocketFactory() {
        try {
            Class<?> okWebSocketFactoryClass = Class.forName("io.ably.pubsub.network.OkHttpWebSocketEngineFactory");
            return (WebSocketEngineFactory) okWebSocketFactoryClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            return null;
        }
    }

    static WebSocketEngineFactory tryGetDefaultFactory() {
        try {
            Class<?> defaultFactoryClass = Class.forName("io.ably.pubsub.network.DefaultWebSocketEngineFactory");
            return (WebSocketEngineFactory) defaultFactoryClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            return null;
        }
    }

    WebSocketEngine create(WebSocketEngineConfig config);

    EngineType getEngineType();
}
