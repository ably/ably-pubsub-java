package io.ably.pubsub.http;

import com.google.gson.JsonParseException;
import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.network.HttpBody;
import io.ably.pubsub.network.FailedConnectionException;
import io.ably.pubsub.network.HttpEngine;
import io.ably.pubsub.network.HttpEngineConfig;
import io.ably.pubsub.network.HttpEngineFactory;
import io.ably.pubsub.network.HttpRequest;
import io.ably.pubsub.network.HttpResponse;
import io.ably.pubsub.http.Auth;
import io.ably.pubsub.transport.Defaults;
import io.ably.pubsub.transport.Hosts;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.ErrorResponse;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.ProxyOptions;
import io.ably.pubsub.util.AgentHeaderCreator;
import io.ably.pubsub.util.Base64Coder;
import io.ably.pubsub.util.ClientOptionsUtils;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.PlatformAgentProvider;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HttpCore performs authenticated HTTP synchronously. Internal; use Http or HttpScheduler instead.
 */
public class HttpCore {

    private static final String TAG = HttpCore.class.getName();

    /*************************
     *     Private state
     *************************/

    static {
        /* if on Android, check version */
        Field androidVersionField = null;
        int androidVersion = 0;
        try {
            androidVersionField = Class.forName("android.os.Build$VERSION").getField("SDK_INT");
            androidVersion = androidVersionField.getInt(androidVersionField);
        } catch (Exception e) {
        }
        if (androidVersionField != null && androidVersion < 8) {
            /* HTTP connection reuse which was buggy pre-froyo */
            System.setProperty("httpCore.keepAlive", "false");
        }
    }

    public final String scheme;
    public final int port;
    final ClientOptions options;
    final Hosts hosts;
    private final Auth auth;
    private final PlatformAgentProvider platformAgentProvider;
    private final HttpEngine engine;
    private HttpAuth proxyAuth;

    /**
     * This field is used for analytics purposes.
     * <p>
     * It holds additional agents that should be added after the Realtime/HTTP client is initialized.
     * - **Static agents** are set in `ClientOptions`.
     * - **Dynamic agents** are added later by higher-level SDKs like Chat or Asset Tracking
     *   and are provided in the `createWrapperSdkProxy` call.
     */
    private Map<String, String> dynamicAgents;

    /*************************
     *     Public API
     *************************/

    public HttpCore(ClientOptions options, Auth auth, PlatformAgentProvider platformAgentProvider) throws AblyException {
        this.options = options;
        this.auth = auth;
        this.platformAgentProvider = platformAgentProvider;
        this.scheme = options.tls ? "https://" : "http://";
        this.port = Defaults.getPort(options);
        this.hosts = new Hosts(options.restHost, Defaults.HOST_REST, options);
        ProxyOptions proxyOptions = options.proxy;
        if (proxyOptions != null) {
            String proxyHost = proxyOptions.host;
            if (proxyHost == null) {
                throw AblyException.fromErrorInfo(new ErrorInfo("Unable to configure proxy without proxy host", 40000, 400));
            }
            int proxyPort = proxyOptions.port;
            if (proxyPort == 0) {
                throw AblyException.fromErrorInfo(new ErrorInfo("Unable to configure proxy without proxy port", 40000, 400));
            }
            String proxyUser = proxyOptions.username;
            if (proxyUser != null) {
                String proxyPassword = proxyOptions.password;
                if (proxyPassword == null) {
                    throw AblyException.fromErrorInfo(new ErrorInfo("Unable to configure proxy without proxy password", 40000, 400));
                }
                proxyAuth = new HttpAuth(proxyUser, proxyPassword, proxyOptions.prefAuthType);
            }
        }
        if (options instanceof DebugOptions && ((DebugOptions) options).httpEngine != null) {
            this.engine = ((DebugOptions) options).httpEngine;
        } else {
            HttpEngineFactory engineFactory = HttpEngineFactory.getFirstAvailable();
            Log.v(TAG, String.format("Using %s HTTP Engine", engineFactory.getEngineType().name()));
            this.engine = engineFactory.create(new HttpEngineConfig(ClientOptionsUtils.convertToProxyConfig(options)));
        }
    }

    private HttpCore(HttpCore underlyingHttpCore, Map<String, String> dynamicAgents) {
        this.options = underlyingHttpCore.options;
        this.auth = underlyingHttpCore.auth;
        this.platformAgentProvider = underlyingHttpCore.platformAgentProvider;
        this.scheme = underlyingHttpCore.scheme;
        this.port = underlyingHttpCore.port;
        this.hosts = underlyingHttpCore.hosts;
        this.proxyAuth = underlyingHttpCore.proxyAuth;
        this.engine = underlyingHttpCore.engine;
        this.dynamicAgents = dynamicAgents;
    }

    /**
     * Make a synchronous HTTP request specified by URL and proxy, retrying if necessary on WWW-Authenticate
     *
     * @param url
     * @param method
     * @param headers
     * @param requestBody
     * @param responseHandler
     * @return
     * @throws AblyException
     */
    public <T> T httpExecuteWithRetry(URL url, String method, Param[] headers, RequestBody requestBody, ResponseHandler<T> responseHandler, boolean requireAblyAuth) throws AblyException {
        boolean renewPending = true, proxyAuthPending = true;
        if (requireAblyAuth) {
            authorize(false);
        }
        while (true) {
            try {
                return httpExecute(url, method, headers, requestBody, true, responseHandler);
            } catch (AuthRequiredException are) {
                if (are.authChallenge != null && requireAblyAuth) {
                    if (are.expired && renewPending) {
                        authorize(true);
                        renewPending = false;
                        continue;
                    }
                }
                if (are.proxyAuthChallenge != null && proxyAuthPending && proxyAuth != null) {
                    proxyAuth.processAuthenticateHeaders(are.proxyAuthChallenge);
                    proxyAuthPending = false;
                    continue;
                }
                throw are;
            }
        }
    }

    /**
     * Gets host for this HTTP client
     *
     * @return
     */
    public String getPreferredHost() {
        return hosts.getPreferredHost();
    }

    /**
     * Sets host for this HTTP client
     *
     * @param host URL string
     */
    public void setPreferredHost(String host) {
        hosts.setPreferredHost(host, false);
    }

    /**
     * Gets host for this HTTP client
     *
     * @return
     */
    public String getPrimaryHost() {
        return hosts.getPrimaryHost();
    }

    /**************************
     *     Internal API
     **************************/

    void authorize(boolean renew) throws AblyException {
        auth.assertAuthorizationHeader(renew);
    }

    /**
     * Make a synchronous HTTP request specified by URL and proxy
     *
     * @param url
     * @param method
     * @param headers
     * @param requestBody
     * @param withCredentials
     * @param responseHandler
     * @return
     * @throws AblyException
     */
    public <T> T httpExecute(URL url, String method, Param[] headers, RequestBody requestBody, boolean withCredentials, ResponseHandler<T> responseHandler) throws AblyException {
        boolean withProxyCredentials = engine.isUsingProxy() && (proxyAuth != null);
        return httpExecute(url, method, headers, requestBody, withCredentials, withProxyCredentials, responseHandler);
    }

    /**
     * Make a synchronous HTTP request with a given HttpURLConnection
     *
     * @param method
     * @param headers
     * @param requestBody
     * @param withCredentials
     * @param responseHandler
     * @return
     * @throws AblyException
     */
    <T> T httpExecute(URL url, String method, Param[] headers, RequestBody requestBody, boolean withCredentials, boolean withProxyCredentials, ResponseHandler<T> responseHandler) throws AblyException {
        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder();
        /* prepare connection */
        requestBuilder
            .url(url)
            .method(method)
            .httpOpenTimeout(options.httpOpenTimeout)
            .httpReadTimeout(options.httpRequestTimeout)
            .body(requestBody != null ? new HttpBody(requestBody.getContentType(), requestBody.getEncoded()) : null);

        Map<String, String> requestHeaders = collectRequestHeaders(url, method, headers, requestBody, withCredentials, withProxyCredentials);
        boolean credentialsIncluded = requestHeaders.containsKey(HttpConstants.Headers.AUTHORIZATION);
        String authHeader = requestHeaders.get(HttpConstants.Headers.AUTHORIZATION);

        requestBuilder.headers(requestHeaders);
        HttpRequest request = requestBuilder.build();

        // Check the logging level to avoid performance hit associated with building the message
        if (Log.level <= Log.VERBOSE && request.getBody() != null && request.getBody().getContent() != null)
            Log.v(TAG, System.lineSeparator() + new String(request.getBody().getContent()));

        /* log raw request details */
        Map<String, List<String>> requestProperties = request.getHeaders();
        // Check the logging level to avoid performance hit associated with building the message
        if (Log.level <= Log.VERBOSE) {
            Log.v(TAG, "HTTP request: " + url + " " + method);
            if (credentialsIncluded)
                Log.v(TAG, "  " + HttpConstants.Headers.AUTHORIZATION + ": " + authHeader);

            for (Map.Entry<String, List<String>> entry : requestProperties.entrySet())
                for (String val : entry.getValue())
                    Log.v(TAG, "  " + entry.getKey() + ": " + val);

            if (requestBody != null) {
                Log.v(TAG, "  " + HttpConstants.Headers.CONTENT_TYPE + ": " + requestBody.getContentType());
                Log.v(TAG, "  " + HttpConstants.Headers.CONTENT_LENGTH + ": " + (requestBody.getEncoded() != null ? requestBody.getEncoded().length : 0));
            }
        }

        DebugOptions.RawHttpListener rawHttpListener = null;
        String id = null;

        if (options instanceof DebugOptions) {
            rawHttpListener = ((DebugOptions) options).httpListener;
            if (rawHttpListener != null) {
                id = String.valueOf(Math.random()).substring(2);
                Response response = rawHttpListener.onRawHttpRequest(id, request, (credentialsIncluded ? authHeader : null), requestProperties, requestBody);
                if (response != null) {
                    return handleResponse(credentialsIncluded, response, responseHandler);
                }
            }
        }


        Response response;

        try {
            response = executeRequest(request);
        } catch (FailedConnectionException exception) {
            throw AblyException.fromThrowable(exception);
        } catch (Exception e) {
            if (e.getCause() instanceof IOException) {
                throw AblyException.fromThrowable(e.getCause());
            } else {
                throw AblyException.fromThrowable(e);
            }
        }

        if (rawHttpListener != null) {
            rawHttpListener.onRawHttpResponse(id, method, response);
        }

        return handleResponse(credentialsIncluded, response, responseHandler);
    }

    private Map<String, String> collectRequestHeaders(URL url, String method, Param[] headers, RequestBody requestBody, boolean withCredentials, boolean withProxyCredentials) throws AblyException {
        Map<String, String> requestHeaders = new HashMap<>();

        String authHeader = Param.getFirst(headers, HttpConstants.Headers.AUTHORIZATION);
        if (authHeader == null && auth != null) {
            authHeader = auth.getAuthorizationHeader();
        }

        if (withCredentials && authHeader != null) {
            requestHeaders.put(HttpConstants.Headers.AUTHORIZATION, authHeader);
        }

        if (withProxyCredentials && proxyAuth.hasChallenge()) {
            byte[] encodedRequestBody = (requestBody != null) ? requestBody.getEncoded() : null;
            String proxyAuthorizationHeader = proxyAuth.getAuthorizationHeader(method, url.getPath(), encodedRequestBody);
            requestHeaders.put(HttpConstants.Headers.PROXY_AUTHORIZATION, proxyAuthorizationHeader);
        }

        boolean acceptSet = false;

        if (headers != null) {
            for (Param header : headers) {
                requestHeaders.put(header.key, header.value);
                if (header.key.equals(HttpConstants.Headers.ACCEPT)) {
                    acceptSet = true;
                }
            }
        }

        if (!acceptSet) {
            requestHeaders.put(HttpConstants.Headers.ACCEPT, HttpConstants.ContentTypes.JSON);
        }

        if (!requestHeaders.containsKey(Defaults.ABLY_PROTOCOL_VERSION_HEADER)) {
            requestHeaders.put(Defaults.ABLY_PROTOCOL_VERSION_HEADER, Defaults.ABLY_PROTOCOL_VERSION); // RSC7e
        }

        Map<String, String> additionalAgents = new HashMap<>();
        if (options.agents != null) additionalAgents.putAll(options.agents);
        if (dynamicAgents != null) additionalAgents.putAll(dynamicAgents);
        requestHeaders.put(Defaults.ABLY_AGENT_HEADER, AgentHeaderCreator.create(additionalAgents, platformAgentProvider));
        if (options.clientId != null)
            requestHeaders.put(Defaults.ABLY_CLIENT_ID_HEADER, Base64Coder.encodeString(options.clientId));

        return requestHeaders;
    }

    /**
     * Handle HTTP response
     *
     * @param credentialsIncluded
     * @param response
     * @param responseHandler
     * @return
     * @throws AblyException
     */
    private <T> T handleResponse(boolean credentialsIncluded, Response response, ResponseHandler<T> responseHandler) throws AblyException {
        if (response.statusCode == 0) {
            return null;
        }

        if (response.statusCode >= 500 && response.statusCode <= 504) {
            ErrorInfo error = ErrorInfo.fromResponseStatus(response.statusLine, response.statusCode);
            throw AblyException.fromErrorInfo(error);
        }

        if (response.statusCode >= 200 && response.statusCode < 300) {
            return (responseHandler != null) ? responseHandler.handleResponse(response, null) : null;
        }

        /* get any in-body error details */
        ErrorInfo error = null;
        if (response.body != null && response.body.length > 0) {
            if (response.contentType != null && response.contentType.contains("msgpack")) {
                try {
                    error = ErrorInfo.fromMsgpackBody(response.body);
                } catch (IOException e) {
                    /* error pages aren't necessarily going to satisfy our Accept criteria ... */
                    System.err.println("Unable to parse msgpack error response");
                }
            } else {
                /* assume json */
                String bodyText = new String(response.body);
                try {
                    ErrorResponse errorResponse = ErrorResponse.fromJSON(bodyText);
                    if (errorResponse != null) {
                        error = errorResponse.error;
                    }
                } catch (JsonParseException jse) {
                    /* error pages aren't necessarily going to satisfy our Accept criteria ... */
                    System.err.println("Error message in unexpected format: " + bodyText);
                }
            }
        }

        /* handle error details in header */
        if (error == null) {
            String errorCodeHeader = response.getHeaderField("X-Ably-ErrorCode");
            String errorMessageHeader = response.getHeaderField("X-Ably-ErrorMessage");
            if (errorCodeHeader != null) {
                try {
                    error = new ErrorInfo(errorMessageHeader, response.statusCode, Integer.parseInt(errorCodeHeader));
                } catch (NumberFormatException e) {
                }
            }
        }

        /* handle www-authenticate */
        if (response.statusCode == 401) {
            boolean stale = (error != null && error.code == 40140);
            List<String> wwwAuthHeaders = response.getHeaderFields(HttpConstants.Headers.WWW_AUTHENTICATE);
            if (wwwAuthHeaders != null && wwwAuthHeaders.size() > 0) {
                Map<HttpAuth.Type, String> headersByType = HttpAuth.sortAuthenticateHeaders(wwwAuthHeaders);
                String tokenHeader = headersByType.get(HttpAuth.Type.X_ABLY_TOKEN);
                if (tokenHeader != null) {
                    stale |= (tokenHeader.indexOf("stale") > -1);
                }
                AuthRequiredException exception = new AuthRequiredException(null, error);
                exception.authChallenge = headersByType;
                if (stale) {
                    exception.expired = true;
                    throw exception;
                }
                if (!credentialsIncluded) {
                    throw exception;
                }
            }
        }

        /* handle proxy-authenticate */
        if (response.statusCode == 407) {
            List<String> proxyAuthHeaders = response.getHeaderFields(HttpConstants.Headers.PROXY_AUTHENTICATE);
            if (proxyAuthHeaders != null && !proxyAuthHeaders.isEmpty()) {
                AuthRequiredException exception = new AuthRequiredException(null, error);
                exception.proxyAuthChallenge = HttpAuth.sortAuthenticateHeaders(proxyAuthHeaders);
                throw exception;
            }
        }

        if (error == null) {
            error = ErrorInfo.fromResponseStatus(response.statusLine, response.statusCode);
        }
        Log.e(TAG, "Error response from server: err = " + error);
        if (responseHandler != null) {
            return responseHandler.handleResponse(response, error);
        }
        throw AblyException.fromErrorInfo(error);
    }

    /**
     * Read the response for an HTTP request
     */
    private Response executeRequest(HttpRequest request) {
        HttpResponse rawResponse = engine.call(request).execute();

        Response response = new Response();
        response.statusCode = rawResponse.getCode();
        response.statusLine = rawResponse.getMessage();

        /* Store all header field names in lower-case to eliminate case insensitivity */
        Log.v(TAG, "HTTP response:");
        Map<String, List<String>> caseSensitiveHeaders = rawResponse.getHeaders();
        response.headers = new HashMap<>(caseSensitiveHeaders.size(), 1f);

        for (Map.Entry<String, List<String>> entry : caseSensitiveHeaders.entrySet()) {
            if (entry.getKey() != null) {
                response.headers.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
                // Check the logging level to avoid performance hit associated with building the message
                if (Log.level <= Log.VERBOSE)
                    for (String val : entry.getValue())
                        Log.v(TAG, entry.getKey() + ": " + val);
            }
        }

        if (response.statusCode == HttpURLConnection.HTTP_NO_CONTENT || rawResponse.getBody() == null) {
            return response;
        }

        response.contentType = rawResponse.getBody().getContentType();
        response.body = rawResponse.getBody().getContent();
        response.contentLength = response.body == null ? 0 : response.body.length;

        if (Log.level <= Log.VERBOSE && response.body != null)
            Log.v(TAG, System.lineSeparator() + new String(response.body));

        return response;
    }

    /**
     * [Internal Method]
     * <p>
     * We use this method to implement proxy Realtime / HTTP clients that add additional agents to the underlying client.
     */
    public HttpCore injectDynamicAgents(Map<String, String> wrapperSDKAgents) {
        return new HttpCore(this, wrapperSDKAgents);
    }

    /**
     * Interface for an entity that supplies an httpCore request body
     */
    public interface RequestBody {
        byte[] getEncoded();

        String getContentType();
    }

    /**
     * Interface for an entity that performs type-specific processing on an httpCore response body
     *
     * @param <T>
     */
    public interface BodyHandler<T> {
        T[] handleResponseBody(String contentType, byte[] body) throws AblyException;
    }

    /**
     * Interface for an entity that performs type-specific processing on an httpCore response
     *
     * @param <T>
     */
    public interface ResponseHandler<T> {
        T handleResponse(Response response, ErrorInfo error) throws AblyException;
    }

    /**
     * A type encapsulating an httpCore response
     */
    public static class Response {
        public int statusCode;
        public String statusLine;
        public Map<String, List<String>> headers;
        public String contentType;
        public int contentLength;
        public byte[] body;

        /**
         * Returns the value of the named header field.
         * <p>
         * If called on a connection that sets the same header multiple times
         * with possibly different values, only the last value is returned.
         *
         * @param name the name of a header field.
         * @return the value of the named header field, or {@code null}
         * if there is no such field in the header.
         */
        public List<String> getHeaderFields(String name) {
            if (headers == null) {
                return null;
            }

            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        public String getHeaderField(String name) {
            if (headers == null) {
                return null;
            }

            List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
            if (values == null || values.isEmpty()) {
                return null;
            }

            return values.get(0);
        }
    }

    /**
     * Exception signifying that an httpCore request failed with a WWW-Authenticate response
     */
    public static class AuthRequiredException extends AblyException {
        private static final long serialVersionUID = 1L;
        public boolean expired;
        public Map<HttpAuth.Type, String> authChallenge;
        public Map<HttpAuth.Type, String> proxyAuthChallenge;

        public AuthRequiredException(Throwable throwable, ErrorInfo reason) {
            super(throwable, reason);
        }
    }
}
