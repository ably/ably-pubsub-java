package com.ably.pubsub

public data class WrapperSdkProxyOptions(val agents: Map<String, String>)

public interface SdkWrapperCompatible<T> {

  /**
   * Creates a proxy client to be used to supply analytics information for Ably-authored SDKs.
   * The proxy client shares the state of the `RealtimeClient` instance on which this method is called.
   * This method should only be called by Ably-authored SDKs.
   */
  public fun createWrapperSdkProxy(options: WrapperSdkProxyOptions): T
}

public fun RealtimeClient.createWrapperSdkProxy(options: WrapperSdkProxyOptions): RealtimeClient =
  (this as SdkWrapperCompatible<*>).createWrapperSdkProxy(options) as RealtimeClient
