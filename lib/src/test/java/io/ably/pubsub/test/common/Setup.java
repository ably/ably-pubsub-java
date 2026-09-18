package io.ably.pubsub.test.common;

import java.io.IOException;
import java.util.Date;

import com.google.gson.Gson;

import io.ably.pubsub.http.Http;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpScheduler;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.http.HttpHelpers;
import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.test.loader.ArgumentLoader;
import io.ably.pubsub.test.loader.ResourceLoader;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.PresenceMessage;
import io.ably.pubsub.util.Serialisation;
import io.ably.pubsub.debug.DebugOptions;

public class Setup {
    /**
     * The `Setup` class can call `System.exit(int)`.
     * The codes supplied to that method are defined by this enumeration.
     */
    private enum TerminationReason {
        UNABLE_TO_INSTANCE_REST(66601),
        UNABLE_TO_READ_SPEC_FILE(66602),
        UNABLE_TO_CREATE_TEST_APP(66603),
        UNABLE_TO_DELETE_TEST_APP(66604);

        private final int code;

        TerminationReason(final int code) {
            this.code = code;
        }

        public void exit(final Throwable t) {
            System.err.println(this + ": " + t);
            t.printStackTrace();
            System.exit(code);
        }
    }

    public static Object loadJson(String resourceName, Class<? extends Object> expectedType) throws IOException {
        try {
            byte[] jsonBytes = resourceLoader.read(resourceName);
            return gson.fromJson(new String(jsonBytes), expectedType);
        } catch(IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class Key {
        public String keyName;
        public String keySecret;
        public String keyStr;
        public String capability;
        public int status;
    }

    public static class Namespace {
        public String id;
        public boolean persisted;
        public boolean pushEnabled;
        public int status;
        public boolean mutableMessages;
    }

    public static class Connection {
        public String id;
    }

    public static class Channel {
        public String name;
        public PresenceMessage[] presence;
    }

    public static class AppSpec {
        public String id;
        public String appId;
        public String accountId;
        public Key[] keys;
        public Namespace[] namespaces;
        public Connection[] connections;
        public Channel[] channels;
        public boolean tlsOnly;
        public String notes;
    }

    public static class TestParameters {
        public boolean useBinaryProtocol;
        public String name;

        public static TestParameters BINARY = new TestParameters(true, "binary_protocol");
        public static TestParameters TEXT = new TestParameters(false, "text_protocol");

        public TestParameters(boolean useBinaryProtocol, String name) {
            this.useBinaryProtocol = useBinaryProtocol;
            this.name = name;
        }

        public boolean equals(Object obj) {
            TestParameters arg = (TestParameters)obj;
            return arg.useBinaryProtocol == this.useBinaryProtocol;
        }

        public String toString() {
            return name;
        }

        public static TestParameters getDefault() {
            return BINARY;
        }
    }

    public static class TestVars extends AppSpec {
        public String restHost;
        public String realtimeHost;
        public String environment;
        public int port;
        public int tlsPort;
        public boolean tls;

        public DebugOptions createOptions() {
            DebugOptions opts = new DebugOptions();
            fillInOptions(opts, null);
            return opts;
        }

        public DebugOptions createOptions(String key) throws AblyException {
            return createOptions(key, null);
        }

        public DebugOptions createOptions(TestParameters params) throws AblyException {
            DebugOptions opts = new DebugOptions();
            fillInOptions(opts, params);
            return opts;
        }

        public DebugOptions createOptions(String key, TestParameters params) throws AblyException {
            DebugOptions opts = new DebugOptions(key);
            fillInOptions(opts, params);
            return opts;
        }

        public void fillInOptions(ClientOptions opts) {
            fillInOptions(opts, null);
        }

        public void fillInOptions(ClientOptions opts, TestParameters params) {
            if(params == null) { params = TestParameters.getDefault(); }
            opts.useBinaryProtocol = params.useBinaryProtocol;
            opts.restHost = restHost;
            opts.realtimeHost = realtimeHost;
            opts.environment = environment;
            opts.port = port;
            opts.tlsPort = tlsPort;
            opts.tls = tls;
        }
    }

    public static synchronized TestVars getTestVars() {
        return (refCount++ == 0) ? __getTestVars() : testVars;
    }

    private static TestVars __getTestVars() {
        if(testVars == null) {
            host = argumentLoader.getTestArgument("ABLY_REST_HOST");
            environment = argumentLoader.getTestArgument("ABLY_ENV");
            if(environment == null) {
                environment = "sandbox";
            }

            if(host != null) {
                wsHost = argumentLoader.getTestArgument("ABLY_REALTIME_HOST");
                if(wsHost == null)
                    wsHost = host;
            }

            if(argumentLoader.getTestArgument("ABLY_PORT") != null) {
                port = Integer.valueOf(argumentLoader.getTestArgument("ABLY_PORT"));
                tlsPort = Integer.valueOf(argumentLoader.getTestArgument("ABLY_TLS_PORT"));
            } else if((host != null && host.contains("local")) || environment.equals("local")) {
                port = 8080;
                tlsPort = 8081;
            } else {
                /* default to connecting to sandbox or production through load balancer */
                port = 80;
                tlsPort = 443;
            }

            if(ably == null) {
                try {
                    ClientOptions opts = new ClientOptions();
                    /* we need to provide an appId to keep the library happy,
                     * but we are only instancing the library to use the http
                     * convenience methods */
                    opts.key = "none:none";
                    opts.restHost = host;
                    opts.environment = environment;
                    opts.port = port;
                    opts.tlsPort = tlsPort;
                    opts.tls = true;
                    ably = HttpClientFactory.create(opts);
                } catch(AblyException e) {
                    TerminationReason.UNABLE_TO_INSTANCE_REST.exit(e);
                }
            }

            Setup.AppSpec appSpec = null;
            try {
                appSpec = (Setup.AppSpec)loadJson(specFile, Setup.AppSpec.class);
                appSpec.notes = "Test app; created by ably-java realtime tests; date = " + new Date().toString();
            } catch(IOException ioe) {
                TerminationReason.UNABLE_TO_READ_SPEC_FILE.exit(ioe);
            }
            try {
                testVars = HttpHelpers.postSync(ably.http, "/apps", null, null, new HttpUtils.JsonRequestBody(appSpec), new HttpCore.ResponseHandler<TestVars>() {
                    @Override
                    public TestVars handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                        if(error != null) {
                            throw AblyException.fromErrorInfo(error);
                        }

                        TestVars result = (TestVars)Serialisation.gson.fromJson(new String(response.body), TestVars.class);
                        result.restHost = host;
                        result.realtimeHost = wsHost;
                        result.environment = environment;
                        result.port = port;
                        result.tlsPort = tlsPort;
                        result.tls = true;
                        return result;
                    }}, false);
            } catch (AblyException ae) {
                TerminationReason.UNABLE_TO_CREATE_TEST_APP.exit(ae);
            }
        }
        return testVars;
    }

    public static synchronized void clearTestVars() {
        if(--refCount == 0)
            __clearTestVars();
    }

    private static void __clearTestVars() {
        if(testVars != null) {
            try {
                ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
                opts.restHost = host;
                opts.environment = environment;
                opts.port = port;
                opts.tlsPort = tlsPort;
                opts.tls = true;
                ably = HttpClientFactory.create(opts);
                ably.http.request(new Http.Execute<Void>() {
                    @Override
                    public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                        http.del("/apps/" + testVars.appId, HttpUtils.defaultAcceptHeaders(false), null, null, true, callback);
                    }
                }).sync();
            } catch (AblyException ae) {
                TerminationReason.UNABLE_TO_DELETE_TEST_APP.exit(ae);
            }
            testVars = null;
        }
    }

    static {
        argumentLoader = new ArgumentLoader();
        resourceLoader = new ResourceLoader();
    }

    private static ArgumentLoader argumentLoader;
    private static ResourceLoader resourceLoader;
    private static final String specFile = "local/testAppSpec.json";

    private static PubSubHttpClient ably;
    private static String environment;
    private static String host;
    private static String wsHost;
    private static int port;
    private static int tlsPort;

    private static TestVars testVars;
    private static int refCount;
    private static Gson gson = new Gson();
}
