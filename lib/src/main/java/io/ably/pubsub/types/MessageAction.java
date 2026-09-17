package io.ably.pubsub.types;

public enum MessageAction {
    MESSAGE_CREATE, // 0
    MESSAGE_UPDATE, // 1
    MESSAGE_DELETE, // 2
    META, // 3
    MESSAGE_SUMMARY, // 4
    MESSAGE_APPEND; // 5

    static MessageAction tryFindByOrdinal(int ordinal) {
        return values().length <= ordinal ? null: values()[ordinal];
    }
}
