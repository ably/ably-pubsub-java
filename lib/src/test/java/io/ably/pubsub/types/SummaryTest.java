package io.ably.pubsub.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class SummaryTest {

    @Test
    public void testAsSummaryUniqueV1_SingleEntry() {
        JsonObject jsonObject = new JsonObject();
        JsonObject entryValue = new JsonObject();
        entryValue.addProperty("total", 5);
        JsonArray clientIds = new JsonArray();
        clientIds.add("uniqueClient1");
        clientIds.add("uniqueClient2");
        clientIds.add("uniqueClient3");
        clientIds.add("uniqueClient4");
        clientIds.add("uniqueClient5");
        entryValue.add("clientIds", clientIds);
        jsonObject.add("😄️️️", entryValue);

        Map<String, SummaryClientIdList> result = Summary.asSummaryUniqueV1(jsonObject);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("😄️️️"));

        SummaryClientIdList summary = result.get("😄️️️");
        assertNotNull(summary);
        assertEquals(5, summary.total);
        assertEquals(5, summary.clientIds.size());
        assertTrue(summary.clientIds.contains("uniqueClient1"));
        assertTrue(summary.clientIds.contains("uniqueClient2"));
        assertTrue(summary.clientIds.contains("uniqueClient3"));
        assertTrue(summary.clientIds.contains("uniqueClient4"));
        assertTrue(summary.clientIds.contains("uniqueClient5"));
    }

    @Test
    public void testAsSummaryUniqueV1_InvalidJsonStructure() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("invalidKey", "invalidValue");

        try {
            Summary.asSummaryUniqueV1(jsonObject);
            fail("Should throw IllegalStateException");
        } catch (IllegalStateException exception) {
            assertNotNull(exception);
        }
    }

    @Test
    public void testAsSummaryDistinctV1_EmptyJsonObject() {
        JsonObject jsonObject = new JsonObject();

        Map<String, SummaryClientIdList> result = Summary.asSummaryDistinctV1(jsonObject);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testAsSummaryDistinctV1_SingleEntry() {
        JsonObject jsonObject = new JsonObject();
        JsonObject entryValue = new JsonObject();
        entryValue.addProperty("total", 3);
        JsonArray clientIds = new JsonArray();
        clientIds.add("client1");
        clientIds.add("client2");
        clientIds.add("client3");
        entryValue.add("clientIds", clientIds);
        jsonObject.add("😄️️️", entryValue);

        Map<String, SummaryClientIdList> result = Summary.asSummaryDistinctV1(jsonObject);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("😄️️️"));

        SummaryClientIdList summary = result.get("😄️️️");
        assertNotNull(summary);
        assertEquals(3, summary.total);
        assertEquals(3, summary.clientIds.size());
        assertTrue(summary.clientIds.contains("client1"));
        assertTrue(summary.clientIds.contains("client2"));
        assertTrue(summary.clientIds.contains("client3"));
    }

    @Test
    public void testAsSummaryDistinctV1_InvalidJsonStructure() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("invalidKey", "invalidValue");

        try {
            Summary.asSummaryDistinctV1(jsonObject);
            fail("Should throw ClassCastException");
        } catch (IllegalStateException exception) {
            assertNotNull(exception);
        }
    }

    @Test
    public void testAsSummaryFlagV1_SingleEntry() {
        JsonObject entryValue = new JsonObject();
        entryValue.addProperty("total", 3);
        JsonArray clientIds = new JsonArray();
        clientIds.add("client1");
        clientIds.add("client2");
        clientIds.add("client3");
        entryValue.add("clientIds", clientIds);

        SummaryClientIdList result = Summary.asSummaryFlagV1(entryValue);

        assertNotNull(result);
        assertEquals(3, result.total);
        assertEquals(3, result.clientIds.size());
        assertTrue(result.clientIds.contains("client1"));
        assertTrue(result.clientIds.contains("client2"));
        assertTrue(result.clientIds.contains("client3"));
        assertFalse(result.clipped);
    }

    @Test
    public void testAsSummaryFlagV1_clippedTrue() {
        JsonObject entryValue = new JsonObject();
        entryValue.addProperty("total", 100);
        JsonArray clientIds = new JsonArray();
        clientIds.add("client1");
        entryValue.add("clientIds", clientIds);
        entryValue.addProperty("clipped", true);

        SummaryClientIdList result = Summary.asSummaryFlagV1(entryValue);

        assertNotNull(result);
        assertEquals(100, result.total);
        assertEquals(1, result.clientIds.size());
        assertTrue(result.clientIds.contains("client1"));
        assertTrue(result.clipped);
    }

    @Test
    public void testAsSummaryMultipleV1_EmptyJsonObject() {
        JsonObject jsonObject = new JsonObject();

        Map<String, SummaryClientIdCounts> result = Summary.asSummaryMultipleV1(jsonObject);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testAsSummaryMultipleV1_SingleEntry() {
        JsonObject jsonObject = new JsonObject();
        JsonObject entryValue = new JsonObject();
        entryValue.addProperty("total", 4);
        JsonObject clientIds = new JsonObject();
        clientIds.addProperty("client1", 2);
        clientIds.addProperty("client2", 1);
        clientIds.addProperty("client3", 1);
        entryValue.add("clientIds", clientIds);
        jsonObject.add("😄️️️", entryValue);

        Map<String, SummaryClientIdCounts> result = Summary.asSummaryMultipleV1(jsonObject);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("😄️️️"));

        SummaryClientIdCounts summary = result.get("😄️️️");
        assertNotNull(summary);
        assertEquals(4, summary.total);
        assertEquals(3, summary.clientIds.size());
        assertEquals(2, summary.clientIds.get("client1").intValue());
        assertEquals(1, summary.clientIds.get("client2").intValue());
        assertEquals(1, summary.clientIds.get("client3").intValue());
    }

    @Test
    public void testAsSummaryMultipleV1_MultipleEntries() {
        JsonObject jsonObject = new JsonObject();

        JsonObject entryValue1 = new JsonObject();
        entryValue1.addProperty("total", 5);
        JsonObject clientIds1 = new JsonObject();
        clientIds1.addProperty("clientA", 3);
        clientIds1.addProperty("clientB", 2);
        entryValue1.add("clientIds", clientIds1);
        jsonObject.add("😄️️️", entryValue1);

        JsonObject entryValue2 = new JsonObject();
        entryValue2.addProperty("total", 2);
        JsonObject clientIds2 = new JsonObject();
        clientIds2.addProperty("clientX", 1);
        clientIds2.addProperty("clientY", 1);
        entryValue2.add("clientIds", clientIds2);
        jsonObject.add("👍️️️️️️", entryValue2);

        Map<String, SummaryClientIdCounts> result = Summary.asSummaryMultipleV1(jsonObject);

        assertNotNull(result);
        assertEquals(2, result.size());

        SummaryClientIdCounts summaryA = result.get("😄️️️");
        assertNotNull(summaryA);
        assertEquals(5, summaryA.total);
        assertEquals(2, summaryA.clientIds.size());
        assertEquals(3, (int) summaryA.clientIds.get("clientA"));
        assertEquals(2, (int) summaryA.clientIds.get("clientB"));
        assertEquals(0, summaryA.totalUnidentified);
        assertEquals(5, summaryA.totalClientIds);
        assertFalse(summaryA.clipped);

        SummaryClientIdCounts summaryB = result.get("👍️️️️️️");
        assertNotNull(summaryB);
        assertEquals(2, summaryB.total);
        assertEquals(2, summaryB.clientIds.size());
        assertEquals(1, (int) summaryB.clientIds.get("clientX"));
        assertEquals(1, (int) summaryB.clientIds.get("clientY"));
        assertEquals(0, summaryB.totalUnidentified);
        assertEquals(2, summaryB.totalClientIds);
        assertFalse(summaryA.clipped);
    }

    @Test
    public void testAsSummaryMultipleV1_ClippedTrue() {
        JsonObject jsonObject = new JsonObject();

        JsonObject entryValue1 = new JsonObject();
        entryValue1.addProperty("total", 5);
        JsonObject clientIds1 = new JsonObject();
        clientIds1.addProperty("clientA", 1);
        entryValue1.add("clientIds", clientIds1);
        entryValue1.addProperty("clipped", true);
        entryValue1.addProperty("totalClientIds", 1);
        jsonObject.add("😄️️️", entryValue1);

        Map<String, SummaryClientIdCounts> result = Summary.asSummaryMultipleV1(jsonObject);
        SummaryClientIdCounts summary = result.get("😄️️️");
        assertEquals(5, summary.total);
        assertEquals(1, summary.totalClientIds);
        assertEquals(0, summary.totalUnidentified);
        assertTrue(summary.clipped);
    }

    @Test
    public void testAsSummaryMultipleV1_InvalidJsonStructure() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("invalidKey", "invalidValue");

        try {
            Summary.asSummaryMultipleV1(jsonObject);
            fail("Should throw IllegalStateException");
        } catch (IllegalStateException exception) {
            assertNotNull(exception);
        }
    }

    @Test
    public void testAsSummaryTotalV1_ValidJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("total", 10);

        SummaryTotal result = Summary.asSummaryTotalV1(jsonObject);

        assertNotNull(result);
        assertEquals(10, result.total);
    }

    @Test
    public void testAsSummaryTotalV1_EmptyJsonObject() {
        JsonObject jsonObject = new JsonObject();

        try {
            Summary.asSummaryTotalV1(jsonObject);
            fail("Should throw NullPointerException");
        } catch (NullPointerException exception) {
            assertNotNull(exception);
        }
    }

    @Test
    public void testAsSummaryTotalV1_InvalidJsonStructure() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("invalidKey", "invalidValue");

        try {
            Summary.asSummaryTotalV1(jsonObject);
            fail("Should throw IllegalStateException");
        } catch (Exception exception) {
            assertNotNull(exception);
        }
    }
}
