package io.ably.pubsub.liveobjects.http;

import org.jetbrains.annotations.Nullable;

/**
 * A counter object in a {@link io.ably.pubsub.liveobjects.HttpObject#get(io.ably.pubsub.types.Param[])}
 * response.
 *
 * <p>Spec: RSO15
 */
public interface HttpLiveCounter extends HttpLiveObject {

    /**
     * @return the counter's current value (the wire field {@code counter.data.number}, RSO15b), or
     * {@code null} if the response didn't carry a numeric value there.
     */
    @Nullable Double getCount();
}
