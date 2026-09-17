package io.ably.pubsub.http;

import io.ably.pubsub.http.BasePaginatedQuery;
import io.ably.pubsub.http.Http;
import io.ably.pubsub.http.HttpScheduler;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.realtime.CompletionListener;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.AsyncPaginatedResult;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.MessageOperation;
import io.ably.pubsub.types.MessageSerializer;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.PresenceMessage;
import io.ably.pubsub.types.PresenceSerializer;
import io.ably.pubsub.types.PublishResult;
import io.ably.pubsub.types.UpdateDeleteResult;
import io.ably.pubsub.util.Crypto;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;

/**
 * A class representing a Channel in the Ably REST API.
 * In the REST API, the library is essentially stateless;
 * a Channel object simply represents a channel for making
 * REST requests, and existence of a channel does not
 * signify that there is a realtime connection or attachment
 * to that channel.
 */
public class ChannelBase {

    /**
     * The Channel name
     */
    public final String name;

    /**
     * The presence instance for this channel.
     */
    public final Presence presence;

    /**
     * Represents the annotations associated with a channel message.
     * This field provides functionality for managing annotations.
     */
    public final HttpAnnotations annotations;


    private final MessageEditsMixin messageEditsMixin;


    /**
     * Publish a message on this channel using the REST API.
     * Since the REST API is stateless, this request is made independently
     * of any other request on this or any other channel.
     * @param name the event name
     * @param data the message payload;
     * @throws AblyException
     */
    @Blocking
    public void publish(String name, Object data) throws AblyException {
        publish(ably.http, name, data);
    }

    @Blocking
    void publish(Http http, String name, Object data) throws AblyException {
        publishImpl(http, name, data).sync();
    }

    /**
     * Publish a message on this channel using the REST API and return the result.
     * Since the REST API is stateless, this request is made independently
     * of any other request on this or any other channel.
     * @param name the event name
     * @param data the message payload;
     * @return A {@link PublishResult} containing the message serial(s)
     * @throws AblyException
     */
    @Blocking
    public PublishResult publishWithResult(String name, Object data) throws AblyException {
        return publishWithResult(ably.http, name, data);
    }

    @Blocking
    PublishResult publishWithResult(Http http, String name, Object data) throws AblyException {
        return publishImpl(http, name, data).sync();
    }

    /**
     * Publish a message on this channel using the REST API.
     * Since the REST API is stateless, this request is made independently
     * of any other request on this or any other channel.
     *
     * @param name the event name
     * @param data the message payload;
     * @param listener a listener to be notified of the outcome of this message.
     * <p>
     * This listener is invoked on a background thread.
     * @deprecated Use {@link #publishAsync(String, Object, Callback)} instead.
     */
    @Deprecated
    public void publishAsync(String name, Object data, CompletionListener listener) {
        publishAsync(ably.http, name, data, listener);
    }

    @NonBlocking
    void publishAsync(Http http, String name, Object data, CompletionListener listener) {
        publishImpl(http, name, data).async(new CompletionListener.ToCallback<>(listener));
    }

    /**
     * Asynchronously publish a message on this channel using the REST API.
     * Since the REST API is stateless, this request is made independently
     * of any other request on this or any other channel.
     *
     * @param name the event name
     * @param data the message payload;
     * @param callback a callback to be notified of the outcome of this message with the {@link PublishResult}.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void publishAsync(String name, Object data, Callback<PublishResult> callback) {
        publishAsync(ably.http, name, data, callback);
    }

    @NonBlocking
    void publishAsync(Http http, String name, Object data, Callback<PublishResult> callback) {
        publishImpl(http, name, data).async(callback);
    }

    private Http.Request<PublishResult> publishImpl(Http http, String name, Object data) {
        return publishImpl(http, new Message[] {new Message(name, data)});
    }

    /**
     * Publish an array of messages on this channel. When there are
     * multiple messages to be sent, it is more efficient to use this
     * method to publish them in a single request, as compared with
     * publishing via multiple independent requests.
     * @param messages array of messages to publish.
     * @throws AblyException
     */
    @Blocking
    public void publish(final Message[] messages) throws AblyException {
        publish(ably.http, messages);
    }

    @Blocking
    void publish(Http http, final Message[] messages) throws AblyException {
        publishImpl(http, messages).sync();
    }

    /**
     * Publish an array of messages on this channel. When there are
     * multiple messages to be sent, it is more efficient to use this
     * method to publish them in a single request, as compared with
     * publishing via multiple independent requests.
     * @param messages array of messages to publish.
     * @throws AblyException
     */
    @Blocking
    public PublishResult publishWithResult(final Message[] messages) throws AblyException {
        return publishImpl(ably.http, messages).sync();
    }

    /**
     * Asynchronously publish an array of messages on this channel
     *
     * @param messages the message
     * @param listener a listener to be notified of the outcome of this message.
     * @deprecated Use {@link #publishAsync(Message[], Callback)} instead.
     * <p>
     * This listener is invoked on a background thread.
     */
    @Deprecated
    @NonBlocking
    public void publishAsync(final Message[] messages, final CompletionListener listener) {
        publishAsync(ably.http, messages, listener);
    }

    @Deprecated
    void publishAsync(Http http, final Message[] messages, final CompletionListener listener) {
        publishImpl(http, messages).async(new CompletionListener.ToCallback<>(listener));
    }

    /**
     * Asynchronously publish an array of messages on this channel
     *
     * @param messages the message
     * @param callback a callback to be notified of the outcome of this message.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void publishAsync(final Message[] messages, final Callback<PublishResult> callback) {
        publishImpl(ably.http, messages).async(callback);
    }

    private Http.Request<PublishResult> publishImpl(Http http, final Message[] messages) {
        return http.request(new Http.Execute<PublishResult>() {
            @Override
            public void execute(HttpScheduler http, final Callback<PublishResult> callback) throws AblyException {
                /* handle message ids */
                boolean hasClientSuppliedId = false;
                for(Message message : messages) {
                    /* RSL1k2 */
                    hasClientSuppliedId |= (message.id != null);
                    /* RTL6g3 */
                    ably.auth.checkClientId(message, true, false);
                    message.encode(options);
                }
                if(!hasClientSuppliedId && ably.options.idempotentRestPublishing) {
                    /* RSL1k1: populate the message id with a library-generated id */
                    String messageId = Crypto.getRandomId();
                    for (int i = 0; i < messages.length; i++) {
                        messages[i].id = messageId + ':' + i;
                    }
                }

                HttpCore.RequestBody requestBody = ably.options.useBinaryProtocol ? MessageSerializer.asMsgpackRequest(messages) : MessageSerializer.asJsonRequest(messages);
                final Param[] params = ably.options.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null; // RSC7c

                // Create ResponseHandler from BodyHandler
                HttpCore.BodyHandler<String> bodyHandler = PublishResult.getBodyHandler();
                HttpCore.ResponseHandler<PublishResult> responseHandler = (response, error) -> {
                    if (error != null) throw AblyException.fromErrorInfo(error);
                    String[] serials = bodyHandler.handleResponseBody(response.contentType, response.body);
                    return new PublishResult(serials);
                };

                http.post(basePath + "/messages", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, requestBody, responseHandler, true, callback);
            }
        });
    }

    /**
     * Obtain recent history for this channel using the REST API.
     * The history provided relqtes to all clients of this application,
     * not just this instance.
     * @param params the request params. See the Ably REST API
     * documentation for more details.
     * @return an array of Messages for this Channel.
     * @throws AblyException
     */
    public PaginatedResult<Message> history(Param[] params) throws AblyException {
        return history(ably.http, params);
    }

    PaginatedResult<Message> history(Http http, Param[] params) throws AblyException {
        return historyImpl(http, params).sync();
    }

    /**
     * Asynchronously obtain recent history for this channel using the REST API.
     * @param params the request params. See the Ably REST API
     * @param callback
     * @return
     */
    public void historyAsync(Param[] params, Callback<AsyncPaginatedResult<Message>> callback) {
        historyAsync(ably.http, params, callback);
    }

    void historyAsync(Http http, Param[] params, Callback<AsyncPaginatedResult<Message>> callback) {
        historyImpl(http, params).async(callback);
    }

    private BasePaginatedQuery.ResultRequest<Message> historyImpl(Http http, Param[] initialParams) {
        HttpCore.BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(options);
        final Param[] params = ably.options.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams; // RSC7c
        return (new BasePaginatedQuery<Message>(http, basePath + "/messages", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler)).get();
    }

    /**
     * Enables the retrieval of the current and historic presence set for a channel.
     */
    public class Presence {

        /**
         * Retrieves the current members present on the channel and the metadata for each member,
         * such as their {@link io.ably.pubsub.types.PresenceMessage.Action} and ID. Returns a {@link PaginatedResult} object,
         * containing an array of {@link PresenceMessage} objects.
         * <p>
         * Spec: RSPa
         * @param params the request params:
         * <p>
         * limit (RSP3a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
         * <p>
         * clientId (RSP3a2) - Filters the list of returned presence members by a specific client using its ID.
         * <p>
         * connectionId (RSP3a3) - Filters the list of returned presence members by a specific connection using its ID.
         * @return A {@link PaginatedResult} object containing an array of {@link PresenceMessage} objects.
         * @throws AblyException
         */
        public PaginatedResult<PresenceMessage> get(Param[] params) throws AblyException {
            return get(ably.http, params);
        }

        PaginatedResult<PresenceMessage> get(Http http, Param[] params) throws AblyException {
            return getImpl(http, params).sync();
        }

        /**
         * Asynchronously retrieves the current members present on the channel and the metadata for each member,
         * such as their {@link io.ably.pubsub.types.PresenceMessage.Action} and ID. Returns a {@link PaginatedResult} object,
         * containing an array of {@link PresenceMessage} objects.
         * <p>
         * Spec: RSPa
         * @param params the request params:
         * <p>
         * limit (RSP3a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
         * <p>
         * clientId (RSP3a2) - Filters the list of returned presence members by a specific client using its ID.
         * <p>
         * connectionId (RSP3a3) - Filters the list of returned presence members by a specific connection using its ID.
         * @param callback A Callback returning {@link AsyncPaginatedResult} object containing an array of {@link PresenceMessage} objects.
         * <p>
         * This callback is invoked on a background thread.
         */
        public void getAsync(Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
            getAsync(ably.http, params, callback);
        }

        void getAsync(Http http, Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
            getImpl(http, params).async(callback);
        }

        private BasePaginatedQuery.ResultRequest<PresenceMessage> getImpl(Http http, Param[] initialParams) {
            HttpCore.BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(options);
            final Param[] params = ably.options.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams; // RSC7c
            return (new BasePaginatedQuery<PresenceMessage>(http, basePath + "/presence", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler)).get();
        }

        /**
         * Retrieves a {@link PaginatedResult} object, containing an array of historical {@link PresenceMessage} objects for the channel.
         * If the channel is configured to persist messages,
         * then presence messages can be retrieved from history for up to 72 hours in the past.
         * If not, presence messages can only be retrieved from history for up to two minutes in the past.
         * <p>
         * Spec: RSP4a
         * @param params the request params:
         * <p>
         * start (RSP4b1) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
         * <p>
         * end (RSP4b1) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
         * <p>
         * direction (RSP4b2) - The order for which messages are returned in.
         *               Valid values are backwards which orders messages from most recent to oldest,
         *               or forwards which orders messages from oldest to most recent.
         *               The default is backwards.
         * limit (RSP4b3) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
         * @return A {@link PaginatedResult} object containing an array of {@link PresenceMessage} objects.
         * @throws AblyException
         */
        public PaginatedResult<PresenceMessage> history(Param[] params) throws AblyException {
            return history(ably.http, params);
        }

        PaginatedResult<PresenceMessage> history(Http http, Param[] params) throws AblyException {
            return historyImpl(http, params).sync();
        }

        /**
         * Asynchronously retrieves a {@link PaginatedResult} object, containing an array of historical {@link PresenceMessage} objects for the channel.
         * If the channel is configured to persist messages,
         * then presence messages can be retrieved from history for up to 72 hours in the past.
         * If not, presence messages can only be retrieved from history for up to two minutes in the past.
         * <p>
         * Spec: RSP4a
         * @param params the request params:
         * <p>
         * start (RSP4b1) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
         * <p>
         * end (RSP4b1) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
         * <p>
         * direction (RSP4b2) - The order for which messages are returned in.
         *               Valid values are backwards which orders messages from most recent to oldest,
         *               or forwards which orders messages from oldest to most recent.
         *               The default is backwards.
         * limit (RSP4b3) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
         * @param callback  A Callback returning {@link AsyncPaginatedResult} object containing an array of {@link PresenceMessage} objects.
         * <p>
         * This callback is invoked on a background thread.
         * @throws AblyException
         */
        public void historyAsync(Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
            historyAsync(ably.http, params, callback);
        }

        void historyAsync(Http http, Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
            historyImpl(http, params).async(callback);
        }

        private BasePaginatedQuery.ResultRequest<PresenceMessage> historyImpl(Http http, Param[] initialParams) {
            HttpCore.BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(options);
            final Param[] params = ably.options.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams; // RSC7c
            return (new BasePaginatedQuery<PresenceMessage>(http, basePath + "/presence/history", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler)).get();
        }

    }

    //region Message Edits and Deletes

    /**
     * Retrieves the latest version of a specific message by its serial identifier.
     * <p>
     * This method allows you to fetch the current state of a message, including any updates
     * or deletions that have been applied since its creation.
     *
     * @param serial The unique serial identifier of the message to retrieve.
     * @return A {@link Message} object representing the latest version of the message.
     * @throws AblyException If the message cannot be retrieved or does not exist.
     */
    @Blocking
    public Message getMessage(String serial) throws AblyException {
        return messageEditsMixin.getMessage(ably.http, serial);
    }

    /**
     * Asynchronously retrieves the latest version of a specific message by its serial identifier.
     *
     * @param serial The unique serial identifier of the message to retrieve.
     * @param callback A callback to handle the result asynchronously.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void getMessageAsync(String serial, Callback<Message> callback) {
        messageEditsMixin.getMessageAsync(ably.http, serial, callback);
    }

    /**
     * Updates an existing message using patch semantics.
     * <p>
     * Non-null fields in the provided message (name, data, extras) will replace the corresponding
     * fields in the existing message, while null fields will be left unchanged.
     *
     * @param message A {@link Message} object containing the fields to update and the serial identifier.
     *                Only non-null fields will be applied to the existing message.
     * @param operation operation metadata such as clientId, description, or metadata in the version field
     * @throws AblyException If the update operation fails.
     * @return A {@link UpdateDeleteResult} containing the updated message version serial.
     */
    @Blocking
    public UpdateDeleteResult updateMessage(Message message, MessageOperation operation) throws AblyException {
        return messageEditsMixin.updateMessage(ably.http, message, operation);
    }

    /**
     * Updates an existing message using patch semantics.
     * <p>
     * Non-null fields in the provided message (name, data, extras) will replace the corresponding
     * fields in the existing message, while null fields will be left unchanged.
     *
     * @param message A {@link Message} object containing the fields to update and the serial identifier.
     *                Only non-null fields will be applied to the existing message.
     * @throws AblyException If the update operation fails.
     * @return A {@link UpdateDeleteResult} containing the updated message version serial.
     */
    @Blocking
    public UpdateDeleteResult updateMessage(Message message) throws AblyException {
        return updateMessage(message, null);
    }

    /**
     * Asynchronously updates an existing message.
     *
     * @param message A {@link Message} object containing the fields to update and the serial identifier.
     * @param operation operation metadata such as clientId, description, or metadata in the version field
     * @param callback A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void updateMessageAsync(Message message, MessageOperation operation, Callback<UpdateDeleteResult> callback) {
        messageEditsMixin.updateMessageAsync(ably.http, message, operation, callback);
    }

    /**
     * Asynchronously updates an existing message.
     *
     * @param message A {@link Message} object containing the fields to update and the serial identifier.
     * @param callback A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void updateMessageAsync(Message message, Callback<UpdateDeleteResult> callback) {
        updateMessageAsync(message, null, callback);
    }

    /**
     * Marks a message as deleted.
     * <p>
     * This operation does not remove the message from history; it marks it as deleted
     * while preserving the full message history. The deleted message can still be
     * retrieved and will have its action set to MESSAGE_DELETE.
     *
     * @param message A {@link Message} message containing the serial identifier.
     * @param operation operation metadata such as clientId, description, or metadata in the version field
     * @throws AblyException If the delete operation fails.
     * @return A {@link UpdateDeleteResult} containing the deleted message version serial.
     */
    @Blocking
    public UpdateDeleteResult deleteMessage(Message message, MessageOperation operation) throws AblyException {
        return messageEditsMixin.deleteMessage(ably.http, message, operation);
    }

    /**
     * Marks a message as deleted.
     * <p>
     * This operation does not remove the message from history; it marks it as deleted
     * while preserving the full message history. The deleted message can still be
     * retrieved and will have its action set to MESSAGE_DELETE.
     *
     * @param message A {@link Message} message containing the serial identifier.
     * @throws AblyException If the delete operation fails.
     * @return A {@link UpdateDeleteResult} containing the deleted message version serial.
     */
    @Blocking
    public UpdateDeleteResult deleteMessage(Message message) throws AblyException {
        return deleteMessage(message, null);
    }

    /**
     * Asynchronously marks a message as deleted.
     *
     * @param message A {@link Message} object containing the serial identifier and operation metadata.
     * @param operation operation metadata such as clientId, description, or metadata in the version field
     * @param callback A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void deleteMessageAsync(Message message, MessageOperation operation, Callback<UpdateDeleteResult> callback) {
        messageEditsMixin.deleteMessageAsync(ably.http, message, operation, callback);
    }

    /**
     * Asynchronously marks a message as deleted.
     *
     * @param message A {@link Message} object containing the serial identifier and operation metadata.
     * @param callback A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void deleteMessageAsync(Message message, Callback<UpdateDeleteResult> callback) {
        deleteMessageAsync(message, null, callback);
    }

    /**
     * Appends message text to the end of the message.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * @param operation operation details such as clientId, description, or metadata
     * @return A {@link UpdateDeleteResult} containing the updated message version serial.
     * @throws AblyException If the append operation fails.
     */
    @Blocking
    public UpdateDeleteResult appendMessage(Message message, MessageOperation operation) throws AblyException {
        return messageEditsMixin.appendMessage(ably.http, message, operation);
    }

    /**
     * Appends message text to the end of the message.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * @return A {@link UpdateDeleteResult} containing the updated message version serial.
     * @throws AblyException If the append operation fails.
     */
    @Blocking
    public UpdateDeleteResult appendMessage(Message message) throws AblyException {
        return appendMessage(message, null);
    }

    /**
     * Asynchronously appends message text to the end of the message.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * @param operation operation details such as clientId, description, or metadata
     * @param callback A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void appendMessageAsync(Message message, MessageOperation operation, Callback<UpdateDeleteResult> callback) {
        messageEditsMixin.appendMessageAsync(ably.http, message, operation, callback);
    }

    /**
     * Asynchronously appends message text to the end of the message.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * @param callback A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void appendMessageAsync(Message message, Callback<UpdateDeleteResult> callback) {
        appendMessageAsync(message, null, callback);
    }

    /**
     * Retrieves all historical versions of a specific message.
     * <p>
     * This method returns a paginated result containing all versions of the message,
     * ordered chronologically. Each version includes metadata about when and by whom
     * the message was modified.
     *
     * @param serial The unique serial identifier of the message.
     * @param params Query parameters for filtering or pagination (e.g., limit, start, end).
     * @return A {@link PaginatedResult} containing an array of {@link Message} objects
     *         representing all versions of the message.
     * @throws AblyException If the versions cannot be retrieved.
     */
    @Blocking
    public PaginatedResult<Message> getMessageVersions(String serial, Param[] params) throws AblyException {
        return messageEditsMixin.getMessageVersions(ably.http, serial, params);
    }

    /**
     * Asynchronously retrieves all historical versions of a specific message.
     *
     * @param serial The unique serial identifier of the message.
     * @param params Query parameters for filtering or pagination.
     * @param callback A callback to handle the result asynchronously.
     */
    @NonBlocking
    public void getMessageVersionsAsync(String serial, Param[] params, Callback<AsyncPaginatedResult<Message>> callback) throws AblyException {
        messageEditsMixin.getMessageVersionsAsync(ably.http, serial, params, callback);
    }

    //endregion

    /******************
     * internal
     * @throws AblyException
     ******************/

    ChannelBase(PubSubBase ably, String name, ChannelOptions options) throws AblyException {
        this.ably = ably;
        this.name = name;
        this.options = options;
        this.basePath = "/channels/" + HttpUtils.encodeURIComponent(name);
        this.presence = new Presence();
        this.annotations = new HttpAnnotations(name, ably.http, ably.options, options);
        this.messageEditsMixin = new MessageEditsMixin(basePath, ably.options, options, ably.auth);
    }

    private final PubSubBase ably;
    private final String basePath;
    ChannelOptions options;

}
