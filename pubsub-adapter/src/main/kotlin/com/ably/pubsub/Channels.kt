package com.ably.pubsub

import io.ably.pubsub.realtime.ChannelState
import io.ably.pubsub.types.ChannelOptions

/**
 * Represents collection of managed Channel instances
 */
public interface Channels<ChannelType> : Iterable<ChannelType> {

  /**
   * Checks if channel with specified name exists
   * <p>
   * Spec: RSN2, RTS2
   * @param name The channel name.
   * @return `true` if it contains the specified [name].
   */
  public fun contains(name: String): Boolean

  /**
   * Creates a new [Channel] object, or returns the existing channel object.
   * <p>
   * Spec: RSN3a, RTS3a
   * @param name The channel name.
   * @return A [Channel] object.
   */
  public fun get(name: String): ChannelType

  /**
   * Creates a new [Channel] object, with the specified [ChannelOptions], or returns the existing channel object.
   * <p>
   * Spec: RSN3c, RTS3c
   * @param name The channel name.
   * @param options A [ChannelOptions] object.
   * @return A [Channel] object.
   */
  public fun get(name: String, options: ChannelOptions): ChannelType

  /**
   * Releases a [Channel] object, deleting it, and enabling it to be garbage collected.
   * It also removes any listeners associated with the channel.
   * To release a channel, the [ChannelState] must be `INITIALIZED`, `DETACHED`, or `FAILED`.
   * <p>
   * Spec: RSN4, RTS4
   * @param name The channel name.
   */
  public fun release(name: String)
}
