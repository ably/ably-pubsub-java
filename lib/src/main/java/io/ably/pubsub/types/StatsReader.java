package io.ably.pubsub.types;

import java.io.UnsupportedEncodingException;

import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.util.Serialisation;

/**
 * StatsReader: internal
 * Utility class to convert response bodies in different formats to Stats data.
 */
public class StatsReader  {

    public static Stats[] readJson(byte[] jsonBytes) throws AblyException {
        try {
            return readJson(new String(jsonBytes, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw AblyException.fromThrowable(e);
        }
    }

    public static Stats[] readJson(String packed) throws AblyException {
        return Serialisation.gson.fromJson(packed, Stats[].class);
    }

    public static HttpCore.BodyHandler<Stats> statsResponseHandler = new HttpCore.BodyHandler<Stats>() {
        @Override
        public Stats[] handleResponseBody(String contentType, byte[] body) throws AblyException {
            if("application/json".equals(contentType))
                return readJson(body);
            return null;
        }
    };
}
