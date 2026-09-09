package io.ably.lib.liveobjects.integration.setup

import io.ably.lib.liveobjects.ablyException
import io.ably.lib.liveobjects.integration.helpers.RestObjects
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.ConnectionEvent
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.ClientOptions
import io.ably.lib.uts.infra.integration.SandboxApp
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

internal fun Sandbox.createRealtimeClient(options: ClientOptions.() -> Unit): AblyRealtime {
  val clientOptions = ClientOptions().apply {
    apply(options)
    key = apiKey
    environment = "sandbox"
  }
  return AblyRealtime(clientOptions)
}

internal fun Sandbox.createRestObjects(): RestObjects {
  val options = ClientOptions().apply {
    key = apiKey
    environment = "sandbox"
    useBinaryProtocol = false
  }
  return RestObjects(options)
}

internal suspend fun AblyRealtime.ensureConnected() {
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
