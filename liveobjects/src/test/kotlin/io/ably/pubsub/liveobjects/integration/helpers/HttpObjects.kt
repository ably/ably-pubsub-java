package io.ably.pubsub.liveobjects.integration.helpers

import com.google.gson.JsonObject
import io.ably.pubsub.liveobjects.message.WireObjectData
import io.ably.pubsub.http.HttpClientFactory
import io.ably.pubsub.http.PubSubHttpClient
import io.ably.pubsub.http.HttpUtils
import io.ably.pubsub.liveobjects.integration.helpers.fixtures.DataFixtures
import io.ably.pubsub.types.ClientOptions

/**
 * Helper class to create pre-determined objects and modify them on channels using rest api.
 */
internal class HttpObjects(options: ClientOptions) {

  private val pubSubHttpClient: PubSubHttpClient = HttpClientFactory.create(options)

  /**
   * Creates a new map object on the channel with optional initial data.
   * @return The object ID of the created map
   */
  internal fun createMap(channelName: String, data: Map<String, WireObjectData>? = null): String {
    val mapCreateOp = PayloadBuilder.mapCreateHttpOp(data = data)
    return operationRequest(channelName, mapCreateOp).objectId ?:
    throw Exception("Failed to create map: no objectId returned")
  }

  /**
   * Sets a value (primitives, JsonObject, JsonArray, etc.) at the specified key in an existing map.
   */
  internal fun setMapValue(channelName: String, mapObjectId: String, key: String, data: WireObjectData) {
    val mapCreateOp = PayloadBuilder.mapSetHttpOp(mapObjectId, key, data)
    operationRequest(channelName, mapCreateOp)
  }

  /**
   * Sets an object reference at the specified key in an existing map.
   */
  internal fun setMapRef(channelName: String, mapObjectId: String, key: String, refMapObjectId: String) {
    val mapCreateOp = PayloadBuilder.mapSetHttpOp(mapObjectId, key, DataFixtures.mapRef(refMapObjectId))
    operationRequest(channelName, mapCreateOp)
  }

  /**
   * Removes a key-value pair from an existing map.
   */
  internal fun removeMapValue(channelName: String, mapObjectId: String, key: String) {
    val mapRemoveOp = PayloadBuilder.mapRemoveHttpOp(mapObjectId, key)
    operationRequest(channelName, mapRemoveOp)
  }

  /**
   * Creates a new counter object with an optional initial value (defaults to 0).
   * @return The object ID of the created counter
   */
  internal fun createCounter(channelName: String, initialValue: Double? = null): String {
    val counterCreateOp = PayloadBuilder.counterCreateHttpOp(number = initialValue)
    return operationRequest(channelName, counterCreateOp).objectId
      ?: throw Exception("Failed to create counter: no objectId returned")
  }

  /**
   * Increments an existing counter by the specified amount.
   */
  internal fun incrementCounter(channelName: String, counterObjectId: String, incrementBy: Double) {
    val counterIncrementOp = PayloadBuilder.counterIncHttpOp(counterObjectId, incrementBy)
    operationRequest(channelName, counterIncrementOp)
  }

  /**
   * Decrements an existing counter by the specified amount.
   */
  internal fun decrementCounter(channelName: String, counterObjectId: String, decrementBy: Double) {
    val counterDecrementOp = PayloadBuilder.counterIncHttpOp(counterObjectId, -decrementBy)
    operationRequest(channelName, counterDecrementOp)
  }

  /**
   * Core method that executes object operations by sending POST requests to Ably's Objects REST API.
   * All public methods delegate to this for actual API communication.
   */
  private fun operationRequest(channelName: String, opBody: JsonObject): OperationResult {
    try {
      val path = "/channels/$channelName/objects"
      val requestBody = HttpUtils.requestBodyFromGson(opBody, pubSubHttpClient.options.useBinaryProtocol)

      val response = pubSubHttpClient.request("POST", path, null, requestBody, null)

      if (!response.success) {
        throw Exception("REST operation failed: HTTP ${response.statusCode} - ${response.errorMessage}")
      }

      val responseItems = response.items()
      if (responseItems.isEmpty()) {
        return OperationResult(null, null, success = true)
      }

      // Process first response item
      responseItems[0].asJsonObject.let { firstItem ->
        val objectIds = firstItem.get("objectIds")?.let { element ->
          if (element.isJsonArray) element.asJsonArray.map { it.asString } else null
        }
        return OperationResult(objectIds?.firstOrNull(), objectIds, success = true)
      }
    } catch (e: Exception) {
      throw Exception("Failed to execute operation request: ${e.message}", e)
    }
  }

  /**
   * Result class for operation requests containing the response data and extracted object ID.
   */
  private data class OperationResult(
    val objectId: String?,
    val objectIds: List<String>? = null, // Seems only used for batch operations
    val success: Boolean = true
  )
}
