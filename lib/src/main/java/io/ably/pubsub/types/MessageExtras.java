package io.ably.pubsub.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.pubsub.util.Serialisation;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ImmutableMapValue;
import org.msgpack.value.ImmutableValue;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

public final class MessageExtras {
    private static final String TAG = MessageExtras.class.getName();

    private static final String DELTA = "delta";

    private final DeltaExtras delta; // may be null
    private final JsonObject jsonObject; // never null

    /**
     * Creates a MessageExtras instance to be sent as extra with a Message to Ably's servers.
     *
     * @see <a href="https://www.ably.com/docs/general/push/publish#channel-broadcast-example">Channel-based push notification example</a>
     *
     * @since 1.2.1
     */
    public MessageExtras(final JsonObject jsonObject) {
        this(jsonObject, null);
    }

    private MessageExtras(final JsonObject jsonObject, final DeltaExtras delta) {
        if (null == jsonObject) {
            throw new NullPointerException("jsonObject cannot be null.");
        }

        this.jsonObject = jsonObject;
        this.delta = delta;
    }

    public DeltaExtras getDelta() {
        return delta;
    }

    public JsonObject asJsonObject() {
        return jsonObject;
    }

    /* package private */ void write(MessagePacker packer) {
            Serialisation.gsonToMsgpack(jsonObject, packer);
    }

    /* package private */ static MessageExtras read(MessageUnpacker unpacker) throws IOException {
        DeltaExtras delta = null;

        final ImmutableValue value = unpacker.unpackValue();
        if (value instanceof ImmutableMapValue) {
            final Map<Value, Value> map = ((ImmutableMapValue) value).map();
            final Value deltaValue = map.get(ValueFactory.newString(DELTA));
            if (null != deltaValue) {
                if (!(deltaValue instanceof ImmutableMapValue)) {
                    // There's a delta key but the value at that key is not a map.
                    throw new IOException("The delta extras unpacked to the wrong type \"" + deltaValue.getClass() + "\" when expected a map.");
                }
                final Map<Value, Value> deltaMap = ((ImmutableMapValue)deltaValue).map();
                delta = DeltaExtras.read(deltaMap);
            }
        }

        final JsonElement element = Serialisation.msgpackToGson(value);
        if (!(element instanceof JsonObject)) {
            // The root thing that we unpacked was not a map.
            throw new IOException("The extras unpacked to the wrong type \"" + element.getClass() + "\" when expected a JsonObject.");
        }
        final JsonObject raw = (JsonObject)element;

        return new MessageExtras(raw, delta);
    }

    /* package private */ static MessageExtras read(final JsonObject raw) throws MessageDecodeException {
        DeltaExtras delta = null;

        final JsonElement deltaElement = raw.get(DELTA);
        if (deltaElement instanceof JsonObject) {
            delta = DeltaExtras.read((JsonObject)deltaElement);
        } else {
            if (null != deltaElement) {
                throw MessageDecodeException.fromDescription("The value under the delta key is of the wrong type \"" + deltaElement.getClass() + "\" when expected a map.");
            }
        }

        return new MessageExtras(raw, delta);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageExtras that = (MessageExtras) o;
        return Objects.equals(jsonObject, that.jsonObject);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(jsonObject);
    }

    @Override
    public String toString() {
        return "MessageExtras{" +
                DELTA + "=" + delta +
                ", raw=" + jsonObject +
                '}';
    }

    public static class Serializer implements JsonSerializer<MessageExtras> {
        @Override
        public JsonElement serialize(final MessageExtras src, final Type typeOfSrc, final JsonSerializationContext context) {
            return src.jsonObject;
        }
    }
}
