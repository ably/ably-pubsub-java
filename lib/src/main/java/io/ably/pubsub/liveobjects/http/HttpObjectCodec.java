package io.ably.pubsub.liveobjects.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.liveobjects.message.ObjectsMapSemantics;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.util.Base64Coder;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.IntegerValue;
import org.msgpack.value.Value;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire encoding and decoding for the objects REST endpoint.
 *
 * <p>Request and response bodies are handled as a format-neutral tree of plain Java values
 * ({@code Map<String, Object>}, {@code List<Object>}, {@code String}, {@code Long} (or
 * {@code BigInteger} beyond the long range), {@code Double}, {@code Boolean}, {@code byte[]},
 * {@code null}), the same for both protocols, which is what {@code getCompact} returns to the
 * caller verbatim (RSO2f). The tree is converted to and from JSON or MessagePack at the edges (RSC8).
 */
final class HttpObjectCodec {

    private static final String MSGPACK_CONTENT_TYPE = "application/x-msgpack";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final List<String> LEAF_VALUE_FIELDS = Arrays.asList("string", "number", "boolean", "bytes", "json");

    private HttpObjectCodec() {}

    //region Operations -> wire tree (RSO3d, RSO4e)

    /**
     * Encodes an operation for the wire (RSO3d). {@code id}, {@code objectId} and {@code path} are
     * copied as given (RSO3d3); there is no {@code action} field, the server infers it from the body
     * field (RSO3d4).
     */
    static Map<String, Object> encodeOperation(HttpObjectOperation op, boolean binary) throws AblyException {
        Map<String, Object> wire = new LinkedHashMap<>();
        putIfNotNull(wire, "id", op.getId());
        putIfNotNull(wire, "objectId", op.getObjectId());
        putIfNotNull(wire, "path", op.getPath());
        if (op.getMapCreate() != null) {
            wire.put("mapCreate", encodeMapCreate(op.getMapCreate(), binary));
        }
        if (op.getMapCreateWithObjectId() != null) {
            wire.put("mapCreateWithObjectId", encodeCreateWithObjectId(op.getMapCreateWithObjectId()));
        }
        if (op.getMapSet() != null) {
            Map<String, Object> mapSet = new LinkedHashMap<>();
            mapSet.put("key", op.getMapSet().getKey());
            mapSet.put("value", encodeData(op.getMapSet().getValue(), binary));
            wire.put("mapSet", mapSet);
        }
        if (op.getMapRemove() != null) {
            wire.put("mapRemove", singleton("key", op.getMapRemove().getKey()));
        }
        if (op.getCounterCreate() != null) {
            wire.put("counterCreate", encodeCounterCreate(op.getCounterCreate()));
        }
        if (op.getCounterCreateWithObjectId() != null) {
            wire.put("counterCreateWithObjectId", encodeCreateWithObjectId(op.getCounterCreateWithObjectId()));
        }
        if (op.getCounterInc() != null) {
            wire.put("counterInc", singleton("number", finite(op.getCounterInc().getNumber(), "counterInc.number")));
        }
        return wire;
    }

    /** RSO3d1/RSO4e1a semantics per OMP2, RSO3d2/RSO4e1b entry data per OD4. */
    static Map<String, Object> encodeMapCreate(HttpObjectOperation.MapCreate body, boolean binary) throws AblyException {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (Map.Entry<String, PublishObjectData> entry : body.getEntries().entrySet()) {
            entries.put(entry.getKey(), singleton("data", encodeData(entry.getValue(), binary)));
        }
        Map<String, Object> mapCreate = new LinkedHashMap<>();
        mapCreate.put("semantics", encodeSemantics(body.getSemantics()));
        mapCreate.put("entries", entries);
        return mapCreate;
    }

    static Map<String, Object> encodeCounterCreate(HttpObjectOperation.CounterCreate body) throws AblyException {
        return singleton("count", finite(body.getCount(), "counterCreate.count"));
    }

    private static Map<String, Object> encodeCreateWithObjectId(HttpObjectOperation.CreateWithObjectId body) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("initialValue", body.getInitialValue());
        wire.put("nonce", body.getNonce());
        return wire;
    }

    /** OMP2: semantics travel as their integer code. */
    private static int encodeSemantics(ObjectsMapSemantics semantics) throws AblyException {
        if (semantics == ObjectsMapSemantics.LWW) {
            return 0;
        }
        throw invalidInput("Unrecognized map semantics: " + semantics);
    }

    /**
     * Encodes a leaf value per OD4 (RSO3d2): with MessagePack, binary stays binary (OD4c); with JSON,
     * it is Base64-encoded (OD4d2). A JSON object or array is sent as a JSON-encoded string in the
     * {@code json} field with both protocols (OD4c5, OD4d5).
     */
    private static Map<String, Object> encodeData(PublishObjectData data, boolean binary) throws AblyException {
        if (data == null) throw invalidInput("A map value must not be null");
        if (data.getString() != null) return singleton("string", data.getString());
        if (data.getNumber() != null) return singleton("number", finite(data.getNumber(), "number"));
        if (data.getBoolean() != null) return singleton("boolean", data.getBoolean());
        if (data.getBytes() != null) {
            byte[] bytes = data.getBytes();
            return singleton("bytes", binary ? bytes : Base64Coder.encodeToString(bytes));
        }
        if (data.getJson() != null) return singleton("json", data.getJson().toString());
        if (data.getObjectId() != null) return singleton("objectId", data.getObjectId());
        throw invalidInput("PublishObjectData has no value set"); // unreachable via the public factories
    }

    //endregion

    //region Full-format response decoding (RSO2g)

    /**
     * Decodes a node of a {@code get} response (RSO2g). A node with {@code map} is a map
     * whose entries are decoded recursively (RSO2g1), one with {@code counter} a counter (RSO2g2). A
     * node that carries only an {@code objectId} and fields this library doesn't know is an object
     * of an unrecognised type, passed through (RSO2g4, RSO9d). Anything else is a leaf (RSO2g3).
     */
    static HttpObjectGetFullResult decodeFullNode(Object node) throws AblyException {
        try {
            return decodeNode(node);
        } catch (RuntimeException e) {
            // e.g. a malformed JSON string or Base64 value in a leaf
            throw decodeError("Failed to decode full-format response: " + e.getMessage(), e);
        }
    }

    private static HttpObjectGetFullResult decodeNode(Object node) throws AblyException {
        if (!(node instanceof Map)) {
            throw decodeError("Unexpected full-format node: " + node, null);
        }
        Map<?, ?> fields = (Map<?, ?>) node;
        String objectId = asString(fields.get("objectId"));
        Object map = fields.get("map");
        Object counter = fields.get("counter");
        if (map instanceof Map) {
            return decodeMap(objectId, (Map<?, ?>) map, fields);
        }
        if (counter instanceof Map) {
            return decodeCounter(objectId, (Map<?, ?>) counter, fields);
        }
        if (objectId != null && !hasLeafValue(fields)) {
            return new HttpObjectResults.LiveObject(objectId, unrecognized(fields, "objectId"));
        }
        return decodeLeaf(fields);
    }

    private static HttpLiveMap decodeMap(String objectId, Map<?, ?> map, Map<?, ?> fields) throws AblyException {
        Map<String, HttpLiveMapEntry> entries = new LinkedHashMap<>();
        Object rawEntries = map.get("entries");
        if (rawEntries instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawEntries).entrySet()) {
                if (!(entry.getValue() instanceof Map)) continue;
                Object data = ((Map<?, ?>) entry.getValue()).get("data");
                if (data == null) continue;
                entries.put(String.valueOf(entry.getKey()), new HttpObjectResults.LiveMapEntry(decodeNode(data)));
            }
        }
        return new HttpObjectResults.LiveMap(
            objectId != null ? objectId : "",
            decodeSemantics(map.get("semantics")),
            entries,
            unrecognized(fields, "objectId", "map"));
    }

    /** A counter whose value isn't a number at {@code counter.data.number} has a null count, not 0. */
    private static HttpLiveCounter decodeCounter(String objectId, Map<?, ?> counter, Map<?, ?> fields) {
        Double count = null;
        Object data = counter.get("data");
        if (data instanceof Map && ((Map<?, ?>) data).get("number") instanceof Number) {
            count = ((Number) ((Map<?, ?>) data).get("number")).doubleValue();
        }
        return new HttpObjectResults.LiveCounter(
            objectId != null ? objectId : "", count, unrecognized(fields, "objectId", "counter"));
    }

    /**
     * RSO14b: the REST API returns semantics as a name ({@code "LWW"}), the realtime protocol as an
     * OMP2 code ({@code 0}); accept both. Anything else is preserved as UNKNOWN.
     */
    private static ObjectsMapSemantics decodeSemantics(Object value) {
        if (value instanceof Number && ((Number) value).longValue() == 0L) return ObjectsMapSemantics.LWW;
        if (value instanceof String && "lww".equalsIgnoreCase((String) value)) return ObjectsMapSemantics.LWW;
        return ObjectsMapSemantics.UNKNOWN;
    }

    /**
     * Decodes a leaf per OD5 (RSO2g3): Base64 {@code bytes} become binary (OD5b2), and the
     * JSON-encoded string in {@code json} is parsed (OD5a2, OD5b3). The result carries no
     * {@code objectId} or {@code encoding} (RSO9c1).
     */
    private static HttpObjectData decodeLeaf(Map<?, ?> fields) {
        final String string = asString(fields.get("string"));
        JsonElement json = null;
        Object rawJson = fields.get("json");
        if (rawJson instanceof String) {
            json = JsonParser.parseString((String) rawJson);
        } else if (rawJson != null) {
            json = toJson(rawJson);
        }
        byte[] bytes = null;
        Object rawBytes = fields.get("bytes");
        if (rawBytes instanceof byte[]) {
            bytes = (byte[]) rawBytes;
        } else if (rawBytes instanceof String) {
            bytes = Base64Coder.decode((String) rawBytes);
        }
        Object number = fields.get("number");
        Object bool = fields.get("boolean");
        return new HttpObjectResults.ObjectDataLeaf(
            string,
            number instanceof Number ? ((Number) number).doubleValue() : null,
            bool instanceof Boolean ? (Boolean) bool : null,
            bytes,
            json);
    }

    private static boolean hasLeafValue(Map<?, ?> fields) {
        for (String field : LEAF_VALUE_FIELDS) {
            if (fields.get(field) != null) return true;
        }
        return false;
    }

    /** The fields not in {@code known}, as JSON; binary values become Base64 strings. */
    private static JsonObject unrecognized(Map<?, ?> fields, String... known) {
        List<String> knownFields = Arrays.asList(known);
        JsonObject result = new JsonObject();
        for (Map.Entry<?, ?> entry : fields.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!knownFields.contains(key)) result.add(key, toJson(entry.getValue()));
        }
        return result;
    }

    //endregion

    //region Publish result (RSO10)

    static HttpObjectPublishResult decodePublishResult(Object body) throws AblyException {
        if (!(body instanceof Map)) {
            throw decodeError("Unexpected publish response: " + body, null);
        }
        Map<?, ?> fields = (Map<?, ?>) body;
        List<String> objectIds = new ArrayList<>();
        Object rawIds = fields.get("objectIds");
        if (rawIds instanceof List) {
            for (Object id : (List<?>) rawIds) {
                if (id instanceof String) objectIds.add((String) id);
            }
        }
        String messageId = asString(fields.get("messageId"));
        String channel = asString(fields.get("channel"));
        return new HttpObjectResults.PublishResult(
            messageId != null ? messageId : "", channel != null ? channel : "", objectIds);
    }

    //endregion

    //region Format conversion (RSC8)

    static HttpCore.RequestBody requestBody(Object tree, boolean binary) throws AblyException {
        if (binary) {
            return new HttpUtils.ByteArrayRequestBody(toMsgpack(tree), MSGPACK_CONTENT_TYPE);
        }
        return new HttpUtils.JsonRequestBody(toJson(tree).toString());
    }

    /** Decodes a response body per its content type into plain values. */
    static Object decodeResponseBody(HttpCore.Response response) throws IOException {
        byte[] body = response.body;
        if (body == null || body.length == 0) return null;
        if (response.contentType != null && response.contentType.toLowerCase().contains("msgpack")) {
            try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(body)) {
                return fromMsgpack(unpacker.unpackValue());
            }
        }
        return fromJson(JsonParser.parseString(new String(body, UTF_8)));
    }

    /** Converts a plain-value tree to JSON. Binary values become Base64 strings. */
    static JsonElement toJson(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonElement) return (JsonElement) value;
        if (value instanceof String) return new JsonPrimitive((String) value);
        if (value instanceof Number) return new JsonPrimitive((Number) value);
        if (value instanceof Boolean) return new JsonPrimitive((Boolean) value);
        if (value instanceof byte[]) return new JsonPrimitive(Base64Coder.encodeToString((byte[]) value));
        if (value instanceof Map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.add(String.valueOf(entry.getKey()), toJson(entry.getValue()));
            }
            return object;
        }
        if (value instanceof List) {
            JsonArray array = new JsonArray();
            for (Object item : (List<?>) value) array.add(toJson(item));
            return array;
        }
        return new JsonPrimitive(value.toString());
    }

    private static Object fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), fromJson(entry.getValue()));
            }
            return map;
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) list.add(fromJson(item));
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) {
            String text = primitive.getAsString();
            if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
                return Double.parseDouble(text);
            }
            BigInteger integer = new BigInteger(text);
            return integer.bitLength() < 64 ? (Object) integer.longValue() : integer;
        }
        return primitive.getAsString();
    }

    private static byte[] toMsgpack(Object tree) throws AblyException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            pack(packer, tree);
            return packer.toByteArray();
        } catch (IOException e) {
            throw AblyException.fromThrowable(e);
        }
    }

    private static void pack(MessagePacker packer, Object value) throws IOException {
        if (value == null) {
            packer.packNil();
        } else if (value instanceof String) {
            packer.packString((String) value);
        } else if (value instanceof Boolean) {
            packer.packBoolean((Boolean) value);
        } else if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
            packer.packDouble(((Number) value).doubleValue());
        } else if (value instanceof BigInteger) {
            packer.packBigInteger((BigInteger) value);
        } else if (value instanceof Number) {
            packer.packLong(((Number) value).longValue());
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            packer.packBinaryHeader(bytes.length);
            packer.writePayload(bytes);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            packer.packMapHeader(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                packer.packString(String.valueOf(entry.getKey()));
                pack(packer, entry.getValue());
            }
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            packer.packArrayHeader(list.size());
            for (Object item : list) pack(packer, item);
        } else if (value instanceof JsonElement) {
            pack(packer, fromJson((JsonElement) value));
        } else {
            packer.packString(value.toString());
        }
    }

    private static Object fromMsgpack(Value value) {
        switch (value.getValueType()) {
            case BOOLEAN:
                return value.asBooleanValue().getBoolean();
            case INTEGER:
                IntegerValue integer = value.asIntegerValue();
                return integer.isInLongRange() ? (Object) integer.toLong() : integer.toBigInteger();
            case FLOAT:
                return value.asFloatValue().toDouble();
            case STRING:
                return value.asStringValue().asString();
            case BINARY:
                return value.asBinaryValue().asByteArray();
            case ARRAY: {
                List<Object> list = new ArrayList<>();
                for (Value item : value.asArrayValue()) list.add(fromMsgpack(item));
                return list;
            }
            case MAP: {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<Value, Value> entry : value.asMapValue().map().entrySet()) {
                    Value key = entry.getKey();
                    map.put(key.isStringValue() ? key.asStringValue().asString() : key.toString(), fromMsgpack(entry.getValue()));
                }
                return map;
            }
            case EXTENSION:
                return value.asExtensionValue().getData();
            case NIL:
            default:
                return null;
        }
    }

    //endregion

    //region Helpers

    static AblyException invalidInput(String message) {
        return AblyException.fromErrorInfo(new ErrorInfo(message, 400, 40003));
    }

    /**
     * A response that could not be decoded: 500 / 50000, built so that it is a plain
     * {@link AblyException} rather than the {@code HostFailedException} that
     * {@link AblyException#fromErrorInfo} makes of a 5xx status, which would mean "retry on a
     * fallback host".
     */
    static AblyException decodeError(String message, Throwable cause) {
        AblyException exception = AblyException.fromThrowable(cause != null ? cause : new IllegalStateException(message));
        exception.errorInfo.message = message;
        return exception;
    }

    private static Number finite(Number number, String field) throws AblyException {
        if (number == null) throw invalidInput("Invalid " + field + ": must not be null");
        double value = number.doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw invalidInput("Invalid " + field + ": " + number + " is not a finite number");
        }
        return number;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }

    //endregion
}
