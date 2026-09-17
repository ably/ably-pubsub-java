package io.ably.pubsub.liveobjects

import io.ably.pubsub.types.AblyException
import io.ably.pubsub.types.ErrorInfo
import java.nio.charset.StandardCharsets

internal fun ablyException(
  errorMessage: String,
  errorCode: ObjectErrorCode,
  statusCode: ObjectHttpStatusCode = ObjectHttpStatusCode.BadRequest,
  cause: Throwable? = null,
): AblyException {
  val errorInfo = createErrorInfo(errorMessage, errorCode, statusCode)
  return createAblyException(errorInfo, cause)
}

internal fun ablyException(
  errorInfo: ErrorInfo,
  cause: Throwable? = null,
): AblyException = createAblyException(errorInfo, cause)

private fun createErrorInfo(
  errorMessage: String,
  errorCode: ObjectErrorCode,
  statusCode: ObjectHttpStatusCode,
) = ErrorInfo(errorMessage, statusCode.code, errorCode.code)

private fun createAblyException(
  errorInfo: ErrorInfo,
  cause: Throwable?,
) = cause?.let { AblyException.fromErrorInfo(it, errorInfo) }
  ?: AblyException.fromErrorInfo(errorInfo)

internal fun clientError(errorMessage: String) = ablyException(errorMessage, ObjectErrorCode.BadRequest, ObjectHttpStatusCode.BadRequest)

internal fun serverError(errorMessage: String) = ablyException(errorMessage, ObjectErrorCode.InternalError, ObjectHttpStatusCode.InternalServerError)

internal fun objectError(errorMessage: String, cause: Throwable? = null): AblyException {
  return ablyException(errorMessage, ObjectErrorCode.InvalidObject, ObjectHttpStatusCode.InternalServerError, cause)
}


/**
 * Calculates the byte size of a string.
 * For non-ASCII, the byte size can be 2–4x the character count. For ASCII, there is no difference.
 * e.g. "Hello" has a byte size of 5, while "你" has a byte size of 3 and "😊" has a byte size of 4.
 */
internal val String.byteSize: Int
  get() = this.toByteArray(StandardCharsets.UTF_8).size

/**
 * Generates a random nonce string for object creation.
 */
internal fun generateNonce(): String {
  val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" // avoid calculation using range
  return (1..16).map { chars.random() }.joinToString("")
}
