package io.ably.pubsub.liveobjects

import io.ably.pubsub.liveobjects.adapter.AblyClientAdapter
import io.ably.pubsub.realtime.ChannelState
import io.ably.pubsub.types.ProtocolMessage
import java.util.concurrent.ConcurrentHashMap

public class DefaultLiveObjectsPlugin(private val adapter: AblyClientAdapter) : LiveObjectsPlugin {

  private val objects = ConcurrentHashMap<String, DefaultRealtimeObject>()

  override fun getInstance(channelName: String): RealtimeObject {
    // Requires Java 8 / Android API 24, already mandated by this module's CompletableFuture API.
    return objects.computeIfAbsent(channelName) { DefaultRealtimeObject(channelName, adapter) }
  }

  override fun handle(msg: ProtocolMessage) {
    val channelName = msg.channel
    objects[channelName]?.handle(msg)
  }

  override fun handleStateChange(channelName: String, state: ChannelState, hasObjects: Boolean) {
    objects[channelName]?.handleStateChange(state, hasObjects)
  }

  override fun dispose(channelName: String) {
    objects.remove(channelName)
      ?.dispose(clientError("Channel has been released using channels.release()"))
  }

  override fun dispose() {
    objects.values.forEach {
      it.dispose(clientError("AblyClient has been closed using client.close()"))
    }
    objects.clear()
  }
}
