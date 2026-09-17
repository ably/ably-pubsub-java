package io.ably.pubsub.liveobjects;

/**
 * The type of a value resolved by a {@code PathObject} or wrapped by an
 * {@code Instance} in the LiveObjects graph.
 *
 * <p>Spec: RTTS2
 */
public enum ValueType {
    /** Corresponds to the {@code String} primitive. Spec: RTTS2a1 */
    STRING,
    /** Corresponds to the {@code Number} primitive. Spec: RTTS2a2 */
    NUMBER,
    /** Corresponds to the {@code Boolean} primitive. Spec: RTTS2a3 */
    BOOLEAN,
    /** Corresponds to the {@code Binary} primitive. Spec: RTTS2a4 */
    BINARY,
    /** Corresponds to the {@code JsonObject} primitive. Spec: RTTS2a5 */
    JSON_OBJECT,
    /** Corresponds to the {@code JsonArray} primitive. Spec: RTTS2a6 */
    JSON_ARRAY,
    /** Corresponds to a {@code LiveMap} object. Spec: RTTS2a7 */
    LIVE_MAP,
    /** Corresponds to a {@code LiveCounter} object. Spec: RTTS2a8 */
    LIVE_COUNTER,
    /** Returned by {@code PathObject#getType()} only when a value is present but matches none of the known types. Never produced by an {@code Instance} in normal operation. Spec: RTTS2a9 */
    UNKNOWN,
}
