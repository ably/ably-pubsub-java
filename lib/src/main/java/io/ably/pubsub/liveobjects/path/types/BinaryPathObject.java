package io.ably.pubsub.liveobjects.path.types;

import io.ably.pubsub.liveobjects.path.PathObject;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link PathObject} whose underlying value is expected to be a binary blob
 * (a {@code byte[]}).
 *
 * <p>This is a terminal type. {@link PathObject#instance()} returns an {@code Instance}
 * wrapping the resolved primitive value per RTPO8f; navigation
 * via {@code at(...)} is not available here because it is only defined on
 * {@code LiveMapPathObject}. Only {@link #value()} and the inherited read APIs are
 * useful here.
 *
 * <p>Spec: RTTS6c
 */
public interface BinaryPathObject extends PathObject {

    /**
     * Returns the binary value at this path, or {@code null} when the path does not
     * resolve or resolves to a non-binary value.
     *
     * <p>Spec: RTPO7 / RTTS6c
     *
     * @return the resolved bytes, or {@code null}
     */
    byte @Nullable [] value();
}
