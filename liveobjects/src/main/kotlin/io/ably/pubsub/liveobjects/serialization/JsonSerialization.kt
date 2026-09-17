package io.ably.pubsub.liveobjects.serialization

import com.google.gson.*
import io.ably.pubsub.liveobjects.message.WireObjectData
import io.ably.pubsub.liveobjects.message.WireObjectMessage
import io.ably.pubsub.liveobjects.message.WireObjectOperationAction
import io.ably.pubsub.liveobjects.message.WireObjectsMapSemantics
import java.lang.reflect.Type
import kotlin.enums.EnumEntries

// Gson instance for JSON serialization/deserialization
internal val gson = GsonBuilder()
  .registerTypeAdapter(WireObjectOperationAction::class.java, EnumCodeTypeAdapter({ it.code }, WireObjectOperationAction.entries))
  .registerTypeAdapter(WireObjectsMapSemantics::class.java, EnumCodeTypeAdapter({ it.code }, WireObjectsMapSemantics.entries))
  .create()

internal fun WireObjectMessage.toJsonObject(): JsonObject {
  return gson.toJsonTree(this).asJsonObject
}

internal fun JsonObject.toObjectMessage(): WireObjectMessage {
  return gson.fromJson(this, WireObjectMessage::class.java)
}

internal class EnumCodeTypeAdapter<T : Enum<T>>(
  private val getCode: (T) -> Int,
  private val enumValues: EnumEntries<T>
) : JsonSerializer<T>, JsonDeserializer<T> {

  override fun serialize(src: T, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
    return JsonPrimitive(getCode(src))
  }

  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T {
    val code = json.asInt
    return enumValues.firstOrNull { getCode(it) == code } ?: enumValues.firstOrNull { getCode(it) == -1 }
      ?: throw JsonParseException("Unknown enum code: $code and no Unknown fallback found")
  }
}

internal class WireObjectDataJsonSerializer : JsonSerializer<WireObjectData>, JsonDeserializer<WireObjectData> {
  override fun serialize(src: WireObjectData, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
    val obj = JsonObject()
    src.objectId?.let { obj.addProperty("objectId", it) }
    src.string?.let { obj.addProperty("string", it) }
    src.number?.let { obj.addProperty("number", it) }
    src.boolean?.let { obj.addProperty("boolean", it) }
    src.bytes?.let { obj.addProperty("bytes", it) }
    src.json?.let { obj.addProperty("json", it.toString()) } // Spec: OD4c5
    return obj
  }

  override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): WireObjectData {
    val obj = if (json.isJsonObject) json.asJsonObject else throw JsonParseException("Expected JsonObject")
    val objectId = if (obj.has("objectId")) obj.get("objectId").asString else null
    val string = if (obj.has("string")) obj.get("string").asString else null
    val number = if (obj.has("number")) obj.get("number").asDouble else null
    val boolean = if (obj.has("boolean")) obj.get("boolean").asBoolean else null
    val bytes = if (obj.has("bytes")) obj.get("bytes").asString else null
    val json = if (obj.has("json")) JsonParser.parseString(obj.get("json").asString) else null

    if (objectId == null && string == null && number == null && boolean == null && bytes == null && json == null) {
      throw JsonParseException("Since objectId is not present, at least one of the value fields must be present")
    }
    return WireObjectData(objectId = objectId, string = string, number = number, boolean = boolean, bytes = bytes, json = json)
  }
}
