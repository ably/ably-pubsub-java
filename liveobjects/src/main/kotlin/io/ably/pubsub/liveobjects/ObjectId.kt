package io.ably.pubsub.liveobjects

import io.ably.pubsub.liveobjects.value.ObjectType
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal class ObjectId private constructor(
  internal val type: ObjectType,
  private val hash: String,
  private val timestampMs: Long
) {
  /**
   * Converts ObjectId to string representation.
   * Spec: RTO6b1
   */
  override fun toString(): String {
    return "${type.value}:$hash@$timestampMs"
  }

  companion object {

    /**
     * Spec: RTO14
     */
    internal fun fromInitialValue(objectType: ObjectType, initialValue: String, nonce: String, msTimeStamp: Long): ObjectId {
      val valueForHash = "$initialValue:$nonce".toByteArray(StandardCharsets.UTF_8)
      // RTO14b - Hash the initial value and nonce to create a unique identifier
      val hashBytes = MessageDigest.getInstance("SHA-256").digest(valueForHash)
      val urlSafeHash = Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes)

      return ObjectId(objectType, urlSafeHash, msTimeStamp)
    }

    /**
     * Creates ObjectId instance from hashed object id string.
     */
    internal fun fromString(objectId: String): ObjectId {
      if (objectId.isEmpty()) {
        throw objectError("Invalid object id: $objectId")
      }

      // Parse format: type:hash@msTimestamp
      val parts = objectId.split(':')
      if (parts.size != 2) {
        throw objectError("Invalid object id: $objectId")
      }

      val (typeStr, rest) = parts

      val type = when (typeStr) {
        "map" -> ObjectType.Map
        "counter" -> ObjectType.Counter
        else -> throw objectError("Invalid object type in object id: $objectId")
      }

      val hashAndTimestamp = rest.split('@')
      if (hashAndTimestamp.size != 2) {
        throw objectError("Invalid object id: $objectId")
      }

      val hash = hashAndTimestamp[0]

      if (hash.isEmpty()) {
        throw objectError("Invalid object id: $objectId")
      }

      val msTimestampStr = hashAndTimestamp[1]

      val msTimestamp = try {
        msTimestampStr.toLong()
      } catch (e: NumberFormatException) {
        throw objectError("Invalid object id: $objectId", e)
      }

      return ObjectId(type, hash, msTimestamp)
    }
  }
}
