package io.ably.pubsub.http;

import io.ably.pubsub.http.BasePaginatedQuery;
import io.ably.pubsub.http.Http;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Annotation;
import io.ably.pubsub.types.AnnotationAction;
import io.ably.pubsub.types.AnnotationSerializer;
import io.ably.pubsub.types.AsyncPaginatedResult;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.MessageDecodeException;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.Crypto;
import io.ably.pubsub.util.Log;

import java.util.Arrays;

/**
 * The HttpAnnotation class provides methods to manage and interact with annotations
 * associated with messages in a specific channel.
 * <p>
 * Annotations can be retrieved, published, or deleted both synchronously and asynchronously.
 * This class is intended as part of a client library for managing annotations via REST architecture.
 * <p>
 * Note: This is an experimental API. While the underlying functionality is stable,
 * the public API may change in future releases.
 */
public class HttpAnnotations {

    private static final String TAG = HttpAnnotations.class.getName();

    private final String channelName;
    private final Http http;
    private final ClientOptions clientOptions;
    private final ChannelOptions channelOptions;

    public HttpAnnotations(String channelName, Http http, ClientOptions clientOptions, ChannelOptions channelOptions) {
        this.channelName = channelName;
        this.http = http;
        this.clientOptions = clientOptions;
        this.channelOptions = channelOptions;
    }

    /**
     * Retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param params an array of query parameters for filtering or modifying the request.
     * @return a {@link PaginatedResult} containing the matching annotations.
     * @throws AblyException if an error occurs during the retrieval process.
     */
    public PaginatedResult<Annotation> get(String messageSerial, Param[] params) throws AblyException {
        validateMessageSerial(messageSerial);
        return getImpl(messageSerial, params).sync();
    }

    /**
     * @see #get(String, Param[])
     */
    public PaginatedResult<Annotation> get(Message message, Param[] params) throws AblyException {
        return get(message.serial, params);
    }

    /**
     * Asynchronously retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param params an array of query parameters for filtering or modifying the request.
     * @param callback a callback to handle the result asynchronously, providing an {@link AsyncPaginatedResult} containing the matching annotations.
     */
    public void getAsync(String messageSerial, Param[] params, Callback<AsyncPaginatedResult<Annotation>> callback) throws AblyException {
        validateMessageSerial(messageSerial);
        getImpl(messageSerial, params).async(callback);
    }

    /**
     * @see #getAsync(String, Param[], Callback)
     */
    public void getAsync(Message message, Param[] params, Callback<AsyncPaginatedResult<Annotation>> callback) throws AblyException {
        getAsync(message.serial, params, callback);
    }

    /**
     * Retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated
     * @return a PaginatedResult containing the matching annotations
     * @throws AblyException if an error occurs during the retrieval process
     */
    public PaginatedResult<Annotation> get(String messageSerial) throws AblyException {
        return get(messageSerial, null);
    }

    /**
     * @see #get(String)
     */
    public PaginatedResult<Annotation> get(Message message) throws AblyException {
        return get(message.serial);
    }

    /**
     * Asynchronously retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param callback a callback to handle the result asynchronously, providing an {@link AsyncPaginatedResult} containing the matching annotations.
     */
    public void getAsync(String messageSerial, Callback<AsyncPaginatedResult<Annotation>> callback) throws AblyException {
        validateMessageSerial(messageSerial);
        getImpl(messageSerial, null).async(callback);
    }

    /**
     * @see #getAsync(String, Callback)
     */
    public void getAsync(Message message, Callback<AsyncPaginatedResult<Annotation>> callback) throws AblyException {
        getAsync(message.serial, callback);
    }

    /**
     * Publishes an annotation associated with the specified message serial
     * to the REST channel.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param annotation the annotation to be published.
     * @throws AblyException if an error occurs during the publishing process.
     */
    public void publish(String messageSerial, Annotation annotation) throws AblyException {
        validateMessageSerial(messageSerial);
        publishImpl(messageSerial, annotation).sync();
    }

    /**
     * @see #publish(String, Annotation)
     */
    public void publish(Message message, Annotation annotation) throws AblyException {
        publish(message.serial, annotation);
    }

    /**
     * Asynchronously publishes an annotation associated with the specified message serial
     * to the REST channel.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param annotation the annotation to be published.
     * @param callback a callback to handle the result asynchronously, providing a
     *                 completion indication or error information.
     */
    public void publishAsync(String messageSerial, Annotation annotation, Callback<Void> callback) throws AblyException {
        validateMessageSerial(messageSerial);
        publishImpl(messageSerial, annotation).async(callback);
    }

    /**
     * @see #publishAsync(String, Annotation, Callback)
     */
    public void publishAsync(Message message, Annotation annotation, Callback<Void> callback) throws AblyException {
        publishAsync(message.serial, annotation, callback);
    }

    /**
     * Deletes an annotation associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param annotation the annotation to be deleted.
     * @throws AblyException if an error occurs during the deletion process.
     */
    public void delete(String messageSerial, Annotation annotation) throws AblyException {
        validateMessageSerial(messageSerial);
        deleteImpl(messageSerial, annotation).sync();
    }

    /**
     * @see #delete(String, Annotation)
     */
    public void delete(Message message, Annotation annotation) throws AblyException {
        delete(message.serial, annotation);
    }

    /**
     * Asynchronously deletes an annotation associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param annotation the annotation to be deleted.
     * @param callback a callback to handle the result asynchronously, providing a completion
     *                 indication or error information.
     */
    public void deleteAsync(String messageSerial, Annotation annotation, Callback<Void> callback) throws AblyException {
        validateMessageSerial(messageSerial);
        deleteImpl(messageSerial, annotation).async(callback);
    }

    /**
     * @see #deleteAsync(String, Annotation, Callback)
     */
    public void deleteAsync(Message message, Annotation annotation, Callback<Void> callback) throws AblyException {
        deleteAsync(message.serial, annotation, callback);
    }

    private void validateMessageSerial(String messageSerial) throws AblyException {
        if (messageSerial == null) throw AblyException.fromErrorInfo(
            new ErrorInfo("Message serial can not be empty", 400, 40003)
        );
    }

    private String getBasePath(String messageSerial) {
        return "/channels/" + HttpUtils.encodeURIComponent(channelName) + "/messages/" + HttpUtils.encodeURIComponent(messageSerial) + "/annotations";
    }

    private Http.Request<Void> deleteImpl(String messageSerial, Annotation annotation) throws AblyException {
        Log.v(TAG, "delete(): annotation=" + annotation);
        annotation.action = AnnotationAction.ANNOTATION_DELETE;
        return sendAnnotationImpl(messageSerial, annotation);
    }

    private Http.Request<Void> publishImpl(String messageSerial, Annotation annotation) throws AblyException {
        Log.v(TAG, "publish(): annotation=" + annotation);
        // (RSAN1c2)
        annotation.action = AnnotationAction.ANNOTATION_CREATE;
        return sendAnnotationImpl(messageSerial, annotation);
    }

    private Http.Request<Void> sendAnnotationImpl(String messageSerial, Annotation annotation) throws AblyException {
        // (RSAN1a3)
        if (annotation.type == null) {
            throw AblyException.fromErrorInfo(new ErrorInfo("Annotation type must be specified", 400, 40000));
        }

        // (RSAN1c1)
        annotation.messageSerial = messageSerial;

        try {
            // (RSAN1c3)
            annotation.encode(channelOptions);
        } catch (MessageDecodeException e) {
            throw AblyException.fromThrowable(e);
        }

        // (RSAN1c4)
        if (annotation.id == null && clientOptions.idempotentRestPublishing) {
            annotation.id = Crypto.getRandomId();
        }

        return http.request((http, callback) -> {
            Annotation[] annotations = new Annotation[] { annotation };
            HttpCore.RequestBody requestBody = clientOptions.useBinaryProtocol ? AnnotationSerializer.asMsgpackRequest(annotations) : AnnotationSerializer.asJsonRequest(annotations);
            final Param[] params = clientOptions.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null; // RSC7c
            http.post(getBasePath(messageSerial), HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol), params, requestBody, null, true, callback);
        });
    }

    private BasePaginatedQuery.ResultRequest<Annotation> getImpl(String messageSerial, Param[] initialParams) {
        Log.v(TAG, "getImpl(): params=" + Arrays.toString(initialParams));
        HttpCore.BodyHandler<Annotation> bodyHandler = AnnotationSerializer.getAnnotationResponseHandler(channelOptions);
        final Param[] params = clientOptions.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams; // RSC7c
        return (new BasePaginatedQuery<>(http, getBasePath(messageSerial), HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol), params, bodyHandler)).get();
    }
}
