package com.ably.pubsub

import com.ably.annotations.InternalAPI
import io.ably.pubsub.realtime.RealtimeClient
import io.ably.pubsub.realtime.Connection

/**
 * A client that extends the functionality of the {@link Client} and provides additional realtime-specific features.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public interface RealtimeClient : Client {

  /**
   * The {@link Connection} object for this instance.
   * <p>
   * Spec: RTC2
   */
  public val connection: Connection

  /**
   * Collection of [RealtimeChannel] instances currently managed by Realtime client
   */
  override val channels: Channels<out RealtimeChannel>

  /**
   * This property will be removed once public API for new version of ably-java is stable
   */
  @InternalAPI
  public val javaClient: RealtimeClient
}
