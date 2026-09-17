package io.ably.pubsub.liveobjects.unit

import io.ably.pubsub.liveobjects.*
import io.ably.pubsub.liveobjects.ObjectErrorCode
import io.ably.pubsub.liveobjects.ObjectHttpStatusCode
import io.ably.pubsub.liveobjects.byteSize
import io.ably.pubsub.liveobjects.clientError
import io.ably.pubsub.liveobjects.generateNonce
import io.ably.pubsub.types.ErrorInfo
import org.junit.Test
import org.junit.Assert.*

class UtilsTest {

  @Test
  fun testGenerateNonce() {
    // Test basic functionality
    val nonce1 = generateNonce()
    val nonce2 = generateNonce()

    assertEquals(16, nonce1.length)
    assertEquals(16, nonce2.length)
    assertNotEquals(nonce1, nonce2) // Should be random

    // Test character set
    val validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val nonce = generateNonce()
    nonce.forEach { char ->
      assertTrue("Nonce should only contain valid characters", validChars.contains(char))
    }
  }

  @Test
  fun testStringByteSize() {
    // Test ASCII strings
    assertEquals(5, "Hello".byteSize)
    assertEquals(0, "".byteSize)
    assertEquals(1, "A".byteSize)

    // Test non-ASCII strings
    assertEquals(3, "你".byteSize) // Chinese character
    assertEquals(4, "😊".byteSize) // Emoji
    assertEquals(6, "你好".byteSize) // Two Chinese characters
  }

  @Test
  fun testErrorCreationFunctions() {
    // Test clientError
    val clientEx = clientError("Bad request")
    assertEquals("Bad request", clientEx.errorInfo.message)
    assertEquals(ObjectErrorCode.BadRequest.code, clientEx.errorInfo.code)
    assertEquals(ObjectHttpStatusCode.BadRequest.code, clientEx.errorInfo.statusCode)

    // Test serverError
    val serverEx = serverError("Internal error")
    assertEquals("Internal error", serverEx.errorInfo.message)
    assertEquals(ObjectErrorCode.InternalError.code, serverEx.errorInfo.code)
    assertEquals(ObjectHttpStatusCode.InternalServerError.code, serverEx.errorInfo.statusCode)

    // Test objectError
    val objectEx = objectError("Invalid object")
    assertEquals("Invalid object", objectEx.errorInfo.message)
    assertEquals(ObjectErrorCode.InvalidObject.code, objectEx.errorInfo.code)
    assertEquals(ObjectHttpStatusCode.InternalServerError.code, objectEx.errorInfo.statusCode)

    // Test objectError with cause
    val cause = RuntimeException("Original error")
    val objectExWithCause = objectError("Invalid object", cause)
    assertEquals("Invalid object", objectExWithCause.errorInfo.message)
    assertEquals(cause, objectExWithCause.cause)
  }

  @Test
  fun testAblyExceptionCreation() {
    // Test with error message and codes
    val ex = ablyException("Test error", ObjectErrorCode.BadRequest, ObjectHttpStatusCode.BadRequest)
    assertEquals("Test error", ex.errorInfo.message)
    assertEquals(ObjectErrorCode.BadRequest.code, ex.errorInfo.code)
    assertEquals(ObjectHttpStatusCode.BadRequest.code, ex.errorInfo.statusCode)

    // Test with ErrorInfo
    val errorInfo = ErrorInfo("Custom error", 400, 40000)
    val ex2 = ablyException(errorInfo)
    assertEquals("Custom error", ex2.errorInfo.message)
    assertEquals(400, ex2.errorInfo.statusCode)
    assertEquals(40000, ex2.errorInfo.code)

    // Test with cause
    val cause = RuntimeException("Cause")
    val ex3 = ablyException(errorInfo, cause)
    assertEquals(cause, ex3.cause)
  }
}
