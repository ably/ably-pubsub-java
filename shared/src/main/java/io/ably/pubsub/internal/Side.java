package io.ably.pubsub.internal;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal helper shared by the {@code io.ably.pubsub:device} and {@code io.ably.pubsub:server}
 * door artifacts. It is compiled into each artifact's output from a shared source directory
 * rather than published, so that the two artifacts can share this code without a third
 * artifact existing for it to live in.
 * <p>
 * PDR-091 keeps {@code io.ably.pubsub:core} itself as the shared core, so nothing here may
 * grow into a general abstraction over the core: it exists only to stamp the side a package
 * declares.
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
     * Returns a copy of the caller's options carrying the agent entry that declares this
     * package's side.
     * <p>
     * The side entry is a <em>versionless flag</em> — a bare token on the wire, like the
     * platform's own {@code browser} entry — registered as such in the ably-common agents
     * registry (see ably/ably-common#361). Identity, version and support status keep
     * travelling on the SDK's own {@code ably-java/<version>} entry alongside it;
     * {@link io.ably.lib.util.AgentHeaderCreator} emits a map entry with a {@code null}
     * value as a bare token.
     * <p>
     * The copy is made with {@link ClientOptions#copy()} and a fresh agents map, so the
     * caller's options and their own {@code agents} map are both left untouched. The
     * caller's {@code agents} entries are preserved alongside the side stamp, so an SDK
     * layered on top of this package keeps its attribution. The side stamp is applied last
     * and so wins a collision on its own identifier: which side the package declares is the
     * package's to state, not the caller's to redefine.
     * <p>
     * {@code null} passes through unchanged rather than being defaulted, so a caller who
     * passes nothing gets the core constructor's own initialization error ("no options
     * provided") instead of constructing with only an {@code agents} entry and failing
     * later with a vaguer authentication error.
     *
     * @param options the options the caller passed to the door's builder, or {@code null}.
     * @param identifier the side-declaring agent identifier to stamp.
     * @return a stamped copy of the options, or {@code null} if {@code options} was {@code null}.
     */
    public static ClientOptions optionsWithSideAgent(ClientOptions options, String identifier) {
        if (options == null) {
            return null;
        }
        ClientOptions stamped = options.copy();
        Map<String, String> agents = new LinkedHashMap<>();
        if (options.agents != null) {
            agents.putAll(options.agents);
        }
        agents.put(identifier, null);
        stamped.agents = agents;
        return stamped;
    }

    /**
     * As {@link #optionsWithSideAgent(ClientOptions, String)}, for the API key or
     * token string form the core constructors also accept. Reuses the core's own
     * key-versus-token disambiguation ({@link ClientOptions#ClientOptions(String)}: an Ably
     * API key always contains a colon, an Ably token never does).
     *
     * @param keyOrToken the Ably API key or token string the caller passed to the door's builder.
     * @param identifier the side-declaring agent identifier to stamp.
     * @return stamped options constructed from the key or token.
     * @throws AblyException if the key or token string is rejected by the core.
     */
    public static ClientOptions optionsWithSideAgent(String keyOrToken, String identifier)
        throws AblyException {
        ClientOptions options = new ClientOptions(keyOrToken);
        options.agents = new LinkedHashMap<>();
        options.agents.put(identifier, null);
        return options;
    }
}
