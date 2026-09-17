package io.ably.pubsub.util;

import io.ably.pubsub.test.util.EmptyPlatformAgentProvider;
import io.ably.pubsub.transport.Defaults;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class AgentHeaderCreatorTest {
    private final static String PREDEFINED_AGENTS = Defaults.ABLY_AGENT_VERSION;
    private final PlatformAgentProvider emptyPlatformAgentProvider = new EmptyPlatformAgentProvider();

    @Test
    public void should_create_default_header_if_there_are_no_additional_agents() {
        // given
        Map<String, String> agents = new HashMap<>();

        // when
        String agentHeaderValue = AgentHeaderCreator.create(agents, emptyPlatformAgentProvider);

        // then
        assertMatchingAgentHeaders(PREDEFINED_AGENTS, agentHeaderValue);
    }

    @Test
    public void should_create_default_header_if_additional_agents_are_null() {
        // given

        // when
        String agentHeaderValue = AgentHeaderCreator.create(null, emptyPlatformAgentProvider);

        // then
        assertMatchingAgentHeaders(PREDEFINED_AGENTS, agentHeaderValue);
    }

    @Test
    public void should_create_header_with_appended_agents_if_they_are_provided() {
        // given
        Map<String, String> agents = new HashMap<>();
        agents.put("library", "1.0.1");
        agents.put("other", "0.8.2");

        // when
        String agentHeaderValue = AgentHeaderCreator.create(agents, emptyPlatformAgentProvider);

        // then
        assertMatchingAgentHeaders(PREDEFINED_AGENTS + " library/1.0.1 other/0.8.2", agentHeaderValue);
    }

    @Test
    public void should_create_header_with_appended_agents_without_versions() {
        // given
        Map<String, String> agents = new HashMap<>();
        agents.put("library", "1.0.1");
        agents.put("no-version", null);

        // when
        String agentHeaderValue = AgentHeaderCreator.create(agents, emptyPlatformAgentProvider);

        // then
        assertMatchingAgentHeaders(PREDEFINED_AGENTS + " library/1.0.1 no-version", agentHeaderValue);
    }

    @Test
    public void should_create_header_with_platform_agent_if_it_is_provided() {
        // given
        Map<String, String> agents = new HashMap<>();
        agents.put("library", "1.0.1");
        PlatformAgentProvider androidPlatformAgentProvider = new PlatformAgentProvider() {
            @Override
            public String createPlatformAgent() {
                return "android/25";
            }
        };

        // when
        String agentHeaderValue = AgentHeaderCreator.create(agents, androidPlatformAgentProvider);

        // then
        assertMatchingAgentHeaders(PREDEFINED_AGENTS + " android/25 library/1.0.1", agentHeaderValue);
    }

    private void assertMatchingAgentHeaders(String expectedAgentHeader, String actualAgentHeader) {
        assertPredefinedAgentsAreAtTheStart(actualAgentHeader);
        assertAllExpectedAgentsArePresentInActualAgents(expectedAgentHeader, actualAgentHeader);
    }

    private void assertPredefinedAgentsAreAtTheStart(String actualAgentHeader) {
        assertTrue(
            actualAgentHeader + " does not start with the library predefined agents",
            actualAgentHeader.startsWith(PREDEFINED_AGENTS)
        );
    }

    private void assertAllExpectedAgentsArePresentInActualAgents(String expectedAgentHeader, String actualAgentHeader) {
        List<String> actualAgents = Arrays.asList(actualAgentHeader.split(" "));
        String[] expectedAgents = expectedAgentHeader.split(" ");
        for (String expectedAgent : expectedAgents) {
            assertTrue(
                actualAgentHeader + " does not include " + expectedAgent,
                actualAgents.contains(expectedAgent)
            );
        }
    }
}
