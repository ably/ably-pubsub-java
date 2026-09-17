package io.ably.pubsub.util;

import com.google.gson.JsonElement;
import io.ably.pubsub.http.HttpCore;

public class StringUtils {

    public static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public static Serialisation.FromJsonElement<String> fromJsonElement = new Serialisation.FromJsonElement<String>() {
        @Override
        public String fromJsonElement(JsonElement e) {
            return e.getAsJsonPrimitive().getAsString();
        }
    };

    public static HttpCore.ResponseHandler<String> httpResponseHandler = new Serialisation.HttpResponseHandler<String>(String.class, fromJsonElement);

    public static HttpCore.BodyHandler<String> httpBodyHandler = new Serialisation.HttpBodyHandler<String>(String[].class, fromJsonElement);
}
