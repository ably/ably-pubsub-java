package io.ably.pubsub.types;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Contains application statistics for a specified time interval and time period.
 */
public class Stats {

    /**
     * Contains a breakdown of summary stats data for different (TLS vs non-TLS) connection types.
     */
    public static class ConnectionTypes {
        /**
         * A {@link Stats.ResourceCount} object containing a breakdown of usage by scope over TLS connections (both TLS and non-TLS).
         * <p>
         * Spec: TS4c
         */
        public ResourceCount all;
        /**
         * A {@link Stats.ResourceCount} object containing a breakdown of usage by scope over non-TLS connections.
         * <p>
         * Spec: TS4b
         */
        public ResourceCount plain;
        /**
         * A {@link Stats.ResourceCount} object containing a breakdown of usage by scope over TLS connections.
         * <p>
         * Spec: TS4a
         */
        public ResourceCount tls;
    }

    /**
     * Contains the aggregate counts for messages and data transferred.
     */
    public static class MessageCount {
        /**
         * The count of all messages.
         * <p>
         * Spec: TS5a
         */
        public double count;
        /**
         * The total number of bytes transferred for all messages.
         * <p>
         * Spec: TS5b
         */
        public double data;

        public double uncompressedData;
    }

    public static class MessageCategory extends MessageCount {
        public Map<String, MessageCount> category;
    }

    /**
     * Contains a breakdown of summary stats data for different (channel vs presence) message types.
     */
    public static class MessageTypes {
        /**
         * A {@link Stats.MessageCount} object containing the count and byte value of messages and presence messages.
         * <p>
         * Spec: TS6c
         */
        public MessageCategory all;
        /**
         * A {@link Stats.MessageCount} object containing the count and byte value of messages.
         * <p>
         * Spec: TS6a
         */
        public MessageCategory messages;
        /**
         * A {@link Stats.MessageCount} object containing the count and byte value of presence messages.
         * <p>
         * Spec: TS6b
         */
        public MessageCategory presence;
    }

    /**
     * Contains a breakdown of summary stats data for traffic over various transport types.
     */
    public static class MessageTraffic {
        /**
         * A {@link Stats.MessageTypes} object containing a breakdown of usage by message type
         * for all messages (includes realtime, rest and webhook messages).
         * <p>
         * Spec: TS7d
         */
        public MessageTypes all;
        /**
         * A {@link Stats.MessageTypes} object containing a breakdown of usage by message type
         * for messages transferred over a realtime transport such as WebSocket.
         * <p>
         * Spec: TS7a
         */
        public MessageTypes realtime;
        /**
         * A {@link Stats.MessageTypes} object containing a breakdown of usage by message type
         * for messages transferred over a rest transport such as WebSocket.
         * <p>
         * Spec: TS7b
         */
        public MessageTypes rest;
        /**
         * A {@link Stats.MessageTypes} object containing a breakdown of usage by message type
         * for messages delivered using webhooks.
         * <p>
         * Spec: TS7c
         */
        public MessageTypes webhook;
    }

    /**
     * Contains the aggregate counts for requests made.
     */
    public static class RequestCount {
        /**
         * The number of requests that succeeded.
         * <p>
         * Spec: TS8a
         */
        public double succeeded;
        /**
         * The number of requests that failed.
         * <p>
         * Spec: TS8b
         */
        public double failed;
        /**
         * The number of requests that were refused, typically as a result of permissions or a limit being exceeded.
         * <p>
         * Spec: TS8c
         */
        public double refused;
    }

    /**
     * Contains the aggregate data for usage of a resource in a specific scope.
     */
    public static class ResourceCount {
        /**
         * The total number of resources opened of this type.
         * <p>
         * Spec: TS9a
         */
        public double opened;
        /**
         * The peak number of resources of this type used for this period.
         * <p>
         * Spec: TS9b
         */
        public double peak;
        /**
         * The average number of resources of this type used for this period.
         * <p>
         * Spec: TS9c
         */
        public double mean;
        /**
         * The minimum total resources of this type used for this period.
         * <p>
         * Spec: TS9d
         */
        public double min;
        /**
         * The number of resource requests refused within this period.
         * <p>
         * Spec: TS9e
         */
        public double refused;
    }

    public static class ProcessedCount {
        public double succeeded;
        public double skipped;
        public double failed;
    }

    public static class ProcessedMessages {
        public Map<String, ProcessedCount> delta;
    }

    /**
     * Details the stats on push notifications.
     */
    public static class PushedMessages {
        /**
         * Total number of push messages.
         * <p>
         * Spec: TS10a
         */
        public int messages;
        /**
         * The count of push notifications.
         * <p>
         * Spec: TS10c
         */
        public Map<String, Integer> notifications;
        /**
         * Total number of direct publishes.
         * <p>
         * Spec: TS10b
         */
        public int directPublishes;
    }

    /**
     * Describes the interval unit over which statistics are gathered.
     */
    public enum Granularity {
        /**
         * Interval unit over which statistics are gathered as minutes.
         */
        minute,
        /**
         * Interval unit over which statistics are gathered as hours.
         */
        hour,
        /**
         * Interval unit over which statistics are gathered as days.
         */
        day,
        /**
         * Interval unit over which statistics are gathered as months.
         */
        month
    }

    private static String[] intervalFormatString = new String[] {
        "yyyy-MM-dd:hh:mm",
        "yyyy-MM-dd:hh",
        "yyyy-MM-dd",
        "yyyy-MM"
    };

    public static String toIntervalId(long timestamp, Granularity granularity) {
        String formatString = intervalFormatString[granularity.ordinal()];
        return new SimpleDateFormat(formatString).format(new Date(timestamp));
    }

    public static long fromIntervalId(String intervalId) {
        try {
            String formatString = intervalFormatString[0].substring(0, intervalId.length());
            return new SimpleDateFormat(formatString).parse(intervalId).getTime();
        } catch (ParseException e) { return 0; }
    }

    /**
     * The UTC time at which the time period covered begins.
     * If unit is set to minute this will be in the format YYYY-mm-dd:HH:MM, if hour it will be YYYY-mm-dd:HH,
     * if day it will be YYYY-mm-dd:00 and if month it will be YYYY-mm-01:00.
     * <p>
     * Spec: TS12a
     */
    public String intervalId;
    /**
     * The length of the interval the stats span. Values will be a {@link Granularity}.
     * <p>
     * Spec: TS12c
     */
    public String unit;

    public int count;
    public String inProgress;

    /**
     * A {@link Stats.MessageTypes} object containing the aggregate count of all message stats.
     * <p>
     * Spec: TS12e
     */
    public MessageTypes all;
    /**
     * A {@link Stats.MessageTypes} object containing the aggregate count of inbound message stats.
     * <p>
     * Spec: TS12f
     */
    public MessageTraffic inbound;
    /**
     * A {@link Stats.MessageTypes} object containing the aggregate count of outbound message stats.
     * <p>
     * Spec: TS12g
     */
    public MessageTraffic outbound;
    /**
     * A {@link Stats.MessageTypes} object containing the aggregate count of persisted message stats.
     * <p>
     * Spec: TS12h
     */
    public MessageTypes persisted;
    /**
     * A {@link Stats.ConnectionTypes} object containing a breakdown of connection related stats, such as min, mean and peak connections.
     * <p>
     * Spec: TS12i
     */
    public ConnectionTypes connections;
    /**
     * A {@link Stats.ResourceCount} object containing a breakdown of channels.
     * <p>
     * Spec: TS12j
     */
    public ResourceCount channels;
    /**
     * A {@link Stats.RequestCount} object containing a breakdown of API Requests.
     * <p>
     * Spec: TS12k
     */
    public RequestCount apiRequests;
    /**
     * A {@link Stats.RequestCount} object containing a breakdown of Ably Token requests.
     * <p>
     * Spec: TS12l
     */
    public RequestCount tokenRequests;

    public ProcessedMessages processed;

    /**
     * A {@link Stats.PushedMessages} object containing a breakdown of stats on push notifications.
     * <p>
     * Spec: TS12m
     */
    public PushedMessages push;
}
