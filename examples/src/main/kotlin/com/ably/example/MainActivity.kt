package com.ably.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ably.example.screen.MainScreen
import com.ably.example.ui.theme.AblyTheme
import io.ably.pubsub.device.PubSubDevice
import io.ably.pubsub.realtime.RealtimeClient
import io.ably.pubsub.http.Auth
import io.ably.pubsub.util.Log
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
  private val realtimeClient: RealtimeClient by lazy {
    // This app runs on an end-user device, so it builds its client through the device door.
    PubSubDevice.clientBuilder()
      .apply {
        if (BuildConfig.ABLY_KEY.isBlank()) {
          authCallback(
            Auth.TokenCallback {
              val apiKey = runBlocking {
                val sandbox = Sandbox.getInstance()
                sandbox.apiKey
              }
              // A throwaway client that only signs a token; it never connects.
              PubSubDevice.clientBuilder()
                .key(apiKey)
                .environment("sandbox")
                .autoConnect(false)
                .build()
                .use { it.auth.requestToken(null, null) }
            }
          )
          environment("sandbox")
        } else {
          key(BuildConfig.ABLY_KEY)
        }
        logLevel(Log.VERBOSE)
        autoConnect(false)
      }
      .build()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AblyTheme {
        MainScreen(realtimeClient)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    realtimeClient.connect()
  }

  override fun onStop() {
    super.onStop()
    realtimeClient.close()
  }

  override fun onResume() {
    super.onResume()
    realtimeClient.connect()
  }

  override fun onPause() {
    super.onPause()
    realtimeClient.close()
  }
}
