package com.ably.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ably.example.screen.MainScreen
import com.ably.example.ui.theme.AblyTheme
import io.ably.pubsub.realtime.RealtimeClient
import io.ably.pubsub.http.PubSubHttpClient
import io.ably.pubsub.http.Auth
import io.ably.pubsub.types.ClientOptions
import io.ably.pubsub.util.Log
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
  private val realtimeClient: RealtimeClient by lazy {
    RealtimeClient(
      ClientOptions().apply {
        if (BuildConfig.ABLY_KEY.isBlank()) {
          authCallback = Auth.TokenCallback {
            val apiKey = runBlocking {
              val sandbox = Sandbox.getInstance()
              sandbox.apiKey
            }
            PubSubHttpClient(ClientOptions().apply {
              key = apiKey
              environment = "sandbox"
            }).auth.requestToken(null, null)
          }
          environment = "sandbox"
        } else {
          key = BuildConfig.ABLY_KEY
        }
        logLevel = Log.VERBOSE
        autoConnect = false
      }
    )
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
