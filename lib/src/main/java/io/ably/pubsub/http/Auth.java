package io.ably.pubsub.http;

import java.net.URL;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import io.ably.pubsub.http.HttpConstants;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpHelpers;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.BaseMessage;
import io.ably.pubsub.types.Capability;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.NonRetriableTokenException;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.Base64Coder;
import io.ably.pubsub.util.Clock;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.Serialisation;
import io.ably.pubsub.util.SystemClock;

/**
 * Token-generation and authentication operations for the Ably API.
 * See the Ably Authentication documentation for details of the
 * authentication methods available.
 * Creates Ably {@link TokenRequest} objects and obtains Ably Tokens from Ably to subsequently issue to less trusted clients.
 */
public class Auth {
    /**
     * Authentication methods
     */
    public enum AuthMethod {
        basic,
        token;
    }

    /**
     * Passes authentication-specific properties in authentication requests to Ably.
     * Properties set using AuthOptions are used instead of the default values set when the client library
     * is instantiated, as opposed to being merged with them.
     */
    public static class AuthOptions {

        /**
         * Called when a new token is required.
         * The role of the callback is to obtain a fresh token, one of: an Ably Token string (in plain text format);
         * a signed {@link TokenRequest}; a {@link TokenDetails} (in JSON format);
         * an <a href="https://ably.com/docs/core-features/authentication#ably-jwt">Ably JWT</a>.
         * See <a href="https://ably.com/docs/realtime/authentication">the authentication documentation</a>
         * for details of the Ably {@link TokenRequest} format and associated API calls.
         * <p>
         * This callback is invoked on a background thread.
         * <p>
         * Spec:
         * RSA4a, RSA4, TO3j5, AO2b
         */
        public TokenCallback authCallback;

        /**
         * A URL that the library may use to obtain a token string (in plain text format), or a signed {@link TokenRequest}
         * or {@link TokenDetails} (in JSON format) from.
         * <p>
         * Spec:
         * RSA4a, RSA4, RSA8c, TO3j6, AO2c
         */
        public String authUrl;

        /**
         * The HTTP verb to use for any request made to the authUrl, either GET or POST. The default value is GET.
         * <p>
         * Spec:
         * RSA8c, TO3j7, AO2d
         */
        public String authMethod;

        /**
         * The full API key string, as obtained from the <a href="https://ably.com/dashboard">Ably dashboard</a>.
         * Use this option if you wish to use Basic authentication,
         * or wish to be able to issue Ably Tokens without needing to defer to a separate entity to sign Ably {@link TokenRequest}.
         * Read more about <a href="https://ably.com/docs/core-features/authentication#basic-authentication">Basic authentication</a>.
         * <p>
         * Spec:
         * RSA11, RSA14, TO3j1, AO2a
         */
        public String key;

        /**
         * An authenticated token.
         * This can either be a {@link TokenDetails} object, a {@link TokenRequest} object, or token string
         * (obtained from the token property of a {@link TokenDetails} component of an Ably {@link TokenRequest} response, or a
         * JSON Web Token satisfying the
         * <a href="https://ably.com/docs/core-features/authentication#ably-jwt">Ably requirements for JWTs</a>).
         * This option is mostly useful for testing: since tokens are short-lived,
         * in production you almost always want to use an authentication method that enables the
         * client library to renew the token automatically when the previous one expires, such as authUrl or authCallback.
         * Read more about Token authentication.
         * <p>
         * Spec:
         * RSA4a, RSA4, TO3j2
         */
        public String token;

        /**
         * An authenticated {@link TokenDetails} object (most commonly obtained from an Ably Token Request response).
         * This option is mostly useful for testing: since tokens are short-lived,
         * in production you almost always want to use an authentication method that enables the
         * client library to renew the token automatically when the previous one expires, such as authUrl or authCallback.
         * Use this option if you wish to use Token authentication.
         * Read more about <a href="https://ably.com/docs/core-features/authentication#token-authentication">Token authentication</a>.
         * <p>
         * Spec:
         * RSA4a, RSA4, TO3j2
         */
        public TokenDetails tokenDetails;

        /**
         * A set of key-value pair headers to be added to any request made to the authUrl.
         * Useful when an application requires these to be added to validate the request or implement the response.
         * If the authHeaders object contains an authorization key, then withCredentials is set on the XHR request.
         * <p>
         * Spec:
         * RSA8c3, TO3j8, AO2e
         */
        public Param[] authHeaders;

        /**
         * A set of key-value pair params to be added to any request made to the authUrl.
         * When the authMethod is GET, query params are added to the URL, whereas when authMethod is POST,
         * the params are sent as URL encoded form data.
         * Useful when an application requires these to be added to validate the request or implement the response.
         * <p>
         * Spec:
         * RSA8c3, RSA8c1, TO3j9, AO2f
         */
        public Param[] authParams;

        /**
         * If true, the library queries the Ably servers for the current time when issuing {@link TokenRequest}
         * instead of relying on a locally-available time of day.
         * Knowing the time accurately is needed to create valid signed Ably {@link TokenRequest},
         * so this option is useful for library instances on auth servers where for some reason the server clock
         * cannot be kept synchronized through normal means, such as an <a href="https://en.wikipedia.org/wiki/Ntpd">NTP daemon</a>.
         * The server is queried for the current time once per client library instance (which stores the offset from the local clock),
         * so if using this option you should avoid instancing a new version of the library for each request.
         * The default is false.
         * <p>
         * Spec:
         * RSA9d, TO3j10, AO2a
         */
        public boolean queryTime;

        /**
         * When true, forces token authentication to be used by the library.
         * If a clientId is not specified in the {@link ClientOptions} or {@link TokenParams},
         * then the Ably Token issued is anonymous.
         * <p>
         * Spec:
         * RSA4, RSA14, TO3j4
         */
        public boolean useTokenAuth;

        /**
         * Default constructor
         */
        public AuthOptions() {}

        /**
         * Convenience constructor, to create an AuthOptions based
         * on the key string obtained from the application dashboard.
         * @param key the full key string as obtained from the dashboard
         * @throws AblyException
         */
        public AuthOptions(String key) throws AblyException {
            if (key == null) {
                throw AblyException.fromErrorInfo(new ErrorInfo("key string cannot be null", 40000, 400));
            }
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Key string cannot be empty");
            }
            if(key.indexOf(':') > -1)
                this.key = key;
            else
                this.token = key;
        }

        /**
         * Stores the AuthOptions arguments as defaults for subsequent authorizations
         * with the exception of the attributes {@link AuthOptions#timestamp()} and
         * {@link AuthOptions#queryTime}
         * <p>
         * Spec: RSA10g
         * </p>
         */
        private AuthOptions storedValues() {
            AuthOptions result = new AuthOptions();
            result.key = this.key;
            result.authUrl = this.authUrl;
            result.authMethod = this.authMethod;
            result.authParams = this.authParams;
            result.authHeaders = this.authHeaders;
            result.token = this.token;
            result.tokenDetails = this.tokenDetails;
            result.authCallback = this.authCallback;
            return result;
        }

        /**
         * Create a new copy of object
         *
         * @return copied object
         */
        private AuthOptions copy() {
            AuthOptions result = new AuthOptions();
            result.key = this.key;
            result.authUrl = this.authUrl;
            result.authMethod = this.authMethod;
            result.authParams = this.authParams;
            result.authHeaders = this.authHeaders;
            result.token = this.token;
            result.tokenDetails = this.tokenDetails;
            result.authCallback = this.authCallback;
            result.queryTime = this.queryTime;
            return result;
        }
    }

    /**
     * Contains an Ably Token and its associated metadata.
     */
    public static class TokenDetails {

        /**
         * The <a href="https://ably.com/docs/core-features/authentication#ably-tokens">Ably Token</a> itself.
         * <p>
         * A typical Ably Token string appears with the form xVLyHw.A-pwh7wicf3afTfgiw4k2Ku33kcnSA7z6y8FjuYpe3QaNRTEo4.
         * <p>
         * Spec: TD2
         */
        public String token;

        /**
         * The timestamp at which this token expires as milliseconds since the Unix epoch.
         * <p>
         * Spec: TD3
         */
        public long expires;

        /**
         * The timestamp at which this token was issued as milliseconds since the Unix epoch.
         * <p>
         * Spec: TD4
         */
        public long issued;

        /**
         * The capabilities associated with this Ably Token.
         * The capabilities value is a JSON-encoded representation of the resource paths and associated operations.
         * Read more about capabilities in the
         * <a href="https://ably.com/docs/core-features/authentication/#capabilities-explained">capabilities docs</a>.
         * <p>
         * Spec: TD5
         */
        public String capability;

        /**
         * The client ID, if any, bound to this Ably Token.
         * If a client ID is included, then the Ably Token authenticates its bearer as that client ID,
         * and the Ably Token may only be used to perform operations on behalf of that client ID.
         * The client is then considered to be an
         * <a href="https://ably.com/docs/core-features/authentication#identified-clients">identified client</a>.
         * <p>
         * Spec: TD6
         */
        public String clientId;

        public TokenDetails() {}
        public TokenDetails(String token) { this.token = token; }

        /**
         * A static factory method to create a TokenDetails object from a deserialized
         * TokenDetails-like object or a JSON stringified TokenDetails object.
         * This method is provided to minimize bugs as a result of differing types by platform for fields such as timestamp or ttl.
         * For example, in Ruby ttl in the TokenDetails object is exposed in seconds as that is idiomatic for the language,
         * yet when serialized to JSON using to_json it is automatically converted to the Ably standard which is milliseconds.
         * By using the fromJson() method when constructing a TokenDetails object,
         * Ably ensures that all fields are consistently serialized and deserialized across platforms.
         * <p>
         * Spec: TD7
         * @param json A deserialized TokenDetails-like object or a JSON stringified TokenDetails object.
         * @return An Ably authentication token.
         */
        public static TokenDetails fromJson(String json) {
            return Serialisation.gson.fromJson(json, TokenDetails.class);
        }

        /**
         * A static factory method to create a TokenDetails object from a deserialized
         * TokenDetails-like object or a JSON stringified TokenDetails object.
         * This method is provided to minimize bugs as a result of differing types by platform for fields such as timestamp or ttl.
         * For example, in Ruby ttl in the TokenDetails object is exposed in seconds as that is idiomatic for the language,
         * yet when serialized to JSON using to_json it is automatically converted to the Ably standard which is milliseconds.
         * By using the fromJson() method when constructing a TokenDetails object,
         * Ably ensures that all fields are consistently serialized and deserialized across platforms.
         * <p>
         * Spec: TD7
         * @param json A deserialized TokenDetails-like object or a JSON stringified TokenDetails object.
         * @return An Ably authentication token.
         */
        public static TokenDetails fromJsonElement(JsonObject json) {
            return Serialisation.gson.fromJson(json, TokenDetails.class);
        }

        /**
         * Convert a TokenDetails into a JSON object.
         */
        public JsonObject asJsonElement() {
            return (JsonObject)Serialisation.gson.toJsonTree(this);
        }

        /**
         * Convert a TokenDetails into a JSON string.
         */
        public String asJson() {
            return asJsonElement().toString();
        }

        /**
         * Check equality of a TokenDetails
         * @param obj
         */
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TokenDetails)) return false;

            TokenDetails details = (TokenDetails)obj;
            return equalNullableStrings(this.token, details.token) &
                    equalNullableStrings(this.capability, details.capability) &
                    equalNullableStrings(this.clientId, details.clientId) &
                    (this.issued == details.issued) &
                    (this.expires == details.expires);
        }

        @Override
        public int hashCode() {
            return Objects.hash(token, capability, clientId, issued, expires);
        }

    }

    /**
     * Defines the properties of an Ably Token.
     */
    public static class TokenParams {

        /**
         * Requested time to live for the token in milliseconds. The default is 60 minutes.
         * <p>
         * Spec: RSA9e, TK2a
         */
        public long ttl;

        /**
         * The capabilities associated with this Ably Token.
         * The capabilities value is a JSON-encoded representation of the resource paths and associated operations.
         * Read more about capabilities in the
         * <a href="https://ably.com/docs/core-features/authentication/#capabilities-explained">capabilities docs</a>.
         * <p>
         * Spec: RSA9f, TK2b
         */
        public String capability;

        /**
         * A client ID, used for identifying this client when publishing messages or for presence purposes.
         * The clientId can be any non-empty string, except it cannot contain a *.
         * This option is primarily intended to be used in situations where the library is instantiated with a key.
         * Note that a clientId may also be implicit in a token used to instantiate the library.
         * An error is raised if a clientId specified here conflicts with the clientId implicit in the token.
         * Find out more about <a href="https://ably.com/docs/core-features/authentication#identified-clients">identified clients</a>.
         * <p>
         * Spec: TK2c
         */
        public String clientId;

        /**
         * The timestamp of this request as milliseconds since the Unix epoch.
         * Timestamps, in conjunction with the nonce, are used to prevent requests from being replayed.
         * timestamp is a "one-time" value, and is valid in a request,
         * but is not validly a member of any default token params such as ClientOptions.defaultTokenParams.
         * <p>
         * Spec: RSA9d, Tk2d
         */
        public long timestamp;

        /**
         * Internal; convert a TokenParams to a collection of Params
         * @return
         */
        public Map<String, Param> asMap() {
            Map<String, Param> params = new HashMap<String, Param>();
            if(ttl > 0) params.put("ttl", new Param("ttl", String.valueOf(ttl)));
            if(capability != null) params.put("capability", new Param("capability", capability));
            if(clientId != null) params.put("clientId", new Param("clientId", clientId));
            if(timestamp > 0) params.put("timestamp", new Param("timestamp", String.valueOf(timestamp)));
            return params;
        }

        /**
         * Check equality of a TokenParams
         * @param obj
         */
        @Override
        public boolean equals(Object obj) {
            TokenParams params = (TokenParams)obj;
            return (this.ttl == params.ttl) &
                    equalNullableStrings(this.capability, params.capability) &
                    equalNullableStrings(this.clientId, params.clientId) &
                    (this.timestamp == params.timestamp);
        }

        /**
         * Stores the TokenParams arguments as defaults for subsequent authorizations
         * with the exception of the attributes {@link TokenParams#timestamp}
         * <p>
         * Spec: RSA10g
         * </p>
         */
        private TokenParams storedValues() {
            TokenParams result = new TokenParams();
            result.ttl = this.ttl;
            result.capability = this.capability;
            result.clientId = this.clientId;
            return result;
        }

        /**
         * Create a new copy of object
         *
         * @return copied object
         */
        private TokenParams copy() {
            TokenParams result = new TokenParams();
            result.ttl = this.ttl;
            result.capability = this.capability;
            result.clientId = this.clientId;
            result.timestamp = this.timestamp;
            return result;
        }
    }

    /**
     * Contains the properties of a request for a token to Ably.
     * Tokens are generated using {@link Auth#requestToken}.
     */
    public static class TokenRequest extends TokenParams {

        public TokenRequest() {}

        public TokenRequest(TokenParams params) {
            this.ttl = params.ttl;
            this.capability = params.capability;
            this.clientId = params.clientId;
            this.timestamp = params.timestamp;
        }

        /**
         * The name of the key against which this request is made. The key name is public, whereas the key secret is private.
         * <p>
         * Spec: TE2
         */
        public String keyName;

        /**
         * A cryptographically secure random string of at least 16 characters, used to ensure the TokenRequest cannot be reused.
         * <p>
         * Spec: TE2
         */
        public String nonce;

        /**
         * The Message Authentication Code for this request.
         * <p>
         * Spec: TE2
         */
        public String mac;

        /**
         * A static factory method to create a TokenRequest object from a deserialized TokenRequest-like object
         * or a JSON stringified TokenRequest object.
         * This method is provided to minimize bugs as a result of differing types by platform for fields such as timestamp or ttl.
         * For example, in Ruby ttl in the TokenRequest object is exposed in seconds as that is idiomatic for the language,
         * yet when serialized to JSON using to_json it is automatically converted to the Ably standard which is milliseconds.
         * By using the fromJson() method when constructing a TokenRequest object,
         * Ably ensures that all fields are consistently serialized and deserialized across platforms.
         * <p>
         * Spec: TE6
         * @param json A deserialized TokenRequest-like object or a JSON stringified TokenRequest object to create a TokenRequest.
         * @return An Ably token request object.
         */
        public static TokenRequest fromJsonElement(JsonObject json) {
            return Serialisation.gson.fromJson(json, TokenRequest.class);
        }

        /**
         * A static factory method to create a TokenRequest object from a deserialized TokenRequest-like object
         * or a JSON stringified TokenRequest object.
         * This method is provided to minimize bugs as a result of differing types by platform for fields such as timestamp or ttl.
         * For example, in Ruby ttl in the TokenRequest object is exposed in seconds as that is idiomatic for the language,
         * yet when serialized to JSON using to_json it is automatically converted to the Ably standard which is milliseconds.
         * By using the fromJson() method when constructing a TokenRequest object,
         * Ably ensures that all fields are consistently serialized and deserialized across platforms.
         * <p>
         * Spec: TE6
         * @param json A deserialized TokenRequest-like object or a JSON stringified TokenRequest object to create a TokenRequest.
         * @return An Ably token request object.
         */
        public static TokenRequest fromJson(String json) {
            return Serialisation.gson.fromJson(json, TokenRequest.class);
        }

        /**
         * Convert a TokenParams into a JSON object.
         */
        public JsonObject asJsonElement() {
            JsonObject o = (JsonObject)Serialisation.gson.toJsonTree(this);
            if (this.ttl == 0) {
                o.remove("ttl");
            }
            if (this.capability != null && this.capability.isEmpty()) {
                o.remove("capability");
            }
            return o;
        }

        /**
         * Convert a TokenParams into a JSON string.
         */
        public String asJson() {
            return asJsonElement().toString();
        }

        /**
         * Check equality of a TokenRequest
         * @param obj
         */
        @Override
        public boolean equals(Object obj) {
            TokenRequest request = (TokenRequest)obj;
            return super.equals(obj) &
                    equalNullableStrings(this.keyName, request.keyName) &
                    equalNullableStrings(this.nonce, request.nonce) &
                    equalNullableStrings(this.mac, request.mac);
        }
    }

    /**
     * An interface providing update result for onAuthUpdated
     */
    public interface AuthUpdateResult{
        /**
         * Signals an update from {@link io.ably.pubsub.transport.ConnectionManager#onAuthUpdatedAsync(String, AuthUpdateResult)}
         * @param success If Update was successful
         * @param errorInfo optional errorInfo if update wasn't successful
         */
        void onUpdate(boolean success, ErrorInfo errorInfo);
    }

    /**
     * An interface providing completion callbackk for renewAuth
     */
    public interface RenewAuthResult {
        /**
         * Signals completion of {@link Auth#renewAuth(RenewAuthResult)}
         * @param success if token renewal was successful. Please note that success for this operation means that
         *                other operations relating to this also succeeded.
         * @param tokenDetails New token details. Please note that this value can exist regardless of value of
         *                     success state.
         * @param errorInfo Error details if operation is completed with error.
         */
        void onCompletion(boolean success,TokenDetails tokenDetails, ErrorInfo errorInfo);
    }

    /**
     * An interface implemented by a callback that provides either tokens,
     * or signed token requests, in response to a request with given token params.
     */
    public interface TokenCallback {
        Object getTokenRequest(TokenParams params) throws AblyException;
    }

    /**
     * A client ID, used for identifying this client when publishing messages or for presence purposes.
     * The clientId can be any non-empty string, except it cannot contain a *.
     * This option is primarily intended to be used in situations where the library is instantiated with a key.
     * Note that a clientId may also be implicit in a token used to instantiate the library.
     * An error is raised if a clientId specified here conflicts with the clientId implicit in the token.
     * Find out more about <a href="https://ably.com/docs/core-features/authentication#identified-clients">identified clients</a>.
     * <p>
     * Spec: RSA7, RSC17, RSA12
     */
    public String clientId;

    /**
     * Instructs the library to get a new token immediately.
     * When using the realtime client, it upgrades the current realtime connection to use the new token,
     * or if not connected, initiates a connection to Ably, once the new token has been obtained.
     * Also stores any {@link TokenParams} and {@link AuthOptions} passed in as the new defaults,
     * to be used for all subsequent implicit or explicit token requests.
     * Any {@link TokenParams} and {@link AuthOptions} objects passed in entirely replace,
     * as opposed to being merged with, the current client library saved values.
     * <p>
     * Spec: RSA10
     *
     * @param params A {@link TokenParams} object.
     * @param options An {@link AuthOptions} object.
     * @return A {@link TokenDetails} object.
     * @throws AblyException
     */
    public TokenDetails authorize(TokenParams params, AuthOptions options) throws AblyException {
        /* Spec: RSA10g */
        if (options != null)
            this.authOptions = options.storedValues();
        if (params != null)
            this.tokenParams = params.storedValues();

        /* Spec: RSA10j */
        options = (options == null) ? this.authOptions : options.copy();
        params = (params == null) ? this.tokenParams : params.copy();

        /* RSA10e (as clarified in PR https://github.com/ably/docs/pull/186 )
         * Use supplied token or tokenDetails if any. */
        if (authOptions.token != null) {
            authOptions.tokenDetails = new TokenDetails(authOptions.token);
        }
        TokenDetails tokenDetails;
        if(authOptions.tokenDetails != null) {
            tokenDetails = authOptions.tokenDetails;
            setTokenDetails(tokenDetails);
        } else {
            try {
                tokenDetails = assertValidToken(params, options, true);
            } catch (AblyException e) {
                /* Give RealtimeClient a chance to update its state and emit an event according to RSA4c */
                ably.onAuthError(e.errorInfo);
                throw e;
            }
        }
        ably.onAuthUpdated(tokenDetails.token, true);
        return tokenDetails;
    }

    /**
     * Calls the requestToken REST API endpoint to obtain an Ably Token
     * according to the specified {@link TokenParams} and {@link AuthOptions}.
     * Both {@link TokenParams} and {@link AuthOptions} are optional.
     * When omitted or null, the default token parameters and authentication options for the client library are used,
     * as specified in the {@link ClientOptions} when the client library was instantiated,
     * or later updated with an explicit authorize request. Values passed in are used instead of,
     * rather than being merged with, the default values.
     * To understand why an Ably {@link TokenRequest} may be issued to clients in favor of a token,
     * see <a href="https://ably.com/docs/core-features/authentication/#token-authentication">Token Authentication explained</a>.
     * <p>
     * Spec: RSA8e
     * @param params : A {@link TokenParams} object.
     * @param tokenOptions : An {@link AuthOptions} object.
     * @return A {@link TokenDetails} object.
     * @throws AblyException
     */
    public TokenDetails requestToken(TokenParams params, AuthOptions tokenOptions) throws AblyException {
        /* Spec: RSA8e */
        tokenOptions = (tokenOptions == null) ? this.authOptions : tokenOptions.copy();
        params = (params == null) ? this.tokenParams : params.copy();

        /* Spec: RSA7d */
        if(params.clientId == null) {
            params.clientId = ably.options.clientId;
        }
        params.capability = Capability.c14n(params.capability);

        /* get the signed token request */
        TokenRequest signedTokenRequest;
        if(tokenOptions.authCallback != null) {
            Log.i("Auth.requestToken()", "using token auth with auth_callback");
            try {
                /* the callback can return either a signed token request, or a TokenDetails */
                Object authCallbackResponse = tokenOptions.authCallback.getTokenRequest(params);
                if(authCallbackResponse instanceof String)
                    return new TokenDetails((String)authCallbackResponse);
                if(authCallbackResponse instanceof TokenDetails)
                    return (TokenDetails)authCallbackResponse;
                if(authCallbackResponse instanceof TokenRequest)
                    signedTokenRequest = (TokenRequest)authCallbackResponse;
                else
                    throw AblyException.fromErrorInfo(new ErrorInfo("Invalid authCallback response", 400, 40000));
            } catch (final Exception e) {
                final boolean isTokenExceptionNonRetriable = e instanceof NonRetriableTokenException;
                final boolean isAblyExceptionNonRetriable = e instanceof AblyException && ((AblyException) e).errorInfo.statusCode == 403;
                final boolean shouldNotRetryAuthOperation = isTokenExceptionNonRetriable || isAblyExceptionNonRetriable;
                final int statusCode = shouldNotRetryAuthOperation ? 403 : 401; // RSA4c & RSA4d
                throw AblyException.fromErrorInfo(e, new ErrorInfo("authCallback failed with an exception", statusCode, 80019));
            }
        } else if(tokenOptions.authUrl != null) {
            Log.i("Auth.requestToken()", "using token auth with auth_url");

            /* the auth request can return either a signed token request as a TokenParams, or a TokenDetails */
            Object authUrlResponse = null;
            try {
                HttpCore.ResponseHandler<Object> responseHandler = new HttpCore.ResponseHandler<Object>() {
                    @Override
                    public Object handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                        if(error != null) {
                            throw AblyException.fromErrorInfo(error);
                        }
                        try {
                            String contentType = response.contentType;
                            byte[] body = response.body;
                            if(body == null || body.length == 0) {
                                return null;
                            }
                            if(contentType != null) {
                                if(contentType.startsWith("text/plain") || contentType.startsWith("application/jwt")) {
                                    /* assumed to be token string */
                                    String token = new String(body);
                                    return new TokenDetails(token);
                                }
                                if(!contentType.startsWith("application/json")) {
                                    throw AblyException.fromErrorInfo(new ErrorInfo("Unacceptable content type from auth callback", 406, 40170));
                                }
                            }
                            /* if not explicitly indicated, we will just assume it's JSON */
                            JsonElement json = Serialisation.gsonParser.parse(new String(body));
                            if(!(json instanceof JsonObject)) {
                                throw AblyException.fromErrorInfo(new ErrorInfo("Unexpected response type from auth callback", 406, 40170));
                            }
                            JsonObject jsonObject = (JsonObject)json;
                            if(jsonObject.has("issued")) {
                                /* we assume this is a token details */
                                return TokenDetails.fromJsonElement(jsonObject);
                            } else {
                                /* otherwise it's a signed token request */
                                return TokenRequest.fromJsonElement(jsonObject);
                            }
                        } catch(JsonParseException e) {
                            throw AblyException.fromErrorInfo(new ErrorInfo("Unable to parse response from auth callback", 406, 40170));
                        }
                    }
                };

                /* append all relevant params to token params */
                Map<String, Param> urlParams = null;
                URL authUrl = HttpUtils.parseUrl(authOptions.authUrl);
                final String urlWithoutQueryParams = HttpUtils.urlWithQueryStringRemoved(authOptions.authUrl);
                String queryString = authUrl.getQuery();
                if(queryString != null && !queryString.isEmpty()) {
                    urlParams = HttpUtils.decodeParams(queryString);
                }
                Map<String, Param> tokenParams = params.asMap();
                if(tokenOptions.authParams != null) {
                    for(Param p : tokenOptions.authParams) {
                        /* (RSA8c2) TokenParams take precedence over any configured
                         * authParams when a name conflict occurs */
                        if(!tokenParams.containsKey(p.key)) {
                            tokenParams.put(p.key, p);
                        }
                    }
                }
                if (HttpConstants.Methods.POST.equals(tokenOptions.authMethod)) {
                    authUrlResponse = HttpHelpers.postUri(ably.httpCore, urlWithoutQueryParams, tokenOptions.authHeaders,
                        HttpUtils.flattenParams(urlParams), HttpUtils.flattenParams(tokenParams), responseHandler);
                } else {
                    Map<String, Param> requestParams = (urlParams != null) ? HttpUtils.mergeParams(urlParams, tokenParams) : tokenParams;
                    authUrlResponse = HttpHelpers.getUri(ably.httpCore, urlWithoutQueryParams, tokenOptions.authHeaders,
                        HttpUtils.flattenParams(requestParams), responseHandler);
                }
            } catch(AblyException e) {
                throw AblyException.fromErrorInfo(e, new ErrorInfo("authUrl failed with an exception", e.errorInfo.statusCode, 80019));
            }
            if(authUrlResponse == null) {
                throw AblyException.fromErrorInfo(null, new ErrorInfo("Empty response received from authUrl", 401, 80019));
            }
            if(authUrlResponse instanceof TokenDetails) {
                /* we're done */
                return (TokenDetails)authUrlResponse;
            }
            /* otherwise it's a signed token request */
            signedTokenRequest = (TokenRequest)authUrlResponse;
        } else if(tokenOptions.key != null) {
            Log.i("Auth.requestToken()", "using token auth with client-side signing");
            signedTokenRequest = createTokenRequest(params, tokenOptions);
        } else {
            throw AblyException.fromErrorInfo(new ErrorInfo("Auth.requestToken(): options must include valid authentication parameters", 400, 40106));
        }

        String tokenPath = "/keys/" + signedTokenRequest.keyName + "/requestToken";
        return HttpHelpers.postSync(ably.http, tokenPath, null, null, new HttpUtils.JsonRequestBody(signedTokenRequest.asJsonElement().toString()), new HttpCore.ResponseHandler<TokenDetails>() {
            @Override
            public TokenDetails handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                if(error != null) {
                    throw AblyException.fromErrorInfo(error);
                }
                try {
                    String jsonText = new String(response.body);
                    JsonObject json = (JsonObject)Serialisation.gsonParser.parse(jsonText);
                    return TokenDetails.fromJsonElement(json);
                } catch(JsonParseException e) {
                    throw AblyException.fromThrowable(e);
                }
            }
        }, false);
    }

    /**
     * Creates and signs an Ably {@link TokenRequest} based on the specified
     * (or if none specified, the client library stored) {@link TokenParams} and {@link AuthOptions}.
     * Note this can only be used when the API key value is available locally.
     * Otherwise, the Ably {@link TokenRequest} must be obtained from the key owner.
     * Use this to generate an Ably {@link TokenRequest} in order to implement an
     * Ably Token request callback for use by other clients. Both {@link TokenParams} and {@link AuthOptions} are optional.
     * When omitted or null, the default token parameters and authentication options for the client library are used,
     * as specified in the {@link ClientOptions} when the client library was instantiated,
     * or later updated with an explicit authorize request.
     * Values passed in are used instead of, rather than being merged with, the default values.
     * To understand why an Ably {@link TokenRequest} may be issued to clients in favor of a token,
     * see <a href="https://ably.com/docs/core-features/authentication/#token-authentication">Token Authentication explained</a>.
     * <p>
     * Spec: RSA9
     * @param params : A {@link TokenParams} object.
     * @param options : An {@link AuthOptions} object.
     * @return A {@link TokenRequest} object.
     * @throws AblyException
     */
    public TokenRequest createTokenRequest(TokenParams params, AuthOptions options) throws AblyException {
        /* Spec: RSA9h */
        options = (options == null) ? this.authOptions : options.copy();
        params = (params == null) ? this.tokenParams : params.copy();

        params.capability = Capability.c14n(params.capability);
        TokenRequest request = new TokenRequest(params);

        String key = options.key;
        if(key == null)
            throw AblyException.fromErrorInfo(new ErrorInfo("No key specified", 401, 40101));

        String[] keyParts = key.split(":");
        if(keyParts.length != 2)
            throw AblyException.fromErrorInfo(new ErrorInfo("Invalid key specified", 401, 40101));

        String keyName = keyParts[0], keySecret = keyParts[1];
        if(request.keyName == null)
            request.keyName = keyName;
        else if(!request.keyName.equals(keyName))
            throw AblyException.fromErrorInfo(new ErrorInfo("Incompatible keys specified", 401, 40102));

        /* expires */
        String ttlText = (request.ttl == 0) ? "" : String.valueOf(request.ttl);

        /* capability */
        String capabilityText = (request.capability == null) ? "" : request.capability;

        /* clientId */
        if (request.clientId == null) request.clientId = ably.options.clientId;
        String clientIdText = (request.clientId == null) ? "" : request.clientId;

        /* timestamp */
        if(request.timestamp == 0) {
            if(options.queryTime) {
                long oldNanoTimeDelta = nanoTimeDelta;
                long currentNanoTimeDelta = clock.currentTimeMillis() - clock.nanoTime()/(1000*1000);

                if (timeDelta != Long.MAX_VALUE) {
                    /* system time changed by more than 500ms since last time? */
                    if(Math.abs(oldNanoTimeDelta - currentNanoTimeDelta) > 500)
                        timeDelta = Long.MAX_VALUE;
                }

                if (timeDelta != Long.MAX_VALUE) {
                    request.timestamp = timestamp() + timeDelta;
                    nanoTimeDelta = currentNanoTimeDelta;
                } else {
                    request.timestamp = ably.time();
                    timeDelta = request.timestamp - timestamp();
                }
            }
            else {
                request.timestamp = timestamp();
            }
        }

        /* nonce */
        request.nonce = random();

        String signText
            = request.keyName + '\n'
            + ttlText + '\n'
            + capabilityText + '\n'
            + clientIdText + '\n'
            + request.timestamp + '\n'
            + request.nonce + '\n';

        request.mac = hmac(signText, keySecret);

        Log.i("Auth.getTokenRequest()", "generated signed request");
        return request;
    }

    /**
     * Get the authentication method for this library instance.
     * @return
     */
    public AuthMethod getAuthMethod() {
        return method;
    }

    /**
     * Get the credentials for HTTP basic auth, if available.
     * @return
     */
    public String getBasicCredentials() {
        return (method == AuthMethod.basic) ? basicCredentials : null;
    }

    /**
     * Get query params representing the current authentication method and credentials.
     * @return
     * @throws AblyException
     */
    public Param[] getAuthParams() throws AblyException {
        Param[] params = null;
        switch(method) {
        case basic:
            params = new Param[]{new Param("key", authOptions.key) };
            break;
        case token:
            assertValidToken();
            params = new Param[]{new Param("accessToken", getTokenDetails().token) };
            break;
        }
        return params;
    }

    /**
     * Get (a copy of) auth options currently set in this Auth.
     */
    public AuthOptions getAuthOptions() {
        return authOptions.copy();
    }

    /**
     * Obtains a new token even if we already have an apparently valid one, and notifies the
     * client that the credentials changed. The internal half of {@link #renewAuth}, for the
     * callers that have no completion handler to hand it.
     */
    private TokenDetails renewToken() throws AblyException {
        TokenDetails tokenDetails = assertValidToken(this.tokenParams, this.authOptions, true);
        ably.onAuthUpdated(tokenDetails.token, false);
        return tokenDetails;
    }

    /**
     * Renew auth credentials.
     * Will obtain a new token, even if we already have an apparently valid one.
     * Authorization will use the parameters supplied on construction.
     * @param result Asynchronous result the completion
     * Please note that completion callback  {@link RenewAuthResult#onCompletion(boolean, TokenDetails, ErrorInfo)}
     *              is called on a background thread.
     */
    public void renewAuth(RenewAuthResult result) throws AblyException {
        final TokenDetails tokenDetails = assertValidToken(this.tokenParams, this.authOptions, true);

        ably.onAuthUpdatedAsync(tokenDetails.token, (success, errorInfo) -> result.onCompletion(success,tokenDetails,errorInfo));
    }

    public void onAuthError(ErrorInfo err) {
        /* we're only interested in token expiry errors */
        if(err.code >= 40140 && err.code < 40150)
            clearTokenDetails();
    }

    public long timestamp() { return clock.currentTimeMillis(); }

    /********************
     * internal
     ********************/

    /**
     * Private constructor.
     * @param ably
     * @param options
     * @throws AblyException
     */
    Auth(PubSubBase ably, ClientOptions options) throws AblyException {
        this.ably = ably;
        this.clock = SystemClock.clockFrom(options);
        this.nanoTimeDelta = clock.currentTimeMillis() - clock.nanoTime()/(1000*1000);
        authOptions = options;
        tokenParams = options.defaultTokenParams != null ?
                options.defaultTokenParams : new TokenParams();

        /* set clientId (spec Rsa7b1) */
        if(options.clientId != null) {
            if(options.clientId.equals(WILDCARD_CLIENTID)) {
                /* RSA7c */
                throw AblyException.fromErrorInfo(new ErrorInfo("Disallowed wildcard clientId in ClientOptions", 400, 40000));
            }
            /* RSC17 */
            setClientId(options.clientId);
            /* RSA7a4 */
            tokenParams.clientId = options.clientId;
        }

        /* decide default auth method (spec: RSA4) */
        if(authOptions.key != null) {
            if(!options.useTokenAuth &&
                    options.token == null &&
                    options.tokenDetails == null &&
                    options.authCallback == null &&
                    options.authUrl == null) {
                /* we have the key and do not need to authenticate the client,
                 * so default to using basic auth */
                Log.i("Auth()", "anonymous, using basic auth");
                this.method = AuthMethod.basic;
                basicCredentials = authOptions.key;
                setClientId(WILDCARD_CLIENTID);
                return;
            }
        }
        /* using token auth, but decide the method */
        this.method = AuthMethod.token;
        if(authOptions.token != null) {
            setTokenDetails(authOptions.token);
        }
        else if(authOptions.tokenDetails != null) {
            setTokenDetails(authOptions.tokenDetails);
        }

        if(authOptions.authCallback != null) {
            Log.i("Auth()", "using token auth with authCallback");
        } else if(authOptions.authUrl != null) {
            /* verify configured URL parses */
            HttpUtils.parseUrl(authOptions.authUrl);
            Log.i("Auth()", "using token auth with authUrl");
        } else if(authOptions.key != null) {
            Log.i("Auth()", "using token auth with client-side signing");
        } else if(tokenDetails != null) {
            Log.i("Auth()", "using token auth with supplied token only");
        } else {
            /* no means to authenticate (Spec: RSA14) */
            Log.e("Auth()", "no authentication parameters supplied");
            throw AblyException.fromErrorInfo(new ErrorInfo("No authentication parameters supplied", 400, 40000));
        }
    }

    public TokenDetails getTokenDetails() {
        Log.i("TokenAuth.getTokenDetails()", "");
        return tokenDetails;
    }

    public String getEncodedToken() {
        Log.i("TokenAuth.getEncodedToken()", "");
        return encodedToken;
    }

    private void setTokenDetails(String token) throws AblyException {
        Log.i("TokenAuth.setTokenDetails()", "");
        this.tokenDetails = new TokenDetails(token);
        this.encodedToken = Base64Coder.encodeString(token);
    }

    private void setTokenDetails(TokenDetails tokenDetails) throws AblyException {
        Log.i("TokenAuth.setTokenDetails()", "");
        setClientId(tokenDetails.clientId);
        this.tokenDetails = tokenDetails;
        this.encodedToken = Base64Coder.encodeString(tokenDetails.token);
    }

    private void clearTokenDetails() {
        Log.i("TokenAuth.clearTokenDetails()", "");
        this.tokenDetails = null;
        this.encodedToken = null;
        this.authHeader = null;
    }

    public TokenDetails assertValidToken() throws AblyException {
        return assertValidToken(tokenParams, authOptions, false);
    }

    private TokenDetails assertValidToken(TokenParams params, AuthOptions options, boolean force) throws AblyException {
        Log.i("Auth.assertValidToken()", "");
        if(tokenDetails != null) {
            if(!force && (tokenDetails.expires == 0 || tokenValid(tokenDetails))) {
                Log.i("Auth.assertValidToken()", "using cached token; expires = " + tokenDetails.expires);
                return tokenDetails;
            } else {
                /* expired, so remove */
                Log.i("Auth.assertValidToken()", "deleting expired token");
                clearTokenDetails();
            }
        }
        Log.i("Auth.assertValidToken()", "requesting new token");
        TokenDetails newTokenDetails;
        try {
            newTokenDetails = requestToken(params, options);
        } catch (AblyException ablyException) {
            if (shouldFailConnectionDueToAuthError(ablyException.errorInfo)) {
                ably.onAuthError(ablyException.errorInfo); // RSA4d
            }
            throw ablyException;
        }
        setTokenDetails(newTokenDetails);
        return tokenDetails;
    }

    /**
     * RSA4d
     * [...] the client library should transition to the FAILED state, with an ErrorInfo
     * (with code 80019, statusCode 403, and cause set to the underlying cause) [...]
     */
    private boolean shouldFailConnectionDueToAuthError(ErrorInfo errorInfo) {
        return errorInfo.statusCode == 403 && errorInfo.code == 80019;
    }

    private boolean tokenValid(TokenDetails tokenDetails) {
        /* RSA4b1: only perform a local check for token validity if we have time sync with the server */
        return (timeDelta == Long.MAX_VALUE) || (tokenDetails.expires > serverTimestamp());
    }

    /**
     * Get the Authorization header, forcing the creation of a new token if requested
     * @param forceRenew
     * @return
     * @throws AblyException
     */
    public void assertAuthorizationHeader(boolean forceRenew) throws AblyException {
        if(authHeader != null && !forceRenew) {
            return;
        }
        if(getAuthMethod() == AuthMethod.basic) {
            authHeader = "Basic " + Base64Coder.encodeString(getBasicCredentials());
        } else {
            if (forceRenew) {
                renewToken();
            } else {
                assertValidToken();
            }
            authHeader = "Bearer " + getEncodedToken();
        }
    }

    public String getAuthorizationHeader() {
        return authHeader;
    }

    private static String random() { return String.format(Locale.ROOT, "%016d", (long)(Math.random() * 1E16)); }

    private static boolean equalNullableStrings(String one, String two) {
        return (one == null) ? (two == null) : one.equals(two);
    }

    private static String hmac(String text, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(Charset.forName("UTF-8")), "HmacSHA256"));
            return new String(Base64Coder.encode(mac.doFinal(text.getBytes(Charset.forName("UTF-8")))));
        } catch (GeneralSecurityException e) { Log.e("Auth.hmac", "Unexpected exception", e); return null; }
    }

    /**
     * Set the clientId, after first initialisation in the construction of the library
     * therefore an existing null value is significant - it means that ClientOptions.clientId
     * was null
     * @param clientId
     * @throws AblyException
     */
    public void setClientId(String clientId) throws AblyException {
        if(clientId == null) {
            /* do nothing - we received a token without a clientId */
            return;
        }

        if(this.clientId == null) {
            /* RSA12a, RSA12b, RSA7b2, RSA7b3, RSA7b4: the given clientId is now our clientId */
            this.clientId = clientId;
            this.ably.onClientIdSet(clientId);
            return;
        }
        /* now this.clientId != null */
        if(this.clientId.equals(clientId)) {
            /* this includes the wildcard case RSA7b4 */
            return;
        }
        if(WILDCARD_CLIENTID.equals(clientId)) {
            /* this signifies that the credentials permit the use of any specific clientId */
            return;
        }
        throw AblyException.fromErrorInfo(new ErrorInfo("Unable to set different clientId from that given in options", 401, 40101));
    }

    /**
     * Verify that a message, possibly containing a clientId,
     * is compatible with Auth.clientId if it is set
     * @param msg
     * @param allowNullClientId true if it is ok for there to be no resolved clientId
     * @param connected true if connected; if false it is ok for the library to be unidentified
     * @return the resolved clientId
     * @throws AblyException
     */
    public String checkClientId(BaseMessage msg, boolean allowNullClientId, boolean connected) throws AblyException {
        /* Check that the message doesn't contain the disallowed wildcard clientId
         * RTL6g3 */
        String msgClientId = msg.clientId;
        if(WILDCARD_CLIENTID.equals(msgClientId)) {
            throw AblyException.fromErrorInfo(new ErrorInfo("Invalid wildcard clientId specified in message", 400, 40000));
        }

        /* Check that any clientId given in the message is compatible with the library clientId */
        boolean undeterminedClientId = (clientId == null && !connected);
        if(msgClientId != null) {
            if(msgClientId.equals(clientId) || WILDCARD_CLIENTID.equals(clientId) || undeterminedClientId) {
                /* RTL6g4: be lenient checking against a null clientId if we're not connected */
                return msgClientId;
            }
            throw AblyException.fromErrorInfo(new ErrorInfo("Incompatible clientId specified in message", 400, 40012));
        }

        if(clientId == null || clientId.equals(WILDCARD_CLIENTID)) {
            if(allowNullClientId || undeterminedClientId) {
                /* the message is sent with no clientId */
                return null;
            }
            /* this case only applies to presence, when allowNullClientId=false */
            throw AblyException.fromErrorInfo(new ErrorInfo("Invalid attempt to enter with no clientId", 400, 91000));
        }

        /* the message is sent with no explicit clientId, but implicitly has the library clientId */
        return clientId;
    }

    /**
     * Using time delta obtained before guess current server time
     */
    public long serverTimestamp() {
        long clientTime = timestamp();
        long delta = timeDelta;
        return delta != Long.MAX_VALUE ? clientTime + timeDelta : clientTime;
    }

    private static final String TAG = Auth.class.getName();
    private final PubSubBase ably;
    private final Clock clock;
    private final AuthMethod method;
    private AuthOptions authOptions;
    private TokenParams tokenParams;
    private String basicCredentials;
    private TokenDetails tokenDetails;
    private String encodedToken;
    private String authHeader;

    /**
     * Time delta is server time minus client time, in milliseconds, MAX_VALUE if not obtained yet
     */
    private long timeDelta = Long.MAX_VALUE;
    /**
     * Time delta between System.nanoTime() and System.currentTimeMillis. If it changes significantly it
     * suggests device time/date has changed
     */
    private long nanoTimeDelta;

    public static final String WILDCARD_CLIENTID = "*";
    /**
     * For testing purposes we need method to clear cached timeDelta
     */
    public void clearCachedServerTime() {
        timeDelta = Long.MAX_VALUE;
    }
}
