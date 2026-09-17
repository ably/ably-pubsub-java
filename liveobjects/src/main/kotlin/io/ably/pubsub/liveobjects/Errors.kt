package io.ably.pubsub.liveobjects

import io.ably.pubsub.types.AblyException
import io.ably.pubsub.types.ErrorInfo

/**
 * Error codes and helpers for the path-based public API implementation.
 * Copied (and extended with the path-API codes) from the legacy package so
 * this package has no dependency on `io.ably.pubsub.objects`.
 */
internal enum class ObjectErrorCode(val code: Int) {
  BadRequest(40_000),
  InternalError(50_000),
  MaxMessageSizeExceeded(40_009),
  InvalidObject(92_000),
  InvalidInputParams(40_003),
  MapValueDataTypeUnsupported(40_013),
  PathNotResolved(92_005), // RTPO3c2 - write operation on a path that does not resolve
  ObjectsTypeMismatch(92_007), // RTTS5d2/RTTS9d - operation on a cast wrapper with mismatched resolved type
  // Channel mode and state validation error codes
  ChannelModeRequired(40_024),
  ChannelStateError(90_001),
  PublishAndApplyFailedDueToChannelState(92_008),
}

internal enum class ObjectHttpStatusCode(val code: Int) {
  BadRequest(400),
  InternalServerError(500),
}

internal fun objectException(
  errorMessage: String,
  errorCode: ObjectErrorCode,
  statusCode: ObjectHttpStatusCode = ObjectHttpStatusCode.BadRequest,
  cause: Throwable? = null,
): AblyException {
  val errorInfo = ErrorInfo(errorMessage, statusCode.code, errorCode.code)
  return cause?.let { AblyException.fromErrorInfo(it, errorInfo) } ?: AblyException.fromErrorInfo(errorInfo)
}

/** ErrorInfo 400 / 40003 - invalid input (RTLMV4a/b, RTLCV4a, key validation). */
internal fun invalidInputError(message: String) =
  objectException(message, ObjectErrorCode.InvalidInputParams)

/** ErrorInfo 400 / 92005 - write operation on an unresolvable path (RTPO3c2). */
internal fun pathNotResolvedError(path: String) =
  objectException("Path could not be resolved: \"$path\"", ObjectErrorCode.PathNotResolved)

/** ErrorInfo 400 / 92007 - resolved/wrapped type does not match the typed wrapper (RTTS5d2/RTTS9d). */
internal fun typeMismatchError(message: String) =
  objectException(message, ObjectErrorCode.ObjectsTypeMismatch)

/** ErrorInfo 500 / 92000 - invalid internal object state. */
internal fun objectStateError(message: String) =
  objectException(message, ObjectErrorCode.InvalidObject, ObjectHttpStatusCode.InternalServerError)
