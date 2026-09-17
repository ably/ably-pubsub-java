package io.ably.pubsub.realtime

import io.ably.pubsub.types.ChannelOptions

val ChannelBase.channelOptions: ChannelOptions?
  get() = options
