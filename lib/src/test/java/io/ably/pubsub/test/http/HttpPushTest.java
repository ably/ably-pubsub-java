package io.ably.pubsub.test.http;

import com.google.gson.JsonObject;
import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.push.PushBase.ChannelSubscription;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.RealtimeClientFactory;
import io.ably.pubsub.realtime.CompletionListener;
import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.DeviceDetails;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.common.Helpers.CompletionWaiter;
import io.ably.pubsub.test.common.Helpers.MessageWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.test.util.TestCases;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.AsyncPaginatedResult;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.JsonUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.junit.Assert.assertEquals;

public class HttpPushTest extends ParameterizedTest {
    private static PubSubHttpClient rest;
    private static RealtimeClient realtime;

    private static DeviceDetails deviceDetails;
    private static DeviceDetails deviceDetails1ClientA;
    private static DeviceDetails deviceDetails2ClientA;
    private static DeviceDetails deviceDetails3ClientB;
    private static DeviceDetails deviceDetails4ClientC;
    private static DeviceDetails[] allDeviceDetails;

    private static ChannelSubscription subscriptionFooDevice1;
    private static ChannelSubscription subscriptionFooDevice2;
    private static ChannelSubscription subscriptionBarDevice2;
    private static ChannelSubscription subscriptionFooClientA;
    private static ChannelSubscription subscriptionFooClientB;
    private static ChannelSubscription subscriptionBarClientB;
    private static ChannelSubscription subscriptionFooDevice4;
    private static ChannelSubscription[] allSubscriptions;

    private static Helpers.RawHttpTracker httpTracker;

    private static JsonObject testPayload = JsonUtils.object()
            .add("data", JsonUtils.object()
                .add("foo", "bar"))
            .add("notification", JsonUtils.object()
                .add("body", null)
                .add("collapseKey", null)
                .add("icon", null)
                .add("sound", null)
                .add("title", null)
                .add("ttl", null)
        ).toJson();

    @Rule
    public Timeout testTimeout = Timeout.seconds(60);

    @Before
    public void setUpBefore() throws Exception {
        if (rest != null) {
            return;
        }

        httpTracker = new Helpers.RawHttpTracker();
        DebugOptions options = createOptions(testVars.keys[0].keyStr);
        options.httpListener = httpTracker;
        rest = HttpClientFactory.create(options);
        realtime = RealtimeClientFactory.create(options);

        deviceDetails = DeviceDetails.fromJsonObject(JsonUtils.object()
                .add("id", "testDeviceDetails")
                .add("platform", "ios")
                .add("formFactor", "phone")
                .add("metadata", JsonUtils.object())
                .add("push", JsonUtils.object()
                        .add("recipient", JsonUtils.object()
                                .add("transportType", "apns")
                                .add("deviceToken", "foo")))
                .toJson());

        deviceDetails1ClientA = DeviceDetails.fromJsonObject(JsonUtils.object()
                .add("id", "deviceDetails1ClientA")
                .add("platform", "android")
                .add("formFactor", "tablet")
                .add("clientId", "clientA")
                .add("metadata", JsonUtils.object())
                .add("push", JsonUtils.object()
                        .add("recipient", JsonUtils.object()
                                .add("transportType", "fcm")
                                .add("registrationToken", "qux")))
                .toJson());

        deviceDetails2ClientA = DeviceDetails.fromJsonObject(JsonUtils.object()
                .add("id", "deviceDetails2ClientA")
                .add("platform", "android")
                .add("formFactor", "tablet")
                .add("clientId", "clientA")
                .add("metadata", JsonUtils.object())
                .add("push", JsonUtils.object()
                        .add("recipient", JsonUtils.object()
                                .add("transportType", "fcm")
                                .add("registrationToken", "qux")))
                .toJson());

        deviceDetails3ClientB = DeviceDetails.fromJsonObject(JsonUtils.object()
                .add("id", "deviceDetails3ClientB")
                .add("platform", "android")
                .add("formFactor", "tablet")
                .add("clientId", "clientB")
                .add("metadata", JsonUtils.object())
                .add("push", JsonUtils.object()
                        .add("recipient", JsonUtils.object()
                                .add("transportType", "fcm")
                                .add("registrationToken", "qux")))
                .toJson());

        deviceDetails4ClientC = DeviceDetails.fromJsonObject(JsonUtils.object()
                .add("id", "deviceDetails4ClientC")
                .add("platform", "android")
                .add("formFactor", "tablet")
                .add("clientId", "clientC")
                .add("metadata", JsonUtils.object())
                .add("push", JsonUtils.object()
                        .add("recipient", JsonUtils.object()
                                .add("transportType", "fcm")
                                .add("registrationToken", "qux")))
                .toJson());

        allDeviceDetails = new DeviceDetails[]{
                deviceDetails,
                deviceDetails1ClientA,
                deviceDetails2ClientA,
                deviceDetails3ClientB,
                deviceDetails4ClientC
        };

        for (DeviceDetails device : allDeviceDetails) {
            rest.push.admin.deviceRegistrations.save(device);
        }

        subscriptionFooDevice1 = ChannelSubscription.forDevice("pushenabled:foo", "deviceDetails1ClientA");
        subscriptionFooDevice2 = ChannelSubscription.forDevice("pushenabled:foo", "deviceDetails2ClientA");
        subscriptionBarDevice2 = ChannelSubscription.forDevice("pushenabled:bar", "deviceDetails2ClientA");
        subscriptionFooClientA = ChannelSubscription.forClientId("pushenabled:foo", "clientA");
        subscriptionFooClientB = ChannelSubscription.forClientId("pushenabled:foo", "clientB");
        subscriptionBarClientB = ChannelSubscription.forClientId("pushenabled:bar", "clientB");
        subscriptionFooDevice4 = ChannelSubscription.forDevice("pushenabled:foo", "deviceDetails4ClientC");

        allSubscriptions = new ChannelSubscription[]{
                subscriptionFooDevice1,
                subscriptionFooDevice2,
                subscriptionBarDevice2,
                subscriptionFooClientA,
                subscriptionFooClientB,
                subscriptionBarClientB,
                subscriptionFooDevice4
        };

        for (ChannelSubscription sub : allSubscriptions) {
            rest.push.admin.channelSubscriptions.save(sub);
        }
    }

    @AfterClass
    public static void tearDownAfter() throws Exception {
        for (DeviceDetails device : allDeviceDetails) {
            rest.push.admin.deviceRegistrations.remove(device);
        }
        for (ChannelSubscription sub : allSubscriptions) {
            rest.push.admin.channelSubscriptions.remove(sub);
        }
    }

    // RHS1a
    @Test
    public void push_admin_publish() throws Exception {
        class TestCase extends TestCases.Base {
            final Param[] recipient;
            final JsonObject payload;

            TestCase(String name, Param[] recipient, JsonObject data, String expectedError) {
                this(name, recipient, data, expectedError, 0);
            }

            TestCase(String name, Param[] recipient, JsonObject payload, String expectedError, int expectedStatusCode) {
                super(name, expectedError, expectedStatusCode);
                this.payload = payload;
                this.recipient = recipient;
            }

            @Override
            public void run() throws Exception {
                final String channelName = "pushenabled:push_admin_publish-" + this.name;

                CompletionWaiter waiter = new CompletionWaiter();
                realtime.channels.get(channelName).attach(waiter);
                waiter.waitFor(1);

                new Helpers.SyncAndAsync<Void, Void>() {
                    @Override
                    public Void getSync(Void arg) throws AblyException {
                        rest.push.admin.publish(recipient, payload);
                        return null;
                    }

                    @Override
                    public void getAsync(Void arg, Callback<Void> callback) {
                        rest.push.admin.publishAsync(recipient, payload, new CompletionListener.FromCallback(callback));
                    }

                    @Override
                    public void then(Helpers.AblyFunction<Void, Void> get) throws AblyException {
                        MessageWaiter messages = new MessageWaiter(realtime.channels.get(channelName), "__ably_push__");
                        get.apply(null);
                        messages.waitFor(1, 10000);

                        assertEquals(1, messages.receivedMessages.size());
                        assertEquals(payload.toString(), messages.receivedMessages.get(0).data);
                    }
                }.run();
            }
        }

        TestCases testCases = new TestCases();
        testCases.add(new TestCase(
                "ok",
                new Param[]{
                        new Param("transportType", "ablyChannel"),
                        new Param("channel", "pushenabled:push_admin_publish-ok"),
                        new Param("ablyKey", testVars.keys[0].keyStr),
                        new Param("ablyUrl", String.format(Locale.ROOT, "%s%s:%d", rest.httpCore.scheme, rest.httpCore.getPrimaryHost(), rest.httpCore.port)),
                },
                testPayload,
                null));
        testCases.add(new TestCase(
                "bad recipient",
                Param.set(null, "foo", "bar"),
                JsonUtils.object()
                        .add("data", JsonUtils.object()
                                .add("foo", "bar")).toJson(),
                "", 400));
        testCases.add(new TestCase(
                "empty recipient",
                new Param[]{},
                JsonUtils.object()
                        .add("data", JsonUtils.object()
                                .add("foo", "bar")).toJson(),
                "recipient"));
        testCases.add(new TestCase(
                "empty payload",
                Param.set(null, "ablyChannel", "pushenabled:push_admin_publish-ok"),
                null,
                "payload"));

        testCases.run();
    }

    // RHS1b1
    @Test
    public void push_admin_deviceRegistrations_get() throws Exception {
        class TestCase extends TestCases.Base {
            final String deviceId;
            final DeviceDetails expectedDevice;

            TestCase(String name, String deviceId, DeviceDetails expectedDevice, String expectedError, int expectedStatusCode) {
                super(name, expectedError, expectedStatusCode);
                this.deviceId = deviceId;
                this.expectedDevice = expectedDevice;
            }

            @Override
            public void run() throws Exception {
                new Helpers.SyncAndAsync<Void, DeviceDetails>() {
                    @Override
                    public DeviceDetails getSync(Void arg) throws AblyException {
                        return rest.push.admin.deviceRegistrations.get(deviceId);
                    }

                    @Override
                    public void getAsync(Void arg, Callback<DeviceDetails> callback) {
                        rest.push.admin.deviceRegistrations.getAsync(deviceId, callback);
                    }

                    @Override
                    public void then(Helpers.AblyFunction<Void, DeviceDetails> get) throws AblyException {
                        assertEquals(expectedDevice, get.apply(null));
                    }
                }.run();
            }
        }

        TestCases testCases = new TestCases();

        testCases.add(new TestCase("found", deviceDetails.id, deviceDetails, null, 0));
        testCases.add(new TestCase("not found", "madeup", null, "not found", 404));

        testCases.run();
    }

    // RHS1b2
    @Test
    public void push_admin_deviceRegistrations_list() throws Exception {
        class TestCase extends TestCases.Base {
            private final Param[] params;
            private final DeviceDetails[] expected;

            TestCase(String name, Param[] params, DeviceDetails[] expected, String expectedError, int expectedStatusCode) {
                super(name, expectedError, expectedStatusCode);
                this.params = params;
                this.expected = expected;
            }

            @Override
            public void run() throws Exception {
                new Helpers.SyncAndAsync<Void, DeviceDetails[]>() {
                    @Override
                    public DeviceDetails[] getSync(Void arg) throws AblyException {
                        return rest.push.admin.deviceRegistrations.list(params).items();
                    }

                    @Override
                    public void getAsync(Void arg, Callback<DeviceDetails[]> callback) {
                        rest.push.admin.deviceRegistrations.listAsync(params, new Callback.Map<AsyncPaginatedResult<DeviceDetails>, DeviceDetails[]>(callback) {
                            @Override
                            public DeviceDetails[] map(AsyncPaginatedResult<DeviceDetails> result) {
                                return result.items();
                            }
                        });
                    }

                    @Override
                    public void then(Helpers.AblyFunction<Void, DeviceDetails[]> get) throws AblyException {
                        Helpers.assertArrayUnorderedEquals(expected, get.apply(null));
                    }
                }.run();
            }
        }

        TestCases testCases = new TestCases();

        testCases.add(new TestCase(
                "by deviceId",
                Param.push(null, "deviceId", deviceDetails.id),
                new DeviceDetails[]{deviceDetails},
                null, 0));
        testCases.add(new TestCase(
                "by clientId A",
                Param.push(null, "clientId", "clientA"),
                new DeviceDetails[]{deviceDetails2ClientA, deviceDetails1ClientA},
                null, 0));
        testCases.add(new TestCase(
                "by clientId B",
                Param.push(null, "clientId", "clientB"),
                new DeviceDetails[]{deviceDetails3ClientB},
                null, 0));
        testCases.add(new TestCase(
                "all",
                Param.push(null, "direction", "forwards"),
                allDeviceDetails,
                null, 0));
        testCases.add(new TestCase(
                "none",
                Param.push(null, "deviceId", "madeup"),
                new DeviceDetails[]{},
                null, 0));

        testCases.run();
    }

    // RHS1b3
    @Test
    public void push_admin_deviceRegistrations_save() throws Exception {
        new Helpers.SyncAndAsync<DeviceDetails, DeviceDetails>() {
            @Override
            public DeviceDetails getSync(DeviceDetails saved) throws AblyException {
                return rest.push.admin.deviceRegistrations.save(saved);
            }

            @Override
            public void getAsync(DeviceDetails saved, Callback<DeviceDetails> callback) {
                rest.push.admin.deviceRegistrations.saveAsync(saved, callback);
            }

            @Override
            public void then(final Helpers.AblyFunction<DeviceDetails, DeviceDetails> get) throws AblyException {
                final DeviceDetails saved = DeviceDetails.fromJsonObject(JsonUtils.object()
                        .add("id", "newDeviceDetails")
                        .add("platform", "ios")
                        .add("formFactor", "phone")
                        .add("metadata", JsonUtils.object())
                        .add("push", JsonUtils.object()
                                .add("recipient", JsonUtils.object()
                                        .add("transportType", "apns")
                                        .add("deviceToken", "foo")))
                        .toJson());
                try {
                    // Save new
                    DeviceDetails got = get.apply(saved);
                    Helpers.RawHttpRequest request = httpTracker.getLastRequest();
                    assertEquals("PUT", request.method);
                    assertEquals("/push/deviceRegistrations/" + saved.id, request.url.getPath());
                    assertEquals(saved, got);

                    // Mutate
                    saved.clientId = "foo";
                    got = get.apply(saved);
                    assertEquals("foo", got.clientId);

                    // Failing
                    Helpers.expectedError(new Helpers.AblyFunction<Void, Void>() {
                        @Override
                        public Void apply(Void aVoid) throws AblyException {
                            saved.formFactor = "madeup";
                            get.apply(saved);
                            return null;
                        }
                    }, "", 400);
                } finally {
                    rest.push.admin.deviceRegistrations.remove(saved);
                }
            }
        }.run();
    }

    // RHS1b4
    @Test
    public void push_admin_deviceRegistrations_remove() throws Exception {
        final DeviceDetails saved = DeviceDetails.fromJsonObject(JsonUtils.object()
                .add("id", "newDeviceDetails")
                .add("platform", "ios")
                .add("formFactor", "phone")
                .add("metadata", JsonUtils.object())
                .add("push", JsonUtils.object()
                        .add("recipient", JsonUtils.object()
                                .add("transportType", "apns")
                                .add("deviceToken", "foo")))
                .toJson());

        new Helpers.SyncAndAsync<Void, Void>() {
            @Override
            public Void getSync(Void aVoid) throws AblyException {
                rest.push.admin.deviceRegistrations.remove(saved);
                return null;
            }

            @Override
            public void getAsync(Void aVoid, Callback<Void> callback) {
                rest.push.admin.deviceRegistrations.removeAsync(saved, new CompletionListener.FromCallback(callback));
            }

            @Override
            public void then(Helpers.AblyFunction<Void, Void> get) throws AblyException {
                try {
                    rest.push.admin.deviceRegistrations.save(saved);

                    // Ensure it exists
                    rest.push.admin.deviceRegistrations.get(saved.id);

                    // Existing
                    get.apply(null);
                    Helpers.expectedError(new Helpers.AblyFunction<Void, Void>() {
                        @Override
                        public Void apply(Void aVoid) throws AblyException {
                            rest.push.admin.deviceRegistrations.get(saved.id);
                            return null;
                        }
                    }, "", 404);

                    // Non-existing
                    get.apply(null);
                } finally {
                    rest.push.admin.deviceRegistrations.remove(saved);
                }
            }
        }.run();
    }

    // RHS1b5
    @Test
    public void push_admin_deviceRegistrations_removeWhere() throws Exception {
        class TestCase extends TestCases.Base {
            private final Param[] params;
            private final DeviceDetails[] expectedRemoved;

            TestCase(String name, String expectedError, Param[] params, DeviceDetails[] expectedRemoved) {
                super(name, expectedError);
                this.params = Param.push(params, "fullWait", "true");
                this.expectedRemoved = expectedRemoved;
            }

            @Override
            public void run() throws Exception {
                new Helpers.SyncAndAsync<Param[], Void>() {
                    @Override
                    public Void getSync(Param[] params) throws AblyException {
                        rest.push.admin.deviceRegistrations.removeWhere(params);
                        return null;
                    }

                    @Override
                    public void getAsync(Param[] params, Callback<Void> callback) {
                        rest.push.admin.deviceRegistrations.removeWhereAsync(params, new CompletionListener.FromCallback(callback));
                    }

                    @Override
                    public void then(Helpers.AblyFunction<Param[], Void> get) throws AblyException {
                        try {
                            get.apply(params);

                            PaginatedResult<DeviceDetails> result = rest.push.admin.deviceRegistrations.list(null);

                            Set<DeviceDetails> expectedRemaining = new CopyOnWriteArraySet<DeviceDetails>(Arrays.asList(allDeviceDetails));
                            expectedRemaining.removeAll(Arrays.asList(expectedRemoved));
                            Set<DeviceDetails> remaining = new CopyOnWriteArraySet<DeviceDetails>(Arrays.asList(result.items()));

                            assertEquals(expectedRemaining, remaining);
                        } finally {
                            for (DeviceDetails removed : expectedRemoved) {
                                rest.push.admin.deviceRegistrations.save(removed);
                            }
                        }
                    }
                }.run();
            }
        }

        TestCases testCases = new TestCases();

        testCases.add(new TestCase(
                "by clientId",
                null,
                Param.push(null, "clientId", "clientA"),
                new DeviceDetails[]{
                        deviceDetails1ClientA,
                        deviceDetails2ClientA
                }));
        testCases.add(new TestCase(
                "by deviceId",
                null,
                Param.push(null, "deviceId", deviceDetails2ClientA.id),
                new DeviceDetails[]{
                        deviceDetails2ClientA
                }));
        testCases.add(new TestCase(
                "matching none",
                null,
                Param.push(null, "deviceId", "madeup"),
                new DeviceDetails[]{}));

        testCases.run();
    }

    // RHS1c1
    @Test
    public void push_admin_channelSubscriptions_list() throws Exception {
        class TestCase extends TestCases.Base {
            private final Param[] params;
            private final ChannelSubscription[] expected;

            TestCase(String name, Param[] params, ChannelSubscription[] expected, String expectedError, int expectedStatusCode) {
                super(name, expectedError, expectedStatusCode);
                this.params = params;
                this.expected = expected;
            }

            @Override
            public void run() throws Exception {
                new Helpers.SyncAndAsync<Void, ChannelSubscription[]>() {
                    @Override
                    public ChannelSubscription[] getSync(Void arg) throws AblyException {
                        return rest.push.admin.channelSubscriptions.list(params).items();
                    }

                    @Override
                    public void getAsync(Void arg, Callback<ChannelSubscription[]> callback) {
                        rest.push.admin.channelSubscriptions.listAsync(params, new Callback.Map<AsyncPaginatedResult<ChannelSubscription>, ChannelSubscription[]>(callback) {
                            @Override
                            public ChannelSubscription[] map(AsyncPaginatedResult<ChannelSubscription> result) {
                                return result.items();
                            }
                        });
                    }

                    @Override
                    public void then(Helpers.AblyFunction<Void, ChannelSubscription[]> get) throws AblyException {
                        Helpers.assertArrayUnorderedEquals(expected, get.apply(null));
                    }
                }.run();
            }
        }

        TestCases testCases = new TestCases();

        testCases.add(new TestCase(
                "by deviceId",
                Param.push(null, "deviceId", deviceDetails4ClientC.id),
                new ChannelSubscription[]{subscriptionFooDevice4},
                null, 0));
        testCases.add(new TestCase(
                "by clientId A",
                Param.push(null, "clientId", "clientA"),
                new ChannelSubscription[]{subscriptionFooClientA},
                null, 0));
        testCases.add(new TestCase(
                "by clientId B",
                Param.push(null, "clientId", "clientB"),
                new ChannelSubscription[]{subscriptionFooClientB, subscriptionBarClientB},
                null, 0));
        testCases.add(new TestCase(
                "none",
                Param.push(null, "deviceId", "madeup"),
                new ChannelSubscription[]{},
                null, 0));
        testCases.add(new TestCase(
                "by clientId B and channel",
                Param.push(Param.push(null, "clientId", "clientB"), "channel", "pushenabled:bar"),
                new ChannelSubscription[]{subscriptionBarClientB},
                null, 0));

        testCases.run();
    }

    // RHS1c2
    @Test
    @Ignore("FIXME: tests interfere")
    public void push_admin_channelSubscriptions_listChannels() throws Exception {
        new Helpers.SyncAndAsync<Void, String[]>(){
            @Override
            public String[] getSync(Void aVoid) throws AblyException {
                return rest.push.admin.channelSubscriptions.listChannels(null).items();
            }

            @Override
            public void getAsync(Void aVoid, Callback<String[]> callback) {
                rest.push.admin.channelSubscriptions.listChannelsAsync(null, new Callback.Map<AsyncPaginatedResult<String>, String[]>(callback) {
                    @Override
                    public String[] map(AsyncPaginatedResult<String> result) {
                        return result.items();
                    }
                });
            }

            @Override
            public void then(Helpers.AblyFunction<Void, String[]> get) throws AblyException {
                Helpers.assertArrayUnorderedEquals(new String[]{"pushenabled:foo", "pushenabled:bar"}, get.apply(null));
            }
        }.run();
    }

   // RHS1c3
    @Test
    public void push_admin_channelSubscriptions_save() throws Exception {
        new Helpers.SyncAndAsync<ChannelSubscription, ChannelSubscription>() {
            @Override
            public ChannelSubscription getSync(ChannelSubscription saved) throws AblyException {
                return rest.push.admin.channelSubscriptions.save(saved);
            }

            @Override
            public void getAsync(ChannelSubscription saved, Callback<ChannelSubscription> callback) {
                rest.push.admin.channelSubscriptions.saveAsync(saved, callback);
            }

            @Override
            public void then(final Helpers.AblyFunction<ChannelSubscription, ChannelSubscription> get) throws AblyException {
                ChannelSubscription saved = ChannelSubscription.forClientId("pushenabled:qux", "newClient");
                try {
                    // Save new
                    ChannelSubscription got = get.apply(saved);
                    Helpers.RawHttpRequest request = httpTracker.getLastRequest();
                    assertEquals("POST", request.method);
                    assertEquals("/push/channelSubscriptions", request.url.getPath());
                    assertEquals(saved, got);

                    // Failing
                    Helpers.expectedError(new Helpers.AblyFunction<Void, Void>() {
                        @Override
                        public Void apply(Void aVoid) throws AblyException {
                            get.apply(ChannelSubscription.forClientId("notpushenabled", "foo"));
                            return null;
                        }
                    }, "not enabled", 401);
                } finally {
                    rest.push.admin.channelSubscriptions.remove(saved);
                }
            }
        }.run();
    }

    // RHS1c4
    @Test
    public void push_admin_channelSubscriptions_remove() throws Exception {
        final ChannelSubscription saved = ChannelSubscription.forClientId("pushenabled:qux", "newClient");

        new Helpers.SyncAndAsync<Void, Void>() {
            @Override
            public Void getSync(Void aVoid) throws AblyException {
                rest.push.admin.channelSubscriptions.remove(saved);
                return null;
            }

            @Override
            public void getAsync(Void aVoid, Callback<Void> callback) {
                rest.push.admin.channelSubscriptions.removeAsync(saved, new CompletionListener.FromCallback(callback));
            }

            @Override
            public void then(Helpers.AblyFunction<Void, Void> get) throws AblyException {
                try {
                    rest.push.admin.channelSubscriptions.save(saved);

                    // Ensure it exists
                    assertEquals(1, rest.push.admin.channelSubscriptions.list(Param.push(Param.push(null, "channel", "pushenabled:qux"), "clientId", "newClient")).items().length);

                    // Existing
                    get.apply(null);
                    assertEquals(0, rest.push.admin.channelSubscriptions.list(Param.push(Param.push(null, "channel", "pushenabled:qux"), "clientId", "newClient")).items().length);

                    // Non-existing
                    get.apply(null);
                } finally {
                    rest.push.admin.channelSubscriptions.remove(saved);
                }
            }
        }.run();
    }

    // RHS1c5
    @Test
    public void push_admin_channelSubscriptions_removeWhere() throws Exception {
        class TestCase extends TestCases.Base {
            private final Param[] params;
            private final ChannelSubscription[] expectedRemoved;

            TestCase(String name, String expectedError, Param[] params, ChannelSubscription[] expectedRemoved) {
                super(name, expectedError);
                this.params = Param.push(params, "fullWait", "true");
                this.expectedRemoved = expectedRemoved;
            }

            @Override
            public void run() throws Exception {
                new Helpers.SyncAndAsync<Param[], Void>() {
                    @Override
                    public Void getSync(Param[] params) throws AblyException {
                        rest.push.admin.channelSubscriptions.removeWhere(params);
                        return null;
                    }

                    @Override
                    public void getAsync(Param[] params, Callback<Void> callback) {
                        rest.push.admin.channelSubscriptions.removeWhereAsync(params, new CompletionListener.FromCallback(callback));
                    }

                    @Override
                    public void then(Helpers.AblyFunction<Param[], Void> get) throws AblyException {
                        try {
                            get.apply(params);

                            PaginatedResult<ChannelSubscription> result = rest.push.admin.channelSubscriptions.list(params);
                            assertEquals(0, result.items().length);
                        } finally {
                            for (ChannelSubscription removed : expectedRemoved) {
                                rest.push.admin.channelSubscriptions.save(removed);
                            }
                        }
                    }
                }.run();
            }
        }

        TestCases testCases = new TestCases();

        testCases.add(new TestCase(
                "by clientId",
                null,
                Param.push(null, "clientId", "clientB"),
                new ChannelSubscription[]{
                        subscriptionFooClientB,
                        subscriptionBarClientB
                }));
        testCases.add(new TestCase(
                "by clientId and channel",
                null,
                Param.push(Param.push(null, "clientId", "clientB"), "channel", "pushenabled:foo"),
                new ChannelSubscription[]{
                        subscriptionFooClientB,
                }));
        testCases.add(new TestCase(
                "by deviceId",
                null,
                Param.push(null, "deviceId", subscriptionBarDevice2.deviceId),
                new ChannelSubscription[]{
                        subscriptionFooDevice2,
                        subscriptionBarDevice2
                }));
        testCases.add(new TestCase(
                "matching none",
                null,
                Param.push(null, "deviceId", "madeup"),
                new ChannelSubscription[]{}));

        testCases.run();
    }
}
