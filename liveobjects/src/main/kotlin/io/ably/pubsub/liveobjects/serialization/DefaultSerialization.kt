package io.ably.pubsub.liveobjects.serialization

import com.google.gson.*
import io.ably.pubsub.liveobjects.message.WireObjectMessage
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker

/**
 * Default implementation of {@link ObjectSerializer} that handles serialization/deserialization
 * of WireObjectMessage arrays for both JSON and MessagePack formats using Gson and MessagePack.
 * Dynamically loaded by ObjectSerializer#tryGet() to avoid hard dependencies.
 */
@Suppress("unused") // Used via reflection in ObjectSerializer.Holder
internal class DefaultObjectsSerializer : ObjectSerializer {

  override fun readMsgpackArray(unpacker: MessageUnpacker): Array<Any> {
    val objectMessagesCount = unpacker.unpackArrayHeader()
    return Array(objectMessagesCount) { readObjectMessage(unpacker) }
  }

  override fun writeMsgpackArray(objects: Array<out Any>, packer: MessagePacker) {
    val objectMessages = objects.map { it as WireObjectMessage }
    packer.packArrayHeader(objectMessages.size)
    objectMessages.forEach { it.writeMsgpack(packer) }
  }

  override fun readFromJsonArray(json: JsonArray): Array<Any> {
    return json.map { element ->
      if (element.isJsonObject) element.asJsonObject.toObjectMessage()
      else throw JsonParseException("Expected JsonObject, but found: $element")
    }.toTypedArray()
  }

  override fun asJsonArray(objects: Array<out Any>): JsonArray {
    val objectMessages = objects.map { it as WireObjectMessage }
    val jsonArray = JsonArray()
    for (objectMessage in objectMessages) {
      jsonArray.add(objectMessage.toJsonObject())
    }
    return jsonArray
  }
}
