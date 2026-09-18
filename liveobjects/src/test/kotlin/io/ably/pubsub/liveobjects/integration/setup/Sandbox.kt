package io.ably.pubsub.liveobjects.integration.setup

import io.ably.pubsub.liveobjects.ablyException
import io.ably.pubsub.liveobjects.integration.helpers.HttpObjects
import io.ably.pubsub.realtime.RealtimeClient
import io.ably.pubsub.realtime.RealtimeClientFactory
import io.ably.pubsub.realtime.ConnectionEvent
import io.ably.pubsub.realtime.ConnectionState
import io.ably.pubsub.types.ClientOptions
import io.ably.pubsub.uts.infra.integration.SandboxApp
import kotlinx.coroutines.CompletableDeferred

/**
 * A sandbox test app for the LiveObjects integration tests. Provisioning is delegated to the
 * shared [SandboxApp] fixture from `:uts` (single source of truth for the sandbox host and the
 * canonical `test-app-setup.json` app spec); this type just carries the fields the local
 * client-factory extensions need.
 */
class Sandbox private constructor(private val app: SandboxApp, val appId: String, val apiKey: String) {
  companion object {
    internal suspend fun createInstance(): Sandbox {
      val app = SandboxApp.create()
      // defaultKey is the full-capability "appId.keyId:keySecret" key (index 0 of the app spec)
      return Sandbox(app = app, appId = app.appId, apiKey = app.defaultKey)
    }
  }

  /** Best-effort teardown of the provisioned sandbox app (see [SandboxApp.delete]). */
  internal suspend fun delete() = app.delete()
}

internal fun Sandbox.createRealtimeClient(options: ClientOptions.() -> Unit): RealtimeClient {
  val clientOptions = ClientOptions().apply {
    apply(options)
    key = apiKey
    environment = "sandbox"
  }
  return RealtimeClientFactory.create(clientOptions)
}

internal fun Sandbox.createHttpObjects(): HttpObjects {
  val options = ClientOptions().apply {
    key = apiKey
    environment = "sandbox"
    useBinaryProtocol = false
  }
  return HttpObjects(options)
}

internal suspend fun RealtimeClient.ensureConnected() {
  if (this.connection.state == ConnectionState.connected) {
    return
  }
  val connectedDeferred = CompletableDeferred<Unit>()
  this.connection.on {
    if (it.event == ConnectionEvent.connected) {
      connectedDeferred.complete(Unit)
      this.connection.off()
    } else if (it.event != ConnectionEvent.connecting) {
      connectedDeferred.completeExceptionally(ablyException(it.reason))
      this.connection.off()
      this.close()
    }
  }
  connectedDeferred.await()
}
