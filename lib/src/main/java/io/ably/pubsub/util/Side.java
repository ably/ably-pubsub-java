package io.ably.pubsub.util;

import io.ably.pubsub.types.ClientOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal helper shared by the {@code io.ably.pubsub:device} and {@code io.ably.pubsub:server}
 * door artifacts. It lives in this module's sources and ships in {@code io.ably.pubsub:core}
 * (and the Android core) as {@code io.ably.pubsub.util.Side}, so both doors depend on it
 * through the core artifact they already require. It is public only because the doors live in
 * other packages; it is not part of the supported API.
 * <p>
 * Nothing here may grow into a general abstraction over the core: it exists only to stamp the
 * side a package declares.
 */
public final class Side {
    private Side() {}

    /*
     * The `-device` / `-server` suffix on both identifiers below is load-bearing, not
     * cosmetic. On API-key auth the realtime system grants the server exemption by matching
     * an agent entry ending in `-server`, and an identifier that is not yet in the
     * ably-common registry is classified by that suffix alone. Renaming either without
     * preserving its suffix silently reclassifies every client the package constructs.
     *
     * Both live here rather than in the package that uses each, so the naming scheme can be
     * changed in one place.
     */

    /** The agent identifier declaring the device side, sent by {@code io.ably.pubsub:device}. */
    public static final String DEVICE_AGENT_IDENTIFIER = "ably-pubsub-device";

    /**
     * The agent identifier declaring the server side, sent by {@code io.ably.pubsub:server}.
     * <p>
     * This is the entry that earns the MAU exemption on API-key auth, so its {@code -server}
     * suffix is the one with billing consequences.
     */
    public static final String SERVER_AGENT_IDENTIFIER = "ably-pubsub-server";

    /**
     * Injects a side agent identifier into the provided {@code ClientOptions} instance.
     * The method adds the specified agent identifier to the {@code agents} map in the given
     * {@code ClientOptions} object, and removes the opposite side's identifier, so a client
     * never declares both sides. If the {@code agents} map does not exist, a new one is created.
     * <p>
     * This mutates {@code options} (it replaces {@code options.agents}); callers that must not
     * alter their own object pass a {@link ClientOptions#copy()}.
     *
     * @param options the {@code ClientOptions} instance to which the agent identifier will be added.
     *                If {@code null}, the method returns {@code null}.
     * @param identifier the agent identifier to be injected into the {@code ClientOptions} instance.
     *                   This identifier is used for specifying and classifying the type of client.
     * @return the updated {@code ClientOptions} instance with the injected agent identifier.
     *         Returns {@code null} if the input {@code options} is {@code null}.
     */
    public static ClientOptions injectSideAgent(ClientOptions options, String identifier) {
        if (options == null) {
            return null;
        }
        Map<String, String> agents = new HashMap<>();
        if (options.agents != null) {
            agents.putAll(options.agents);
        }
        agents.remove(DEVICE_AGENT_IDENTIFIER.equals(identifier) ? SERVER_AGENT_IDENTIFIER
            : SERVER_AGENT_IDENTIFIER.equals(identifier) ? DEVICE_AGENT_IDENTIFIER : null);
        agents.put(identifier, null);
        options.agents = agents;
        return options;
    }
}
