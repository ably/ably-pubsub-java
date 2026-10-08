package io.ably.pubsub.transport;

import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.util.Clock;
import io.ably.pubsub.util.SystemClock;

import java.util.Arrays;
import java.util.Collections;


/**
 * Object to encapsulate primary host name and shuffled fallback host names.
 *
 * Methods on this class are safe to be called from any thread.
 */
public class Hosts {
    private final String primaryHost;
    private final String[] fallbackHosts;
    private final long fallbackRetryTimeout;

    private final Preferred preferred = new Preferred();
    private final Clock clock;

    /**
     * Create Hosts object
     *
     * @param options ClientOptions to get endpoint and fallbackHosts from
     *
     * REST requests and realtime connections share the same primary domain (RSC25, RTN2),
     * so an HttpCore and a ConnectionManager built from the same options resolve the same hosts.
     */
    public Hosts(final ClientOptions options) {
        /* REC1 */
        this.primaryHost = Defaults.getPrimaryDomain(options.endpoint);
        /* REC2a2: explicit fallbackHosts always replace the defaults; REC2c: otherwise derive them from the endpoint */
        String[] tempFallbackHosts = options.fallbackHosts != null
            ? options.fallbackHosts
            : Defaults.getEndpointFallbackHosts(options.endpoint);
        fallbackHosts = tempFallbackHosts.clone();
        /* RSC15a: shuffle the fallback hosts. */
        Collections.shuffle(Arrays.asList(fallbackHosts));
        fallbackRetryTimeout = options.fallbackRetryTimeout;
        this.clock = SystemClock.clockFrom(options);
    }

    /**
     * set preferred hostname, which might not be the primary
     */
    public synchronized void setPreferredHost(final String prefHost, final boolean temporary) {
        if (preferred.isHost(prefHost)) {
            /* a successful request against a fallback; don't update the expiry time */
            return;
        }
        if(prefHost.equals(primaryHost)) {
            /* a successful request against the primary host; reset */
            preferred.clear();
        } else {
            preferred.setHost(prefHost, temporary ? clock.currentTimeMillis() + fallbackRetryTimeout : 0);
        }
    }

    /**
     * Get primary host name
     */
    public String getPrimaryHost() {
        return primaryHost;
    }

    /**
     * Get preferred host name (taking into account any affinity to a fallback: see RSC15f)
     */
    public synchronized String getPreferredHost() {
        final String host = preferred.getHostOrClearIfExpired(clock);
        return (host == null) ? primaryHost : host;
    }

    /**
     * Get next fallback host if any
     *
     * @param lastHost
     * @return Successor host that can be used as a fallback.
     * null, if there is no successor fallback available.
     */
    public synchronized String getFallback(String lastHost) {
        int idx;
        if (lastHost.equals(primaryHost)) {
            idx = 0;
        } else if(lastHost.equals(preferred.getHostOrClearIfExpired(clock))) {
            /* RSC15f: there was a failure on an unexpired, cached fallback; so try again using the primary */
            preferred.clear();
            return primaryHost;
        } else {
            /* Onto next fallback. */
            idx = Arrays.asList(fallbackHosts).indexOf(lastHost);
            if (idx < 0) {
                return null;
            }
            ++idx;
        }
        if (idx >= fallbackHosts.length) {
            return null;
        }
        return fallbackHosts[idx];
    }

    public synchronized int fallbackHostsRemaining(String candidateHost) {
        if(candidateHost.equals(primaryHost) || candidateHost.equals(preferred.getHost())) {
            return fallbackHosts.length;
        }
        return fallbackHosts.length - Arrays.asList(fallbackHosts).indexOf(candidateHost) - 1;
    }

    private static class Preferred {
        private String host;
        private long expiry;

        public void clear() {
            host = null;
            expiry = 0;
        }

        public boolean isHost(final String host) {
            return (this.host == null) ? (host == null) : this.host.equals(host);
        }

        public void setHost(final String host, final long expiry) {
            this.host = host;
            this.expiry = expiry;
        }

        public String getHostOrClearIfExpired(Clock clock) {
            if(expiry > 0 && expiry <= clock.currentTimeMillis()) {
                clear(); // expired, so reset
            }
            return host;
        }

        public String getHost() {
            return host;
        }
    }
}
