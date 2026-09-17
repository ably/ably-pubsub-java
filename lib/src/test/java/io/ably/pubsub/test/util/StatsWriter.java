package io.ably.pubsub.test.util;

import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Stats;
import io.ably.pubsub.util.Serialisation;

public class StatsWriter {
    public static HttpCore.RequestBody asJsonRequest(Stats[] stats) throws AblyException {
        return new HttpUtils.JsonRequestBody(Serialisation.gson.toJson(stats));
    }
}
