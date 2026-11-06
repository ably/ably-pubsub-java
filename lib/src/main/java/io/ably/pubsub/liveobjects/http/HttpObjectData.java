package io.ably.pubsub.liveobjects.http;

import io.ably.pubsub.liveobjects.message.ObjectData;

/**
 * A decoded leaf value in a full-format
 * {@link io.ably.pubsub.liveobjects.HttpObject#get(io.ably.pubsub.types.Param[])} response. Exactly one value
 * getter returns non-null: binary values are decoded to {@code byte[]} and JSON values are parsed
 * (RSO9c2). {@link #getObjectId()} is always {@code null}; an object reference is returned as an
 * {@link HttpLiveObject} instead (RSO9c1).
 *
 * <p>Spec: RSO9c
 */
public interface HttpObjectData extends ObjectData, HttpObjectGetFullResult {
}
