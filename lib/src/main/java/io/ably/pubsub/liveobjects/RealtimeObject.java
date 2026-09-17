package io.ably.pubsub.liveobjects;

import io.ably.pubsub.liveobjects.path.types.LiveMapPathObject;
import io.ably.pubsub.liveobjects.state.ObjectStateChange;
import io.ably.pubsub.liveobjects.state.ObjectStateEvent;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * The RealtimeObject interface is the entry point to the strongly-typed, path-based
 * LiveObjects API on a channel. It exposes the root of the objects graph as a
 * {@link LiveMapPathObject} and, via {@link ObjectStateChange}, lets callers observe
 * synchronization state transitions for the channel's objects.
 *
 * <p>Implementations of this interface must be thread-safe as they may be accessed
 * from multiple threads concurrently.
 *
 * <p>Spec: RTO23
 */
public interface RealtimeObject extends ObjectStateChange {

    /**
     * Retrieves a {@link LiveMapPathObject} rooted at the channel's root {@code LiveMap}.
     * The returned object has an empty path and resolves to the root {@code LiveMap}; use
     * its navigation methods to address nested values within the objects graph.
     *
     * <p>When called without a type variable, we return a default root type which is based
     * on the globally defined interface for the Objects feature. A user can provide an
     * explicit type to set the type structure on this particular channel. This is useful
     * when working with multiple channels with different underlying data structures.
     *
     * <p>This operation requires the {@code OBJECT_SUBSCRIBE} channel mode. It implicitly
     * attaches the channel if it is not already attached; the returned future completes once
     * the objects synchronization state has transitioned to {@code SYNCED}, and completes
     * exceptionally with an {@code AblyException} if synchronization fails.
     *
     * <p>Spec: RTO23, RTO23f (typed SDKs return a {@link LiveMapPathObject})
     *
     * @return a future that completes with the root {@link LiveMapPathObject} for this
     *         channel's objects graph.
     */
    @NotNull
    CompletableFuture<LiveMapPathObject> get();

    /**
     * Null-Object guard for {@link RealtimeObject}, used as the value of {@code channel.object}
     * when the LiveObjects plugin is not installed.
     *
     * <p>Because {@code channel.object} is a field, dereferencing it can never throw; instead
     * every method here fails fast with the plugin-missing error, so {@code get()}, {@code on()},
     * {@code off()} and {@code offAll()} surface a clear, consistent error rather than a
     * {@link NullPointerException}.
     *
     * <p>A stateless singleton ({@link #INSTANCE}) shared across all channels that lack the
     * plugin. Adding a method to {@link RealtimeObject} will fail compilation here until it is
     * guarded, which is the intended safety net.
     */
    final class Unavailable implements RealtimeObject {

        public static final Unavailable INSTANCE = new Unavailable();

        private Unavailable() {}

        @Override
        public @NotNull CompletableFuture<LiveMapPathObject> get() {
            throw missing();
        }

        @Override
        public Subscription on(@NotNull ObjectStateEvent event, ObjectStateChange.@NotNull Listener listener) {
            throw missing();
        }

        @Override
        public void off(ObjectStateChange.@NotNull Listener listener) {
            throw missing();
        }

        @Override
        public void offAll() {
            throw missing();
        }

        private static RuntimeException missing() {
            return new IllegalStateException("LiveObjects plugin hasn't been installed", AblyException.fromErrorInfo(
                new ErrorInfo("add runtimeOnly('io.ably:liveobjects:<ably-version>') to your dependency tree", 400, 40019)
            ));
        }
    }
}
