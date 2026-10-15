package io.ably.pubsub.liveobjects.integration.helpers.fixtures

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.pubsub.liveobjects.message.WireObjectData
import java.util.Base64

internal object DataFixtures {

  /** Test fixture for string value ("stringValue") data type */
  internal val stringData = WireObjectData(string = "stringValue")

  /** Test fixture for empty string data type */
  internal val emptyStringData = WireObjectData(string = "")

  /** Test fixture for binary data containing encoded JSON */
  internal val bytesData = WireObjectData(
    bytes = Base64.getEncoder().encodeToString("eyJwcm9kdWN0SWQiOiAiMDAxIiwgInByb2R1Y3ROYW1lIjogImNhciJ9".toByteArray()))

  /** Test fixture for empty binary data (zero-length byte array) */
  internal val emptyBytesData = WireObjectData(bytes = Base64.getEncoder().encodeToString(ByteArray(0)))

  /** Test fixture for maximum safe number value */
  internal val maxSafeNumberData = WireObjectData(number = 99999999.0)

  /** Test fixture for minimum safe number value */
  internal val negativeMaxSafeNumberData = WireObjectData(number = -99999999.0)

  /** Test fixture for positive number value (1) */
  internal val numberData = WireObjectData(number = 1.0)

  /** Test fixture for zero number value */
  internal val zeroData = WireObjectData(number = 0.0)

  /** Test fixture for boolean true value */
  internal val trueData = WireObjectData(boolean = true)

  /** Test fixture for boolean false value */
  internal val falseData = WireObjectData(boolean = false)

  /** Test fixture for JSON object value with single property */
  internal val objectData = WireObjectData(json = JsonObject().apply { addProperty("foo", "bar") })

  /** Test fixture for JSON array value with three string elements */
  internal val arrayData = WireObjectData(
    json = JsonArray().apply {
      add("foo")
      add("bar")
      add("baz")
    }
  )

  /**
   * Creates an ObjectData instance that references another map object.
   * @param referencedMapObjectId The object ID of the referenced map
   */
  internal fun mapRef(referencedMapObjectId: String) = WireObjectData(objectId = referencedMapObjectId)

  /**
   * Creates a test fixture map containing all supported data types and values.
   * @param referencedMapObjectId The object ID to be used for the map reference entry
   */
  internal fun mapWithAllValues(referencedMapObjectId: String? = null): Map<String, WireObjectData> {
    val baseMap = mapOf(
      "string" to stringData,
      "emptyString" to emptyStringData,
      "bytes" to bytesData,
      "emptyBytes" to emptyBytesData,
      "maxSafeNumber" to maxSafeNumberData,
      "negativeMaxSafeNumber" to negativeMaxSafeNumberData,
      "number" to numberData,
      "zero" to zeroData,
      "true" to trueData,
      "false" to falseData,
      "object" to objectData,
      "array" to arrayData
    )
    referencedMapObjectId?.let {
      return baseMap + ("mapRef" to mapRef(it))
    }
    return baseMap
  }
}
