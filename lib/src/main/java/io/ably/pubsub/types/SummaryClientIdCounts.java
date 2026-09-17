package io.ably.pubsub.types;

import java.util.Map;

/**
 * The per-name value for the multiple.v1 aggregation method.
 */
public class SummaryClientIdCounts {
    /**
     * The sum of the counts from all clients who have published an annotation with this name
     */
    public final int total; // TM7d1a
    /**
     * A list of the clientIds of all clients who have published an annotation with this
     * name, and the count each of them have contributed.
     */
    public final Map<String, Integer> clientIds; // TM7d1b
    /**
     * The sum of the counts from all unidentified clients who have published an annotation with this
     * name, and so who are not included in the clientIds list
     */
    public final int totalUnidentified; // TM7d1d
    /**
     * Whether the list of clientIds has been clipped due to exceeding the maximum number of
     * clients.
     */
    public final boolean clipped; // TM7d1c
    /**
     * The total number of distinct clientIds in the map (equal to length of map if clipped is false).
     */
    public final int totalClientIds; // TM7d1e

    public SummaryClientIdCounts(
        int total,
        Map<String, Integer> clientIds,
        int totalUnidentified,
        boolean clipped,
        int totalClientIds
    ) {
        this.total = total;
        this.clientIds = clientIds;
        this.totalUnidentified = totalUnidentified;
        this.clipped = clipped;
        this.totalClientIds = totalClientIds;
    }
}
