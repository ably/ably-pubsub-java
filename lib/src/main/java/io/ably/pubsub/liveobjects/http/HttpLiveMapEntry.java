package io.ably.pubsub.liveobjects.http;

import org.jetbrains.annotations.NotNull;

/**
 * An entry of an {@link HttpLiveMap}.
 *
 * <p>Spec: RSO16
 */
public interface HttpLiveMapEntry {

    /**
     * @return the entry's value: a nested object or a decoded leaf (RSO16a).
     */
    @NotNull HttpObjectGetFullResult getData();
}
