package com.ably.pubsub

import com.ably.Subscription
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.realtime.Presence.PresenceListener
import io.ably.lib.types.MessageExtras
import io.ably.lib.types.PresenceMessage
import java.util.*


/**
 * Presence for a Realtime channel
 */
public interface RealtimePresence : Presence {

  /**
   * Retrieves the current members present on the channel and the metadata for each member,
   * such as their [io.ably.lib.types.PresenceMessage.Action] and ID.
   * Returns an array of [PresenceMessage] objects.
   *
   * Spec: RTP11
   *
   * @param waitForSync (RTP11c1) - Sets whether to wait for a full presence set synchronization between Ably and the clients on
   * the channel to complete before returning the results.
   * Synchronization begins as soon as the channel is [ChannelState.attached].
   * When set to true the results will be returned as soon as the sync is complete.
   * When set to false the current list of members will be returned without the sync completing.
   * The default is true.
   * @param clientId (RTP11c2) - Filters the array of returned presence members by a specific client using its ID.
   * @param connectionId (RTP11c3) - Filters the array of returned presence members by a specific connection using its ID.
   * @return A list of [PresenceMessage] objects.
   */
  public fun get(clientId: String? = null, connectionId: String? = null, waitForSync: Boolean = true): List<PresenceMessage>

  /**
   * Registers a listener that is called each time a [PresenceMessage] matching a given [PresenceMessage.Action],
   * or an action within an array of [PresenceMessage.Action], is received on the channel,
   * such as a new member entering the presence set.
   *
   * Spec: RTP6a
   *
   * @param listener An event listener function.
   * The listener is invoked on a background thread.
   */
  public fun subscribe(listener: PresenceListener): Subscription

  /**
   * Registers a listener that is called each time a [PresenceMessage] matching a given [PresenceMessage.Action],
   * or an action within an array of [PresenceMessage.Action], is received on the channel,
   * such as a new member entering the presence set.
   *
   * Spec: RTP6b
   *
   * @param action A [PresenceMessage.Action] to register the listener for.
   * @param listener An event listener function.
   * The listener is invoked on a background thread.
   */
  public fun subscribe(action: PresenceMessage.Action, listener: PresenceListener): Subscription

  /**
   * Registers a listener that is called each time a [PresenceMessage] matching a given [PresenceMessage.Action],
   * or an action within an array of [PresenceMessage.Action], is received on the channel,
   * such as a new member entering the presence set.
   *
   * Spec: RTP6b
   *
   * @param actions An array of [PresenceMessage.Action] to register the listener for.
   * @param listener An event listener function.
   * The listener is invoked on a background thread.
   */
  public fun subscribe(actions: EnumSet<PresenceMessage.Action>, listener: PresenceListener): Subscription

  /**
   * Enters the presence set for the channel, optionally passing a data payload.
   * A clientId is required to be present on a channel.
   * An optional callback may be provided to notify of the success or failure of the operation.
   *
   * Spec: RTP8
   *
   * @param data The payload associated with the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun enter(data: Any? = null, listener: CompletionListener? = null): Unit =
    enter(data, null, listener)

  /**
   * Enters the presence set for the channel, optionally passing a data payload and extras.
   * A clientId is required to be present on a channel.
   * An optional callback may be provided to notify of the success or failure of the operation.
   *
   * Spec: RTP8
   *
   * @param data The payload associated with the presence member.
   * @param extras The extras associated with the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun enter(data: Any?, extras: MessageExtras?, listener: CompletionListener? = null)

  /**
   * Updates the data payload for a presence member.
   * If called before entering the presence set, this is treated as an [PresenceMessage.Action.enter] event.
   * An optional callback may be provided to notify of the success or failure of the operation.
   *
   * Spec: RTP9
   *
   * @param data The payload associated with the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun update(data: Any? = null, listener: CompletionListener? = null): Unit =
    update(data, null, listener)

  /**
   * Updates the data payload for a presence member, optionally passing extras.
   * If called before entering the presence set, this is treated as an [PresenceMessage.Action.enter] event.
   * An optional callback may be provided to notify of the success or failure of the operation.
   *
   * Spec: RTP9
   *
   * @param data The payload associated with the presence member.
   * @param extras The extras associated with the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun update(data: Any?, extras: MessageExtras?, listener: CompletionListener? = null)

  /**
   * Leaves the presence set for the channel.
   * A client must have previously entered the presence set before they can leave it.
   *
   * Spec: RTP10
   *
   * @param data The payload associated with the presence member.
   * @param listener a listener to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun leave(data: Any? = null, listener: CompletionListener? = null): Unit =
    leave(data, null, listener)

  /**
   * Leaves the presence set for the channel, optionally passing extras.
   * A client must have previously entered the presence set before they can leave it.
   *
   * Spec: RTP10
   *
   * @param data The payload associated with the presence member.
   * @param extras The extras associated with the presence member.
   * @param listener a listener to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun leave(data: Any?, extras: MessageExtras?, listener: CompletionListener? = null)

  /**
   * Enters the presence set of the channel for a given clientId.
   * Enables a single client to update presence on behalf of any number of clients using a single connection.
   * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
   *
   * Spec: RTP4, RTP14, RTP15
   *
   * @param clientId The ID of the client to enter into the presence set.
   * @param data The payload associated with the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun enterClient(clientId: String, data: Any? = null, listener: CompletionListener? = null): Unit =
    enterClient(clientId, data, null, listener)

  /**
   * Enters the presence set of the channel for a given clientId, optionally passing extras.
   * Enables a single client to update presence on behalf of any number of clients using a single connection.
   * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
   *
   * Spec: RTP4, RTP14, RTP15
   *
   * @param clientId The ID of the client to enter into the presence set.
   * @param data The payload associated with the presence member.
   * @param extras The extras associated with the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun enterClient(clientId: String, data: Any?, extras: MessageExtras?, listener: CompletionListener? = null)

  /**
   * Updates the data payload for a presence member using a given clientId.
   * Enables a single client to update presence on behalf of any number of clients using a single connection.
   * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
   * An optional callback may be provided to notify of the success or failure of the operation.
   *
   * Spec: RTP15
   *
   * @param clientId The ID of the client to update in the presence set.
   * @param data The payload to update for the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun updateClient(clientId: String, data: Any? = null, listener: CompletionListener? = null): Unit =
    updateClient(clientId, data, null, listener)

  /**
   * Updates the data payload for a presence member using a given clientId, optionally passing extras.
   * Enables a single client to update presence on behalf of any number of clients using a single connection.
   * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
   * An optional callback may be provided to notify of the success or failure of the operation.
   *
   * Spec: RTP15
   *
   * @param clientId The ID of the client to update in the presence set.
   * @param data The payload to update for the presence member.
   * @param extras The extras associated with the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun updateClient(clientId: String, data: Any?, extras: MessageExtras?, listener: CompletionListener? = null)

  /**
   * Leaves the presence set of the channel for a given clientId.
   * Enables a single client to update presence on behalf of any number of clients using a single connection.
   * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
   *
   * Spec: RTP15
   *
   * @param clientId The ID of the client to leave the presence set for.
   * @param data The payload associated with the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun leaveClient(clientId: String?, data: Any? = null, listener: CompletionListener? = null): Unit =
    leaveClient(clientId, data, null, listener)

  /**
   * Leaves the presence set of the channel for a given clientId, optionally passing extras.
   * Enables a single client to update presence on behalf of any number of clients using a single connection.
   * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
   *
   * Spec: RTP15
   *
   * @param clientId The ID of the client to leave the presence set for.
   * @param data The payload associated with the presence member.
   * @param extras The extras associated with the presence member.
   * @param listener A callback to notify of the success or failure of the operation.
   * This listener is invoked on a background thread.
   */
  public fun leaveClient(clientId: String?, data: Any?, extras: MessageExtras?, listener: CompletionListener? = null)
}
