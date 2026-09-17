package io.ably.pubsub.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonUtils {
    public static JsonUtilsObject object() {
        return new JsonUtilsObject();
    }

    public static class JsonUtilsObject {
        private final JsonObject json;

        JsonUtilsObject() {
            json = new JsonObject();
        }

        public JsonUtilsObject add(String key, Object value) {
            if (value == null) {
                json.add(key, null);
            } else if (value instanceof JsonElement) {
                json.add(key, (JsonElement) value);
            } else if (value instanceof String) {
                json.addProperty(key, (String) value);
            } else if (value instanceof Boolean) {
                json.addProperty(key, (Boolean) value);
            } else if (value instanceof Character) {
                json.addProperty(key, (Character) value);
            } else if (value instanceof Number) {
                json.addProperty(key, (Number) value);
            } else if (value instanceof JsonUtilsObject) {
                json.add(key, ((JsonUtilsObject) value).toJson());
            }
            return this;
        }

        public JsonObject toJson() {
            return json;
        }
    }
}
