package io.ably.pubsub.test.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Auth.AuthOptions;
import io.ably.pubsub.http.Auth.TokenDetails;
import io.ably.pubsub.http.Auth.TokenParams;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.test.common.Setup.Key;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Capability;
import io.ably.pubsub.types.ClientOptions;

public class HttpCapabilityTest extends ParameterizedTest {

    private PubSubHttpClient ably;

    @Before
    public void setUpBefore() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        ably = HttpClientFactory.create(opts);
    }

    /**
     * Blanket intersection with specified key
     */
    @Test
    public void authcapability0() {
        try {
            Key key = testVars.keys[1];
            AuthOptions authOptions = new AuthOptions();
            authOptions.key = key.keyStr;
            TokenDetails tokenDetails = ably.auth.requestToken(null, authOptions);
            assertNotNull("Expected token value", tokenDetails.token);
            assertEquals("Unexpected capability", tokenDetails.capability, key.capability);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability0: Unexpected exception");
        }
    }

    /**
     * Equal intersection with specified key
     */
    @Test
    public void authcapability1() {
        try {
            Key key = testVars.keys[1];
            AuthOptions authOptions = new AuthOptions();
            authOptions.key = key.keyStr;
            TokenParams tokenParams = new TokenParams();
            tokenParams.capability = key.capability;
            TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
            assertNotNull("Expected token value", tokenDetails.token);
            assertEquals("Unexpected capability", tokenDetails.capability, key.capability);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability1: Unexpected exception");
        }
    }

    /**
     * Empty ops intersection
     */
    @Test
    public void authcapability2() {
        Key key = testVars.keys[1];
        AuthOptions authOptions = new AuthOptions();
        authOptions.key = key.keyStr;
        TokenParams tokenParams = new TokenParams();
        Capability capability = new Capability();
        capability.addResource("testchannel", "subscribe");
        tokenParams.capability = capability.toString();
        try {
            ably.auth.requestToken(tokenParams, authOptions);
            fail("Invalid capability, expected rejection");
        } catch(AblyException e) {
            assertEquals("Unexpected error code", e.errorInfo.code, 40160);
        }
    }

    /**
     * Empty paths intersection
     */
    @Test
    public void authcapability3() {
        Key key = testVars.keys[1];
        AuthOptions authOptions = new AuthOptions();
        authOptions.key = key.keyStr;
        TokenParams tokenParams = new TokenParams();
        Capability capability = new Capability();
        capability.addResource("testchannelx", "publish");
        tokenParams.capability = capability.toString();
        try {
            ably.auth.requestToken(tokenParams, authOptions);
            fail("Invalid capability, expected rejection");
        } catch(AblyException e) {
            assertEquals("Unexpected error code", e.errorInfo.code, 40160);
        }
    }

    /**
     * Non-empty ops intersection
     */
    @Test
    public void authcapability4() {
        try {
            Key key = testVars.keys[4];
            AuthOptions authOptions = new AuthOptions();
            authOptions.key = key.keyStr;
            TokenParams tokenParams = new TokenParams();
            Capability requestedCapability = new Capability();
            requestedCapability.addResource("channel2", "presence", "subscribe");
            tokenParams.capability = requestedCapability.toString();
            TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
            Capability expectedCapability = new Capability();
            expectedCapability.addResource("channel2", "subscribe");
            assertNotNull("Expected token value", tokenDetails.token);
            assertEquals("Unexpected capability", tokenDetails.capability, expectedCapability.toString());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability4: Unexpected exception");
        }
    }

    /**
     * Non-empty paths intersection
     */
    @Test
    public void authcapability5() {
        try {
            Key key = testVars.keys[4];
            AuthOptions authOptions = new AuthOptions();
            authOptions.key = key.keyStr;
            TokenParams tokenParams = new TokenParams();
            Capability requestedCapability = new Capability();
            requestedCapability.addResource("channel2", "presence", "subscribe");
            requestedCapability.addResource("channelx", "presence", "subscribe");
            tokenParams.capability = requestedCapability.toString();
            TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
            Capability expectedCapability = new Capability();
            expectedCapability.addResource("channel2", "subscribe");
            assertNotNull("Expected token value", tokenDetails.token);
            assertEquals("Unexpected capability", tokenDetails.capability, expectedCapability.toString());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability5: Unexpected exception");
        }
    }

    /**
     * Wildcard ops intersection
     */
    @Test
    public void authcapability6() {
        try {
            Key key = testVars.keys[4];
            AuthOptions authOptions = new AuthOptions();
            authOptions.key = key.keyStr;
            TokenParams tokenParams = new TokenParams();
            Capability requestedCapability = new Capability();
            requestedCapability.addResource("channel2", "*");
            tokenParams.capability = requestedCapability.toString();
            TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
            Capability expectedCapability = new Capability();
            expectedCapability.addResource("channel2", "publish", "subscribe");
            assertNotNull("Expected token value", tokenDetails.token);
            assertEquals("Unexpected capability", tokenDetails.capability, expectedCapability.toString());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability6: Unexpected exception");
        }
    }
    @Test
    public void authcapability7() {
        try {
            Key key = testVars.keys[4];
            AuthOptions authOptions = new AuthOptions();
            authOptions.key = key.keyStr;
            TokenParams tokenParams = new TokenParams();
            Capability requestedCapability = new Capability();
            requestedCapability.addResource("channel6", "publish", "subscribe");
            tokenParams.capability = requestedCapability.toString();
            TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
            Capability expectedCapability = new Capability();
            expectedCapability.addResource("channel6", "publish", "subscribe");
            assertNotNull("Expected token value", tokenDetails.token);
            assertEquals("Unexpected capability", tokenDetails.capability, expectedCapability.toString());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability7: Unexpected exception");
        }
    }

    /**
     * Wildcard resources intersection
     */
    @Test
    public void authcapability8() {
        try {
            Key key = testVars.keys[2];
            AuthOptions authOptions = new AuthOptions();
            authOptions.key = key.keyStr;
            TokenParams tokenParams = new TokenParams();
            Capability requestedCapability = new Capability();
            requestedCapability.addResource("cansubscribe", "subscribe");
            tokenParams.capability = requestedCapability.toString();
            TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
            assertNotNull("Expected token value", tokenDetails.token);
            assertEquals("Unexpected capability", tokenDetails.capability, requestedCapability.toString());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability8: Unexpected exception");
        }
    }
    @Test
    public void authcapability9() {
        try {
            Key key = testVars.keys[2];
            AuthOptions authOptions = new AuthOptions();
            authOptions.key = key.keyStr;
            TokenParams tokenParams = new TokenParams();
            Capability requestedCapability = new Capability();
            requestedCapability.addResource("canpublish:check", "publish");
            tokenParams.capability = requestedCapability.toString();
            TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
            assertNotNull("Expected token value", tokenDetails.token);
            assertEquals("Unexpected capability", tokenDetails.capability, requestedCapability.toString());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability9: Unexpected exception");
        }
    }
    @Test
    public void authcapability10() {
        try {
            Key key = testVars.keys[2];
            AuthOptions authOptions = new AuthOptions();
            authOptions.key = key.keyStr;
            TokenParams tokenParams = new TokenParams();
            Capability requestedCapability = new Capability();
            requestedCapability.addResource("cansubscribe:*", "subscribe");
            tokenParams.capability = requestedCapability.toString();
            TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
            assertNotNull("Expected token value", tokenDetails.token);
            assertEquals("Unexpected capability", tokenDetails.capability, requestedCapability.toString());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability10: Unexpected exception");
        }
    }

    /**
     * Invalid capabilities
     */
    @Test
    public void authinvalid0() {
        TokenParams tokenParams = new TokenParams();
        Capability invalidCapability = new Capability();
        invalidCapability.addResource("channel0", "publish_");
        tokenParams.capability = invalidCapability.toString();
        try {
            ably.auth.requestToken(tokenParams, null);
            fail("Invalid capability, expected rejection");
        } catch(AblyException e) {
            assertEquals("Unexpected error code", e.errorInfo.code, 40000);
        }
    }
    @Test
    public void authinvalid1() {
        TokenParams tokenParams = new TokenParams();
        Capability invalidCapability = new Capability();
        invalidCapability.addResource("channel0", "*", "publish");
        tokenParams.capability = invalidCapability.toString();
        try {
            ably.auth.requestToken(tokenParams, null);
            fail("Invalid capability, expected rejection");
        } catch(AblyException e) {
            assertEquals("Unexpected error code", e.errorInfo.code, 40000);
        }
    }
    @Test
    public void authinvalid2() {
        TokenParams tokenParams = new TokenParams();
        Capability invalidCapability = new Capability();
        invalidCapability.addResource("channel0");
        tokenParams.capability = invalidCapability.toString();
        try {
            ably.auth.requestToken(tokenParams, null);
            fail("Invalid capability, expected rejection");
        } catch(AblyException e) {
            assertEquals("Unexpected error code", e.errorInfo.code, 40000);
        }
    }
}
