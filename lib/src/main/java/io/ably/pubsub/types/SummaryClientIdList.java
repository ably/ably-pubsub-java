package io.ably.pubsub.types;

import java.util.List;

/**
 * The summary entry for aggregated annotations that use the flag.v1
 * aggregation method; also the per-name value for some other aggregation methods.
 */
public class SummaryClientIdList {
    /**
     * The sum of the counts from all clients who have published an annotation with this name
     */
    public final int total; // TM7c1a
    /**
     * A list of the clientIds of all clients who have published an annotation with this name (or
     * type, depending on context).
     */
    public final List<String> clientIds; // TM7
    /**
     * Whether the list of clientIds has been clipped due to exceeding the maximum number of
     * clients.
     */
    public final boolean clipped; // TM7c1c

    public SummaryClientIdList(int total, List<String> clientIds, boolean clipped) {
        this.total = total;
        this.clientIds = clientIds;
        this.clipped = clipped;
    }
}
