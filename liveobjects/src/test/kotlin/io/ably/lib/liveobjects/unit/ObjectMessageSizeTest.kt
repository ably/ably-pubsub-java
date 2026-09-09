package io.ably.lib.liveobjects.unit

import com.google.gson.JsonObject
import io.ably.lib.liveobjects.connectionManager
import io.ably.lib.liveobjects.ensureMessageSizeWithinLimit
import io.ably.lib.liveobjects.message.WireCounterCreate
import io.ably.lib.liveobjects.message.WireCounterCreateWithObjectId
import io.ably.lib.liveobjects.message.WireCounterInc
import io.ably.lib.liveobjects.message.WireMapCreate
import io.ably.lib.liveobjects.message.WireMapCreateWithObjectId
import io.ably.lib.liveobjects.message.WireMapSet
import io.ably.lib.liveobjects.message.WireObjectData
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.message.WireObjectsCounter
import io.ably.lib.liveobjects.message.WireObjectsMap
import io.ably.lib.liveobjects.message.WireObjectsMapEntry
import io.ably.lib.liveobjects.message.WireObjectsMapSemantics
import io.ably.lib.liveobjects.message.size
import io.ably.lib.transport.Defaults
import io.ably.lib.types.AblyException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ObjectMessageSizeTest {
  @Test
  fun testObjectMessageSizeWithinLimit() = runTest {
    val mockAdapter = getMockAblyClientAdapter()
    mockAdapter.connectionManager.maxMessageSize = Defaults.maxMessageSize // 64 kb
    assertEquals(65536, mockAdapter.connectionManager.maxMessageSize)

    // ObjectMessage with all size-contributing fields
    val objectMessage = WireObjectMessage(
      id = "msg_12345", // Not counted in size calculation
      timestamp = 1699123456789L, // Not counted in size calculation
      clientId = "test-client", // Size: 11 bytes (UTF-8 byte length)
      connectionId = "conn_98765", // Not counted in size calculation
      extras = JsonObject().apply { // Size: JSON serialization byte length
        addProperty("meta", "data") // JSON: {"meta":"data","count":42}
        addProperty("count", 42)
      }, // Total extras size: 26 bytes (verified by gson.toJson().length)
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapCreate,
        objectId = "obj_54321", // Not counted in operation size

        // MapSet contributes to operation size
        mapSet = WireMapSet(
          key = "mapKey", // Size: 6 bytes (UTF-8 byte length)
          value = WireObjectData(
            objectId = "ref_obj", // Not counted in data size
            string = "sample" // Size: 6 bytes (UTF-8 byte length)
          ) // Total ObjectData size: 6 bytes
        ), // Total MapSet size: 6 + 6 = 12 bytes

        // CounterInc contributes to operation size
        counterInc = WireCounterInc(
          number = 10.0 // Size: 8 bytes (number is always 8 bytes)
        ), // Total CounterInc size: 8 bytes

        // mapCreateWithObjectId.derivedFrom contributes to operation size (for client-initiated MAP_CREATE operations)
        mapCreateWithObjectId = WireMapCreateWithObjectId(
          nonce = "dummy-nonce", // Not counted in derivedFrom size
          initialValue = "{}", // Not counted in derivedFrom size
          derivedFrom = WireMapCreate(
            semantics = WireObjectsMapSemantics.LWW, // Not counted in size
            entries = mapOf(
              "entry1" to WireObjectsMapEntry( // Key size: 6 bytes
                tombstone = false, // Not counted in entry size
                timeserial = "ts_123", // Not counted in entry size
                data = WireObjectData(
                  string = "value1" // Size: 6 bytes
                ) // ObjectMapEntry size: 6 bytes
              ), // Total for this entry: 6 (key) + 6 (entry) = 12 bytes
              "entry2" to WireObjectsMapEntry( // Key size: 6 bytes
                data = WireObjectData(
                  number = 42.0 // Size: 8 bytes (number)
                ) // ObjectMapEntry size: 8 bytes
              ) // Total for this entry: 6 (key) + 8 (entry) = 14 bytes
            ) // Total entries size: 12 + 14 = 26 bytes
          ), // Total derivedFrom (MapCreate) size: 26 bytes
        ), // Total mapCreateWithObjectId size (via derivedFrom): 26 bytes

        // counterCreateWithObjectId.derivedFrom contributes to operation size (for client-initiated COUNTER_CREATE operations)
        counterCreateWithObjectId = WireCounterCreateWithObjectId(
          nonce = "dummy-nonce", // Not counted in derivedFrom size
          initialValue = "{}", // Not counted in derivedFrom size
          derivedFrom = WireCounterCreate(
            count = 100.0 // Size: 8 bytes (number is always 8 bytes)
          ), // Total derivedFrom (CounterCreate) size: 8 bytes
        ), // Total counterCreateWithObjectId size (via derivedFrom): 8 bytes

      ), // Total ObjectOperation size: 12 + 8 + 26 + 8 = 54 bytes

      objectState = WireObjectState(
        objectId = "state_obj", // Not counted in state size
        siteTimeserials = mapOf("site1" to "serial1"), // Not counted in state size
        tombstone = false, // Not counted in state size

        // createOp contributes to state size
        createOp = WireObjectOperation(
          action = WireObjectOperationAction.MapSet,
          objectId = "create_obj",
          mapSet = WireMapSet(
            key = "createKey", // Size: 9 bytes
            value = WireObjectData(
              string = "createValue" // Size: 11 bytes
            ) // ObjectData size: 11 bytes
          ) // MapSet size: 9 + 11 = 20 bytes
        ), // Total createOp size: 20 bytes

        // map contributes to state size
        map = WireObjectsMap(
          entries = mapOf(
            "stateKey" to WireObjectsMapEntry( // Key size: 8 bytes
              data = WireObjectData(
                string = "stateValue" // Size: 10 bytes
              ) // ObjectMapEntry size: 10 bytes
            ) // Total: 8 + 10 = 18 bytes
          )
        ), // Total ObjectMap size: 18 bytes

        // counter contributes to state size
        counter = WireObjectsCounter(
          count = 50.0 // Size: 8 bytes
        ) // Total ObjectCounter size: 8 bytes
      ), // Total ObjectState size: 20 + 18 + 8 = 46 bytes

      serial = "serial_123", // Not counted in size calculation
      siteCode = "site_abc" // Not counted in size calculation
    )

    // clientId: 11 bytes + operation: 54 bytes + objectState: 46 bytes + extras: 26 bytes = 137 bytes
    val messageSize = objectMessage.size()
    assertEquals(137, messageSize)

    // Verify the message doesn't exceed the maxMessageSize limit
    mockAdapter.ensureMessageSizeWithinLimit(arrayOf(objectMessage))
  }

  @Test
  fun testObjectMessageSizeForUnicodeCharacters() = runTest {
    val objectMessage = WireObjectMessage(
      operation = WireObjectOperation(
        objectId = "",
        action = WireObjectOperationAction.MapSet,
        mapSet = WireMapSet(
          key = "",
          value = WireObjectData(
            string = "你😊" // 你 -> 3 bytes, 😊 -> 4 bytes
          ),
        ),
      )
    )
    assertEquals(7, objectMessage.size())
  }

  @Test
  fun testObjectMapStateEntryKeyUnicodeSizeIsUtf8() = runTest {
    // OMP4a1: map-state entry keys are measured as their UTF-8 byte length, consistent with the
    // MapCreate/MapSet/MapRemove operation keys. "你😊" -> 7 UTF-8 bytes (not 3 UTF-16 code units);
    // the entry's data string "v" -> 1 byte.
    val objectMessage = WireObjectMessage(
      objectState = WireObjectState(
        objectId = "",
        siteTimeserials = mapOf(),
        tombstone = false,
        map = WireObjectsMap(
          entries = mapOf(
            "你😊" to WireObjectsMapEntry(
              data = WireObjectData(
                string = "v" // Size: 1 byte
              )
            )
          )
        )
      )
    )
    assertEquals(8, objectMessage.size())
  }

  @Test
  fun testObjectMessageSizeAboveLimit() = runTest {
    val mockAdapter = getMockAblyClientAdapter()
    mockAdapter.connectionManager.maxMessageSize = Defaults.maxMessageSize // 64 kb
    assertEquals(65536, mockAdapter.connectionManager.maxMessageSize)

    // Create ObjectMessage with dummy data that results in size 60kb
    val objectMessage1 = WireObjectMessage(
      clientId = CharArray(60 * 1024) { ('a'..'z').random() }.concatToString()
    )
    assertEquals(60 * 1024, objectMessage1.size())

    // Create ObjectMessage with dummy data that results in size 5kb
    val objectMessage2 = WireObjectMessage(
      clientId = CharArray(5 * 1024) { ('a'..'z').random() }.concatToString()
    )
    assertEquals(5 * 1024, objectMessage2.size())

    val exception = assertFailsWith<AblyException> {
      mockAdapter.ensureMessageSizeWithinLimit(arrayOf(objectMessage1, objectMessage2)) // sum size = 65kb exceeds limit
    }
    // Assert on error code and message
    assertEquals(40009, exception.errorInfo.code)
    val expectedMessage = "ObjectMessages size 66560 exceeds maximum allowed size of 65536 bytes"
    assertEquals(expectedMessage, exception.errorInfo.message)
  }
}
