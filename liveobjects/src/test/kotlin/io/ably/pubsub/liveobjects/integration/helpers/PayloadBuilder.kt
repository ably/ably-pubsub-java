package io.ably.pubsub.liveobjects.integration.helpers

import com.google.gson.JsonObject
import io.ably.pubsub.liveobjects.generateNonce
import io.ably.pubsub.liveobjects.message.WireObjectData
import io.ably.pubsub.liveobjects.message.WireObjectOperationAction
import io.ably.pubsub.liveobjects.serialization.gson

internal object PayloadBuilder {
  /**
   * Action strings for REST API operations.
   * Maps ObjectOperationAction enum values to their string representations.
   */
  private val ACTION_STRINGS = mapOf(
    WireObjectOperationAction.MapCreate to "MAP_CREATE",
    WireObjectOperationAction.MapSet to "MAP_SET",
    WireObjectOperationAction.MapRemove to "MAP_REMOVE",
    WireObjectOperationAction.CounterCreate to "COUNTER_CREATE",
    WireObjectOperationAction.CounterInc to "COUNTER_INC",
  )

  /**
   * Creates a MAP_CREATE operation payload for REST API.
   *
   * @param objectId Optional specific object ID
   * @param data Optional initial data for the map
   * @param nonce Optional nonce for deterministic object ID generation
   */
  internal fun mapCreateHttpOp(
    objectId: String? = null,
    data: Map<String, WireObjectData>? = null,
    nonce: String? = null,
  ): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[WireObjectOperationAction.MapCreate])
    }

    if (data != null) {
      opBody.add("data", gson.toJsonTree(data))
    }

    if (objectId != null) {
      opBody.addProperty("objectId", objectId)
      opBody.addProperty("nonce", nonce ?: generateNonce())
    }

    return opBody
  }


  /**
   * Creates a MAP_SET operation payload for REST API.
   */
  internal fun mapSetHttpOp(objectId: String, key: String, value: WireObjectData): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[WireObjectOperationAction.MapSet])
      addProperty("objectId", objectId)
    }

    val dataObj = JsonObject().apply {
      addProperty("key", key)
      add("value", gson.toJsonTree(value))
    }
    opBody.add("data", dataObj)

    return opBody
  }

  /**
   * Creates a MAP_REMOVE operation payload for REST API.
   */
  internal fun mapRemoveHttpOp(objectId: String, key: String): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[WireObjectOperationAction.MapRemove])
      addProperty("objectId", objectId)
    }

    val dataObj = JsonObject().apply {
      addProperty("key", key)
    }
    opBody.add("data", dataObj)

    return opBody
  }

  /**
   * Creates a COUNTER_CREATE operation payload for REST API.
   *
   * @param objectId Optional specific object ID
   * @param nonce Optional nonce for deterministic object ID generation
   * @param number Optional initial counter value
   */
  internal fun counterCreateHttpOp(
    objectId: String? = null,
    number: Double? = null,
    nonce: String? = null,
  ): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[WireObjectOperationAction.CounterCreate])
    }

    if (number != null) {
      val dataObj = JsonObject().apply {
        addProperty("number", number)
      }
      opBody.add("data", dataObj)
    }

    if (objectId != null) {
      opBody.addProperty("objectId", objectId)
      opBody.addProperty("nonce", nonce ?: generateNonce())
    }

    return opBody
  }

  /**
   * Creates a COUNTER_INC operation payload for REST API.
   */
  internal fun counterIncHttpOp(objectId: String, number: Double): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[WireObjectOperationAction.CounterInc])
      addProperty("objectId", objectId)
      add("data", JsonObject().apply {
        addProperty("number", number)
      })
    }
    return opBody
  }
}
