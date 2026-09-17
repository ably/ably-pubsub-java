package io.ably.pubsub.types;

import com.google.gson.JsonSyntaxException;

import java.util.Map;
import java.util.TreeMap;

import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.Serialisation;

public class RecoveryKeyContext {
    private static final String TAG = "RecoveryKeyContext";

    private final String connectionKey;
    private final long msgSerial;
    // Sorted so encode() produces deterministic, key-ordered JSON regardless of input map ordering.
    private final Map<String, String> channelSerials = new TreeMap<>();

    public RecoveryKeyContext(String connectionKey, long msgSerial, Map<String, String> channelSerials) {
        this.connectionKey = connectionKey;
        this.msgSerial = msgSerial;
        this.channelSerials.putAll(channelSerials);
    }

    public String getConnectionKey() {
        return connectionKey;
    }

    public long getMsgSerial() {
        return msgSerial;
    }

    public Map<String, String> getChannelSerials() {
        return channelSerials;
    }

    public String encode() {
        return Serialisation.gson.toJson(this);
    }

    public static RecoveryKeyContext decode(String json) {
        try {
            return Serialisation.gson.fromJson(json, RecoveryKeyContext.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Cannot create recovery key from json: " + e.getMessage());
            return null;
        }
    }
}
