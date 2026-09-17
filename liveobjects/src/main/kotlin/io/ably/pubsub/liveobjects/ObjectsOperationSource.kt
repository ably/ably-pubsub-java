package io.ably.pubsub.liveobjects

/** @spec RTO22 */
internal enum class ObjectsOperationSource {
    LOCAL,   // RTO22a - applied upon receipt of ACK
    CHANNEL  // RTO22b - received over a Realtime channel
}
