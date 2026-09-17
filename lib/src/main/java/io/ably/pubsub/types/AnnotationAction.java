package io.ably.pubsub.types;

/**
 * Enumerates the possible values of the {@link Annotation#action} field of an {@link Annotation}
 */
public enum AnnotationAction {
    /**
     * (TAN2b) A created annotation
     */
    ANNOTATION_CREATE,
    /**
     * (TAN2b) A deleted annotation
     */
    ANNOTATION_DELETE;

    static AnnotationAction tryFindByOrdinal(int ordinal) {
        return values().length <= ordinal ? null: values()[ordinal];
    }
}
