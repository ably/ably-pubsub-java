package io.ably.pubsub.liveobjects.serialization;

import com.google.gson.JsonArray;
import io.ably.pubsub.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Serializer interface for converting between objects and their MessagePack or JSON representations.
 */
public interface ObjectSerializer {

    /**
     * Reads a MessagePack array from the given unpacker and deserializes it into an Object array.
     *
     * @param unpacker the MessageUnpacker to read from
     * @return the deserialized Object array
     * @throws IOException if an I/O error occurs during unpacking
     */
    @NotNull
    Object[] readMsgpackArray(@NotNull MessageUnpacker unpacker) throws IOException;

    /**
     * Serializes the given Object array as a MessagePack array using the provided packer.
     *
     * @param objects the Object array to serialize
     * @param packer the MessagePacker to write to
     * @throws IOException if an I/O error occurs during packing
     */
    void writeMsgpackArray(@NotNull Object[] objects, @NotNull MessagePacker packer) throws IOException;

    /**
     * Reads a JSON array from the given {@link JsonArray} and deserializes it into an Object array.
     *
     * @param json the {@link JsonArray} representing the array to deserialize
     * @return the deserialized Object array
     */
    @NotNull
    Object[] readFromJsonArray(@NotNull JsonArray json);

    /**
     * Serializes the given Object array as a JSON array.
     *
     * @param objects the Object array to serialize
     * @return the resulting JsonArray
     */
    @NotNull
    JsonArray asJsonArray(@NotNull Object[] objects);

    /**
     * Returns the lazily-initialized, process-wide {@link ObjectSerializer} singleton, reflectively
     * loaded from the LiveObjects plugin on the classpath. Returns {@code null} if the plugin is not
     * present; the lookup is retried on subsequent calls until it succeeds.
     *
     * @return the shared {@link ObjectSerializer} instance, or {@code null} if the plugin is unavailable.
     */
    @Nullable
    static ObjectSerializer tryGet() {
        return Holder.getSerializer();
    }

    /**
     * Holds the lazily-initialized {@link ObjectSerializer} singleton. Interfaces cannot declare
     * mutable static fields, so the cache lives here while {@link #tryGet()} delegates to it.
     */
    final class Holder {
        private static final String TAG = ObjectSerializer.Holder.class.getName();
        private static final String IMPLEMENTATION_CLASS = "io.ably.pubsub.liveobjects.serialization.DefaultObjectsSerializer";
        private static volatile ObjectSerializer objectsSerializer;

        private Holder() {}

        @Nullable
        static ObjectSerializer getSerializer() {
            if (objectsSerializer == null) {
                synchronized (Holder.class) {
                    if (objectsSerializer == null) { // Double-Checked Locking (DCL)
                        try {
                            Class<?> serializerClass = Class.forName(IMPLEMENTATION_CLASS);
                            objectsSerializer = (ObjectSerializer) serializerClass.getDeclaredConstructor().newInstance();
                        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                                 NoSuchMethodException |
                                 InvocationTargetException e) {
                            Log.w(TAG, "Failed to init ObjectSerializer, LiveObjects plugin not included in the classpath", e);
                            return null;
                        }
                    }
                }
            }
            return objectsSerializer;
        }
    }
}
