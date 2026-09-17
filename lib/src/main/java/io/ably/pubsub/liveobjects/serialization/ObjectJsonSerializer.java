package io.ably.pubsub.liveobjects.serialization;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.pubsub.util.Log;

import java.lang.reflect.Type;

public class ObjectJsonSerializer implements JsonSerializer<Object[]>, JsonDeserializer<Object[]> {
    private static final String TAG = ObjectJsonSerializer.class.getName();

    @Override
    public Object[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        ObjectSerializer serializer = ObjectSerializer.tryGet();
        if (serializer == null) {
            Log.w(TAG, "Skipping 'state' field json deserialization because ObjectSerializer not found.");
            return null;
        }
        if (!json.isJsonArray()) {
            throw new JsonParseException("Expected a JSON array for 'state' field, but got: " + json);
        }
        return serializer.readFromJsonArray(json.getAsJsonArray());
    }

    @Override
    public JsonElement serialize(Object[] src, Type typeOfSrc, JsonSerializationContext context) {
        ObjectSerializer serializer = ObjectSerializer.tryGet();
        if (serializer == null) {
            Log.w(TAG, "Skipping 'state' field json serialization because ObjectSerializer not found.");
            return JsonNull.INSTANCE;
        }
        return serializer.asJsonArray(src);
    }
}
