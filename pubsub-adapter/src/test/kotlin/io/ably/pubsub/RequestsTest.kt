package io.ably.pubsub

import app.cash.turbine.test
import com.ably.*
import io.ably.pubsub.realtime.RealtimeClient
import io.ably.pubsub.types.AsyncHttpPaginatedResponse
import io.ably.pubsub.types.ErrorInfo
import io.ably.pubsub.types.Param
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestsTest {

  @Test
  fun `should encode params on pagination requests`() = runTest {
    val client = createRealtimeClient()
    server.servedRequests.test {
      val paginatedResult = client.request("GET", "/page", arrayOf(Param("foo", "b a r")), null, null)
      assertEquals(mapOf("foo" to "b a r"), awaitItem().params)
      paginatedResult.next()
      assertEquals(mapOf("param" to "1@1 2"), awaitItem().params)
    }
  }

  @Test
  fun `should encode params on async pagination requests`() = runTest {
    val client = createRealtimeClient()
    server.servedRequests.test {
      val paginatedResult = suspendCancellableCoroutine { continuation ->
        client.requestAsync("GET", "/page", arrayOf(Param("foo", "b a r")), null, null, object : AsyncHttpPaginatedResponse.Callback {
          override fun onResponse(response: AsyncHttpPaginatedResponse?) {
            continuation.resume(response!!)
          }

          override fun onError(reason: ErrorInfo?) {
            continuation.resumeWithException(IllegalArgumentException(reason.toString()))
          }

        })
      }
      assertEquals(mapOf("foo" to "b a r"), awaitItem().params)
      suspendCancellableCoroutine { continuation ->
        paginatedResult.next(object : AsyncHttpPaginatedResponse.Callback {
          override fun onResponse(response: AsyncHttpPaginatedResponse?) {
            continuation.resume(response!!)
          }

          override fun onError(reason: ErrorInfo?) {
            continuation.resumeWithException(IllegalArgumentException(reason.toString()))
          }
        })
      }
      assertEquals(mapOf("param" to "1@1 2"), awaitItem().params)
    }
  }

  companion object {

    private const val PORT = 27332
    private lateinit var server: EmbeddedServer

    @JvmStatic
    @BeforeAll
    fun setUp() = runTest {
      server = EmbeddedServer(PORT) {
        when (it.path) {
          "/page" -> json("[]", buildMap {
            put("Link", "<./page?param=1%401%202>; rel=\"next\"")
          })

          else -> error("Unhandled ${it.path}")
        }
      }
      server.start()
      waitFor { server.wasStarted() }
    }

    @JvmStatic
    @AfterAll
    fun tearDown() {
      server.stop()
    }

    private fun createRealtimeClient(): RealtimeClient = createCoreRealtimeClient(PORT)
  }
}
