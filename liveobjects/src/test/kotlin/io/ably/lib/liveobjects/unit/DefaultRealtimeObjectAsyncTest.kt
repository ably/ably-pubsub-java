package io.ably.lib.liveobjects.unit

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

/**
 * Unit tests for [DefaultRealtimeObject.asyncFuture] / [DefaultRealtimeObject.asyncVoidFuture]:
 * result and exception propagation, plus the SupervisorJob guarantee that a failed operation
 * must not cancel the shared sequential scope for subsequent (sibling) operations.
 */
class DefaultRealtimeObjectAsyncTest {

  private val created = mutableListOf<DefaultRealtimeObject>()

  private fun newObject(): DefaultRealtimeObject =
    DefaultRealtimeObject("ch", getMockAblyClientAdapter()).also { created += it }

  private fun boom() = AblyException.fromErrorInfo(ErrorInfo("boom", 400, 40000))

  @After
  fun tearDown() {
    created.forEach { it.objectsPool.dispose() } // the pool init starts a real GC coroutine
    unmockkAll() // getMockAblyClientAdapter uses mockkStatic - clean up global state
  }

  @Test
  fun asyncFutureCompletesWithResult() {
    val result = newObject().asyncFuture { 42 }.get(2, TimeUnit.SECONDS)
    assertEquals(42, result)
  }

  @Test
  fun asyncVoidFutureCompletesWithNullOnSuccess() {
    val result = newObject().asyncVoidFuture { /* no-op mutation */ }.get(2, TimeUnit.SECONDS)
    assertNull(result) // Void's only inhabitant
  }

  @Test
  fun asyncFuturePropagatesException() {
    val boom = boom()
    val ex = assertFailsWith<ExecutionException> {
      newObject().asyncFuture<Unit> { throw boom }.get(2, TimeUnit.SECONDS)
    }
    assertSame(boom, ex.cause) // original exception surfaced to the caller
  }

  @Test
  fun asyncVoidFuturePropagatesException() {
    val boom = boom()
    val ex = assertFailsWith<ExecutionException> {
      newObject().asyncVoidFuture { throw boom }.get(2, TimeUnit.SECONDS)
    }
    // the thenApply { null } bridge must forward the failure, not swallow it
    assertSame(boom, ex.cause)
  }

  @Test
  fun failedOperationDoesNotCancelScopeForSiblings() {
    val obj = newObject()
    val boom = boom()
    // a mutating op fails on the sequential scope...
    assertFailsWith<ExecutionException> {
      obj.asyncVoidFuture { throw boom }.get(2, TimeUnit.SECONDS)
    }
    // ...and a subsequent op on the same scope still succeeds - proves SupervisorJob isolation
    // (a plain Job would have cancelled the scope, failing this call).
    assertEquals(7, obj.asyncFuture { 7 }.get(2, TimeUnit.SECONDS))
  }
}
