package io.ably.pubsub.network;

import java.lang.reflect.InvocationTargetException;

/**
 * The <code>HttpEngineFactory</code> is a utility class that produces a common HTTP Engine API
 * for different implementations. Currently, it supports:
 * - HttpURLConnection ({@link  EngineType#DEFAULT})
 * - OkHttp ({@link  EngineType#OKHTTP})
 */
public interface HttpEngineFactory {

    static HttpEngineFactory getFirstAvailable() {
        HttpEngineFactory okHttpFactory = tryGetOkHttpFactory();
        if (okHttpFactory != null) return okHttpFactory;
        HttpEngineFactory defaultFactory = tryGetDefaultFactory();
        if (defaultFactory != null) return defaultFactory;
        throw new IllegalStateException("No engines are available");
    }

    static HttpEngineFactory tryGetOkHttpFactory() {
        try {
            Class<?> okHttpFactoryClass = Class.forName("io.ably.pubsub.network.OkHttpEngineFactory");
            return (HttpEngineFactory) okHttpFactoryClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            return null;
        }
    }

    static HttpEngineFactory tryGetDefaultFactory() {
        try {
            Class<?> defaultFactoryClass = Class.forName("io.ably.pubsub.network.DefaultHttpEngineFactory");
            return (HttpEngineFactory) defaultFactoryClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            return null;
        }
    }

    HttpEngine create(HttpEngineConfig config);

    EngineType getEngineType();
}
