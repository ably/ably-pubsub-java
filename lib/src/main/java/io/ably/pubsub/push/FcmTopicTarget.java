package io.ably.pubsub.push;

/**
 * The devices an FCM topic message is sent to: those subscribed to one topic, or those matched by a
 * condition combining several topics. Created with {@link #topic(String)} or {@link #condition(String)}.
 */
public final class FcmTopicTarget {

    /**
     * The kind of target.
     */
    public enum Type {
        /** A single FCM topic. */
        TOPIC("topic"),
        /** A condition combining up to five FCM topics. */
        CONDITION("condition");

        /** The name of the FCM message field that carries the target. */
        final String field;

        Type(String field) {
            this.field = field;
        }
    }

    /**
     * Whether the target is a topic or a condition.
     */
    public final Type type;

    /**
     * The topic name or the condition expression.
     */
    public final String value;

    private FcmTopicTarget(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Targets every device subscribed to a topic.
     *
     * @param topic The FCM topic name, matching {@code [a-zA-Z0-9-_.~%]{1,900}}.
     * @return The target.
     */
    public static FcmTopicTarget topic(String topic) {
        return new FcmTopicTarget(Type.TOPIC, topic);
    }

    /**
     * Targets the devices matched by a condition.
     * <p>
     * A condition combines up to five topics with {@code &&}, {@code ||} and parentheses, for example
     * {@code 'team-manutd' in topics || 'team-liverpool' in topics}. FCM delivers the message once to
     * each matching device, however many of the topics it is subscribed to. The condition is checked
     * by FCM, which rejects an invalid one.
     *
     * @param condition The FCM topic condition.
     * @return The target.
     */
    public static FcmTopicTarget condition(String condition) {
        return new FcmTopicTarget(Type.CONDITION, condition);
    }

    @Override
    public String toString() {
        return type.field + "=" + value;
    }
}
