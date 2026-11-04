package io.ably.pubsub.liveobjects.http;

import com.google.gson.JsonElement;
import io.ably.pubsub.http.HttpConstants;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpRequester;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.http.PubSubBase;
import io.ably.pubsub.liveobjects.HttpObject;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.Base64Coder;
import io.ably.pubsub.util.Crypto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * [Internal] The {@link HttpObject} of one HTTP channel. Stateless apart from the client-wide
 * {@link HttpObjectServerTime}; obtain it from {@code channel.object}.
 *
 * <p>Each operation is built once as an {@link HttpRequester.Request} and run with
 * {@link HttpRequester.Request#sync()} or {@link HttpRequester.Request#async(Callback)}, like the
 * rest of the HTTP channel API. Async callbacks, including those for invalid input, are invoked on
 * the scheduler's background thread.
 *
 * <p>Spec: RSO1-RSO4
 */
public final class DefaultHttpObject implements HttpObject {

    private static final String NONCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int NONCE_LENGTH = 16; // RTO11f6 - at least 16 characters
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String COMPACT = "compact";

    private final PubSubBase client;
    private final String basePath;
    private final HttpObjectServerTime serverTime;

    /**
     * @param client the HTTP client requests are sent through.
     * @param channelBasePath the channel's REST path, {@code /channels/<encoded name>}.
     * @param serverTime the client's shared server time.
     */
    public DefaultHttpObject(@NotNull PubSubBase client, @NotNull String channelBasePath, @NotNull HttpObjectServerTime serverTime) {
        this.client = client;
        this.basePath = channelBasePath + "/object";
        this.serverTime = serverTime;
    }

    //region get (RSO2)

    @Override
    public @Nullable JsonElement getCompact() throws AblyException {
        return compactImpl(null).sync();
    }

    @Override
    public @Nullable JsonElement getCompact(@Nullable Param[] params) throws AblyException {
        return compactImpl(params).sync();
    }

    @Override
    public void getCompactAsync(@NotNull Callback<JsonElement> callback) {
        compactImpl(null).async(callback);
    }

    @Override
    public void getCompactAsync(@Nullable Param[] params, @NotNull Callback<JsonElement> callback) {
        compactImpl(params).async(callback);
    }

    @Override
    public @Nullable HttpObjectGetFullResult get() throws AblyException {
        return fullImpl(null).sync();
    }

    @Override
    public @Nullable HttpObjectGetFullResult get(@Nullable Param[] params) throws AblyException {
        return fullImpl(params).sync();
    }

    @Override
    public void getAsync(@NotNull Callback<HttpObjectGetFullResult> callback) {
        fullImpl(null).async(callback);
    }

    @Override
    public void getAsync(@Nullable Param[] params, @NotNull Callback<HttpObjectGetFullResult> callback) {
        fullImpl(params).async(callback);
    }

    /** RSO2f - the compact body as JSON, otherwise unchanged; binary values become Base64 strings. */
    private HttpRequester.Request<JsonElement> compactImpl(@Nullable Param[] params) {
        // compact is the server default, so it isn't sent (RSO2d)
        return getImpl(params, null, body -> body == null ? null : HttpObjectCodec.toJson(body));
    }

    /** RSO2g - the full body decoded into typed results. */
    private HttpRequester.Request<HttpObjectGetFullResult> fullImpl(@Nullable Param[] params) {
        return getImpl(params, new Param(COMPACT, "false"),
            body -> body == null ? null : HttpObjectCodec.decodeFullNode(body));
    }

    private <T> HttpRequester.Request<T> getImpl(@Nullable Param[] params, @Nullable Param format,
                                                 BodyDecoder<T> decoder) {
        // RSO2c - the objectId, if any, is a percent-encoded path segment
        String objectId = Param.getFirst(params, HttpObjectGetParams.OBJECT_ID);
        final String path = objectId != null ? basePath + "/" + HttpUtils.encodeURIComponent(objectId) : basePath;

        // RSO2d, RSO6 - every other param, such as path, goes in the query string unmodified; the
        // format is this method's, so a caller's compact param is dropped
        List<Param> query = new ArrayList<>();
        if (params != null) {
            for (Param param : params) {
                if (param.key.equals(HttpObjectGetParams.OBJECT_ID) || param.key.equals(COMPACT)) continue;
                query.add(param);
            }
        }
        if (format != null) query.add(format);
        if (client.options.addRequestIds) query.add(Crypto.generateRandomRequestId()); // RSC7c
        final Param[] queryParams = query.toArray(new Param[0]);

        // RSO2e - the body is decoded per RSC8 before the decoder runs; no body is null
        return client.http.request((http, callback) ->
            http.get(path, HttpUtils.defaultAcceptHeaders(binary()), queryParams, responseHandler(decoder), true, callback));
    }

    //endregion

    //region publish (RSO3)

    @Override
    public @NotNull HttpObjectPublishResult publish(@NotNull HttpObjectOperation operation) throws AblyException {
        return publishImpl(Collections.singletonList(operation)).sync();
    }

    @Override
    public @NotNull HttpObjectPublishResult publish(@NotNull List<HttpObjectOperation> operations) throws AblyException {
        return publishImpl(operations).sync();
    }

    @Override
    public void publishAsync(@NotNull HttpObjectOperation operation, @NotNull Callback<HttpObjectPublishResult> callback) {
        publishImpl(Collections.singletonList(operation)).async(callback);
    }

    @Override
    public void publishAsync(@NotNull List<HttpObjectOperation> operations, @NotNull Callback<HttpObjectPublishResult> callback) {
        publishImpl(operations).async(callback);
    }

    private HttpRequester.Request<HttpObjectPublishResult> publishImpl(final List<HttpObjectOperation> operations) {
        final Param[] query = client.options.addRequestIds ? new Param[]{Crypto.generateRandomRequestId()} : new Param[0];
        return client.http.request((http, callback) -> {
            // Encoded before anything is sent, so invalid input fails without a request (RSO3d1).
            // No ids are generated, whatever idempotentRestPublishing says (RSO3f).
            final HttpCore.RequestBody body;
            try {
                body = encodeOperations(operations);
            } catch (AblyException e) {
                http.execute(() -> callback.onError(e.errorInfo));
                return;
            }
            // RSO3c, RSO3e - one POST carrying all operations as an array.
            // Sent to the current host only, never retried on a fallback host: operations don't
            // carry generated ids (RSO3f), so a batch the first host applied before failing would
            // be applied twice.
            http.ablyHttpExecuteWithRetry(client.httpCore.getPreferredHost(), basePath, HttpConstants.Methods.POST,
                HttpUtils.defaultAcceptHeaders(binary()), query, body,
                responseHandler(HttpObjectCodec::decodePublishResult), true, callback);
        });
    }

    private HttpCore.RequestBody encodeOperations(List<HttpObjectOperation> operations) throws AblyException {
        if (operations == null) throw HttpObjectCodec.invalidInput("operations must not be null");
        try {
            List<Object> wireOperations = new ArrayList<>();
            for (HttpObjectOperation operation : operations) {
                if (operation == null) throw HttpObjectCodec.invalidInput("operations must not contain null");
                wireOperations.add(HttpObjectCodec.encodeOperation(operation, binary()));
            }
            return HttpObjectCodec.requestBody(wireOperations, binary());
        } catch (RuntimeException e) {
            // e.g. a null where the typed API expects a value; report it as invalid input
            throw HttpObjectCodec.invalidInput("Invalid operation: " + e);
        }
    }

    //endregion

    //region generateObjectId (RSO4)

    @Override
    public @NotNull HttpObjectGenerateIdResult generateObjectId(@NotNull HttpObjectGenerateIdBody createBody) throws AblyException {
        CreateBody body = CreateBody.of(createBody);
        return body.withTimestamp(serverTime.currentTimeMillis());
    }

    @Override
    public void generateObjectIdAsync(@NotNull final HttpObjectGenerateIdBody createBody,
                                      @NotNull Callback<HttpObjectGenerateIdResult> callback) {
        // Runs on the scheduler, so the callback is invoked on a background thread even when the
        // input is invalid or the server time is already known.
        client.http.<HttpObjectGenerateIdResult>request((http, cb) -> http.execute(() -> {
            final CreateBody body;
            try {
                body = CreateBody.of(createBody);
            } catch (AblyException e) {
                cb.onError(e.errorInfo);
                return;
            }
            serverTime.currentTimeMillisAsync(new Callback<Long>() {
                @Override
                public void onSuccess(Long timestamp) {
                    cb.onSuccess(body.withTimestamp(timestamp));
                }

                @Override
                public void onError(ErrorInfo reason) {
                    cb.onError(reason);
                }
            });
        })).async(callback);
    }

    /** The type, initialValue and nonce of a generateObjectId call, before the server time is known. */
    private static final class CreateBody {
        private final String type;
        private final String initialValue;
        private final String nonce;

        private CreateBody(String type, String initialValue) {
            this.type = type;
            this.initialValue = initialValue;
            this.nonce = generateNonce(); // RSO4f
        }

        /** RSO4c, RSO4d, RSO4e - the type and the initialValue, always JSON-encoded whatever the protocol. */
        static CreateBody of(HttpObjectGenerateIdBody createBody) throws AblyException {
            try {
                return encode(createBody);
            } catch (RuntimeException e) {
                // e.g. a null entry value; report it as invalid input
                throw HttpObjectCodec.invalidInput("Invalid create body: " + e);
            }
        }

        private static CreateBody encode(HttpObjectGenerateIdBody createBody) throws AblyException {
            if (createBody != null && createBody.getMapCreate() != null) {
                return new CreateBody("map",
                    HttpObjectCodec.toJson(HttpObjectCodec.encodeMapCreate(createBody.getMapCreate(), false)).toString());
            }
            if (createBody != null && createBody.getCounterCreate() != null) {
                return new CreateBody("counter",
                    HttpObjectCodec.toJson(HttpObjectCodec.encodeCounterCreate(createBody.getCounterCreate())).toString());
            }
            throw HttpObjectCodec.invalidInput("generateObjectId requires a mapCreate or counterCreate body");
        }

        /** RSO4h, RSO4i */
        HttpObjectGenerateIdResult withTimestamp(long timestamp) {
            return new HttpObjectResults.GenerateIdResult(objectId(type, initialValue, nonce, timestamp), nonce, initialValue);
        }
    }

    //endregion

    private boolean binary() {
        return client.options.useBinaryProtocol;
    }

    /** Decodes a response body that has already been converted to plain values. */
    private interface BodyDecoder<T> {
        T decode(Object body) throws AblyException;
    }

    /**
     * Surfaces server errors as the request's failure (RSO2h, RSO3g) and decodes the body: first
     * its format (RSC8), then its structure with {@code decoder}. A body that can't be decoded fails
     * as a plain {@link AblyException}, never a {@code HostFailedException}, so the request is not
     * retried on a fallback host.
     */
    private static <T> HttpCore.ResponseHandler<T> responseHandler(final BodyDecoder<T> decoder) {
        return (response, error) -> {
            if (error != null) throw AblyException.fromErrorInfo(error);
            Object body;
            try {
                body = HttpObjectCodec.decodeResponseBody(response);
            } catch (Exception e) {
                throw HttpObjectCodec.decodeError("Failed to decode response body: " + e.getMessage(), e);
            }
            return decoder.decode(body);
        };
    }

    /**
     * {@code [type]:[base64url(SHA-256(utf8("[initialValue]:[nonce]")))]@[timestamp]}, without padding.
     * <p>Spec: RTO14
     */
    private static String objectId(String type, String initialValue, String nonce, long timestamp) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest((initialValue + ":" + nonce).getBytes(Charset.forName("UTF-8")));
            String base64Url = Base64Coder.encodeToString(hash)
                .replace('+', '-').replace('/', '_').replace("=", "");
            return type + ":" + base64Url + "@" + timestamp;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e); // every Java platform provides it
        }
    }

    private static String generateNonce() {
        StringBuilder nonce = new StringBuilder(NONCE_LENGTH);
        for (int i = 0; i < NONCE_LENGTH; i++) {
            nonce.append(NONCE_CHARS.charAt(RANDOM.nextInt(NONCE_CHARS.length())));
        }
        return nonce.toString();
    }
}
