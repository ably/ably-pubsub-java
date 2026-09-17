package com.ably.pubsub

import com.ably.Subscription
import com.ably.annotations.InternalAPI
import io.ably.pubsub.realtime.ChannelBase.MessageListener
import io.ably.pubsub.realtime.ChannelState
import io.ably.pubsub.realtime.CompletionListener
import io.ably.pubsub.types.ChannelOptions
import io.ably.pubsub.types.ChannelProperties
import io.ably.pubsub.types.ErrorInfo
import io.ably.pubsub.types.Message


/**
 * An interface representing a Realtime Channel.
 */
public interface RealtimeChannel : Channel {
  /**
   * Presence set for a channel.
   */
  override val presence: RealtimePresence

  /**
   * The current [ChannelState] of the channel.
   *
   * Spec: RTL2b
   */
  public val state: ChannelState

  /**
   * An [ErrorInfo] object describing the last error which occurred on the channel, if any.
   *
   * Spec: RTL4e
   */
  public val reason: ErrorInfo?

  /**
   * A [ChannelProperties] object.
   *
   * Spec: CP1, RTL15
   */
  public val properties: ChannelProperties

  /**
   * Attach to this channel ensuring the channel is created in the Ably system and all messages published
   * on the channel are received by any channel listeners registered using [subscribe].
   * Any resulting channel state change will be emitted to any listeners registered using the
   * [io.ably.pubsub.util.EventEmitter.on] or [io.ably.pubsub.util.EventEmitter.once] methods.
   * As a convenience, `attach()` is called implicitly if [subscribe] for the channel is called,
   * or [RealtimePresence.enter] or [RealtimePresence.subscribe] are called on the [RealtimePresence] object for this channel.
   *
   * Spec: RTL4d
   */
  public fun attach(listener: CompletionListener? = null)

  /**
   * Detach from this channel.
   * Any resulting channel state change is emitted to any listeners registered using the
   * [io.ably.pubsub.util.EventEmitter.on] or [io.ably.pubsub.util.EventEmitter.once] methods.
   * Once all clients globally have detached from the channel, the channel will be released in the Ably service within two minutes.
   *
   * Spec: RTL5e
   */
  public fun detach(listener: CompletionListener? = null)

  /**
   * Registers a listener for messages on this channel.
   * The caller supplies a listener function, which is called each time one or more messages arrives on the channel.
   *
   * Spec: RTL7a
   *
   * @param listener A listener may optionally be passed in to this call to be notified of success or failure
   * of the channel [RealtimeChannel.attach] operation. This listener is invoked on a background thread.
   */
  public fun subscribe(listener: MessageListener): Subscription

  /**
   * Registers a listener for messages with a given event name on this channel.
   * The caller supplies a listener function, which is called each time one or more matching messages arrives at the channel.
   *
   * Spec: RTL7b
   *
   * @param eventName The event name.
   * @param listener A listener may optionally be passed in to this call to be notified of success or failure
   * of the channel [RealtimeChannel.attach] operation. This listener is invoked on a background thread.
   */
  public fun subscribe(eventName: String, listener: MessageListener): Subscription

  /**
   * Registers a listener for messages on this channel for multiple event name values.
   * The caller supplies a listener function, which is called each time one or more matching messages arrives on the channel.
   *
   * Spec: RTL7a
   *
   * @param eventNames A list of event names.
   * @param listener A listener may optionally be passed in to this call to be notified of success or failure
   * of the channel [RealtimeChannel.attach] operation. This listener is invoked on a background thread.
   */
  public fun subscribe(eventNames: List<String>, listener: MessageListener): Subscription

  /**
   * Publishes a single message to the channel with the given event name and payload.
   * When publish is called with this client library, it won't attempt to implicitly attach to the channel,
   * so long as [transient publishing](https://ably.com/docs/realtime/channels#transient-publish) is available in the library.
   * Otherwise, the client will implicitly attach.
   *
   * Spec: RTL6i
   *
   * @param name the event name
   * @param data the message payload
   * @param listener A listener may optionally be passed in to this call to be notified of success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun publish(name: String? = null, data: Any? = null, listener: CompletionListener? = null)

  /**
   * Publishes a message to the channel.
   * When publish is called with this client library, it won't attempt to implicitly attach to the channel.
   *
   * Spec: RTL6i
   *
   * @param message A [Message] object.
   * @param listener A listener may optionally be passed in to this call to be notified of success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun publish(message: Message, listener: CompletionListener? = null)

  /**
   * Publishes an array of messages to the channel.
   * When publish is called with this client library, it won't attempt to implicitly attach to the channel.
   *
   * Spec: RTL6i
   *
   * @param messages A list of [Message] objects.
   * @param listener A listener may optionally be passed in to this call to be notified of success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun publish(messages: List<Message>, listener: CompletionListener? = null)

  /**
   * Sets the [ChannelOptions] for the channel.
   *
   * Spec: RTL16
   *
   * @param options A {@link ChannelOptions} object.
   */
  public fun setOptions(options: ChannelOptions)

  /**
   * This property will be removed once public API for new version of ably-java is stable
   */
  @InternalAPI
  public val javaChannel: io.ably.pubsub.realtime.Channel
}
