package io.ably.pubsub.transport;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
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
    private final boolean primaryHostIsDefault;
    private final String defaultHost;
    private final String[] fallbackHosts;
    private final boolean fallbackHostsIsDefault;
    private final long fallbackRetryTimeout;

    private final Preferred preferred = new Preferred();
    private final Clock clock;

    /**
     * Create Hosts object
     *
     * @param primaryHost the primary hostname, null if not configured
     * @param defaultHost the default hostname that the primary hostname must
     *        match for fallback to occur
     * @param options ClientOptions to get environment and fallbackHosts from
     *
     * The fallback and environment processing here is used when the Hosts
     * object is used by a ConnectionManager (for a realtime connection) or by
     * an HttpCore for a rest connection. The case where the Hosts object is used
     * by an HttpCore that is being used by a ConnectionManager goes through this
     * code, but the results are ignored because ConnectionManager then calls
     * setHost() and fallback is not used.
     */
    public Hosts(final String primaryHost, final String defaultHost, final ClientOptions options) throws AblyException {
        this.defaultHost = defaultHost;
        boolean hasCustomPrimaryHost = primaryHost != null && !primaryHost.equalsIgnoreCase(defaultHost);
        String[] tempFallbackHosts = options.fallbackHosts;

        boolean isProduction = options.environment == null || options.environment.isEmpty() || "production".equalsIgnoreCase(options.environment);

        if (!hasCustomPrimaryHost && tempFallbackHosts == null && options.port == 0 && options.tlsPort == 0) {
            tempFallbackHosts = isProduction ? Defaults.HOST_FALLBACKS : Defaults.getEnvironmentFallbackHosts(options.environment);
        }

        if (hasCustomPrimaryHost) {
            this.primaryHost = primaryHost;
            if (options.environment != null) {
                /* TO3k2: It is never valid to provide both a restHost and environment value
                 * TO3k3: It is never valid to provide both a realtimeHost and environment value */
                throw AblyException.fromErrorInfo(new ErrorInfo("cannot set both restHost/realtimeHost and environment options", 40000, 400));
            }
        } else {
            this.primaryHost = isProduction ? defaultHost : options.environment + "-" + defaultHost;
        }
        primaryHostIsDefault = this.primaryHost.equalsIgnoreCase(defaultHost);

        fallbackHostsIsDefault = Arrays.equals(Defaults.HOST_FALLBACKS, tempFallbackHosts);
        fallbackHosts = tempFallbackHosts == null ? new String[] {} : tempFallbackHosts.clone();
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
        if (fallbackHosts == null)
            return null;
        int idx;
        if (lastHost.equals(primaryHost)) {
            /* RSC15b, RTN17b: only use fallback if the hostname has not been overridden
             * or if ClientOptions#fallbackHosts was provided. */
            if (!primaryHostIsDefault && fallbackHostsIsDefault)
                return null;
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
        if(fallbackHosts == null) {
            return 0;
        }
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
