package io.ably.pubsub.http;

import io.ably.pubsub.http.BasePaginatedQuery;
import io.ably.pubsub.http.Http;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.AsyncPaginatedResult;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.MessageAction;
import io.ably.pubsub.types.MessageOperation;
import io.ably.pubsub.types.MessageSerializer;
import io.ably.pubsub.types.MessageVersion;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.UpdateDeleteResult;
import io.ably.pubsub.util.Crypto;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;

public class MessageEditsMixin {

    private final String basePath;

    private final ClientOptions clientOptions;

    private final ChannelOptions channelOptions;

    private final Auth auth;

    public MessageEditsMixin(String basePath, ClientOptions clientOptions, ChannelOptions channelOptions, Auth auth) {
        this.basePath = basePath;
        this.clientOptions = clientOptions;
        this.channelOptions = channelOptions;
        this.auth = auth;
    }

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
    public Message getMessage(Http http, String serial) throws AblyException {
        return getMessageImpl(http, serial).sync();
    }

    /**
     * Asynchronously retrieves the latest version of a specific message by its serial identifier.
     *
     * @param serial   The unique serial identifier of the message to retrieve.
     * @param callback A callback to handle the result asynchronously.
     *                 <p>
     *                 This callback is invoked on a background thread.
     */
    @NonBlocking
    public void getMessageAsync(Http http, String serial, Callback<Message> callback) {
        getMessageImpl(http, serial).async(callback);
    }

    private Http.Request<Message> getMessageImpl(Http http, String serial) {
        return http.request((scheduler, callback) -> {
            if (serial == null || serial.isEmpty()) {
                throw AblyException.fromErrorInfo(new ErrorInfo("Message serial cannot be empty", 400, 40003));
            }
            HttpCore.BodyHandler<Message> bodyHandler = MessageSerializer.getSingleMessageResponseHandler(channelOptions);
            final Param[] params = clientOptions.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null;
            scheduler.get(basePath + "/messages/" + HttpUtils.encodeURIComponent(serial),
                HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol),
                params,
                (response, error) -> {
                    if (error != null) throw AblyException.fromErrorInfo(error);
                    Message[] messages = bodyHandler.handleResponseBody(response.contentType, response.body);
                    if (messages != null && messages.length > 0) return messages[0];
                    throw AblyException.fromErrorInfo(new ErrorInfo("Message not found", 404, 40400));
                },
                true,
                callback);
        });
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
     *
     * @return A {@link UpdateDeleteResult} containing the updated message version serial.
     */
    @Blocking
    public UpdateDeleteResult updateMessage(Http http, Message message, MessageOperation operation) throws AblyException {
        return updateMessageImpl(http, message, operation, MessageAction.MESSAGE_UPDATE).sync();
    }

    /**
     * Asynchronously updates an existing message.
     *
     * @param message  A {@link Message} object containing the fields to update and the serial identifier.
     * @param callback A listener to be notified of the outcome of this operation.
     *                 <p>
     *                 This listener is invoked on a background thread.
     */
    @NonBlocking
    public void updateMessageAsync(Http http, Message message, MessageOperation operation, Callback<UpdateDeleteResult> callback) {
        updateMessageImpl(http, message, operation, MessageAction.MESSAGE_UPDATE).async(callback);
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
     *
     * @return A {@link UpdateDeleteResult} containing the deleted message version serial.
     */
    @Blocking
    public UpdateDeleteResult deleteMessage(Http http, Message message, MessageOperation operation) throws AblyException {
        return updateMessageImpl(http, message, operation, MessageAction.MESSAGE_DELETE).sync();
    }

    /**
     * Asynchronously marks a message as deleted.
     *
     * @param message  A {@link Message} object containing the serial identifier and operation metadata.
     * @param callback A listener to be notified of the outcome of this operation.
     *                 <p>
     *                 This listener is invoked on a background thread.
     */
    @NonBlocking
    public void deleteMessageAsync(Http http, Message message, MessageOperation operation, Callback<UpdateDeleteResult> callback) {
        updateMessageImpl(http, message, operation, MessageAction.MESSAGE_DELETE).async(callback);
    }

    /**
     * Appends message text to the end of the message.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * @param operation operation details such as clientId, description, or metadata
     * @return A {@link UpdateDeleteResult} containing the updated message version serial.
     */
    @Blocking
    public UpdateDeleteResult appendMessage(Http http, Message message, MessageOperation operation) throws AblyException {
        return updateMessageImpl(http, message, operation, MessageAction.MESSAGE_APPEND).sync();
    }

    /**
     * Asynchronously appends message text to the end of the message.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * @param operation operation details such as clientId, description, or metadata
     * @param callback A listener to be notified of the outcome of this operation.
     *                 <p>
     *                 This listener is invoked on a background thread.
     */
    @NonBlocking
    public void appendMessageAsync(Http http, Message message, MessageOperation operation, Callback<UpdateDeleteResult> callback) {
        updateMessageImpl(http, message, operation, MessageAction.MESSAGE_APPEND).async(callback);
    }

    private Http.Request<UpdateDeleteResult> updateMessageImpl(Http http, Message message, MessageOperation operation, MessageAction action) {
        return http.request((scheduler, callback) -> {
            if (message.serial == null || message.serial.isEmpty()) {
                throw AblyException.fromErrorInfo(new ErrorInfo("Message serial cannot be empty", 400, 40003));
            }
            /* RTL6g3 */
            auth.checkClientId(message, true, false);

            Message updatedMessage = new Message(message.name, message.data, message.extras);
            updatedMessage.action = action;
            updatedMessage.version = new MessageVersion();
            if (operation != null) {
                updatedMessage.version.clientId = operation.clientId;
                updatedMessage.version.description = operation.description;
                updatedMessage.version.metadata = operation.metadata;
            }
            updatedMessage.encode(channelOptions);

            HttpCore.RequestBody requestBody = clientOptions.useBinaryProtocol
                ? MessageSerializer.asSingleMsgpackRequest(updatedMessage)
                : MessageSerializer.asSingleJsonRequest(updatedMessage);
            final Param[] params = clientOptions.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null;

            HttpCore.BodyHandler<UpdateDeleteResult> bodyHandler = UpdateDeleteResult.getBodyHandler();

            scheduler.patch(basePath + "/messages/" + HttpUtils.encodeURIComponent(message.serial),
                HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol),
                params,
                requestBody,
                (response, error) -> {
                    if (error != null) throw AblyException.fromErrorInfo(error);
                    UpdateDeleteResult[] results = bodyHandler.handleResponseBody(response.contentType, response.body);
                    if (results != null && results.length > 0) return results[0];
                    throw AblyException.fromErrorInfo(new ErrorInfo("No versionSerial in the response", 500, 50000));
                },
                true,
                callback);
        });
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
     * representing all versions of the message.
     * @throws AblyException If the versions cannot be retrieved.
     */
    @Blocking
    public PaginatedResult<Message> getMessageVersions(Http http, String serial, Param[] params) throws AblyException {
        return getMessageVersionsImpl(http, serial, params).sync();
    }

    /**
     * Asynchronously retrieves all historical versions of a specific message.
     *
     * @param serial   The unique serial identifier of the message.
     * @param params   Query parameters for filtering or pagination.
     * @param callback A callback to handle the result asynchronously.
     */
    @NonBlocking
    public void getMessageVersionsAsync(Http http, String serial, Param[] params, Callback<AsyncPaginatedResult<Message>> callback) throws AblyException {
        getMessageVersionsImpl(http, serial, params).async(callback);
    }

    private BasePaginatedQuery.ResultRequest<Message> getMessageVersionsImpl(Http http, String serial, Param[] initialParams) throws AblyException {
        if (serial == null || serial.isEmpty()) {
            throw AblyException.fromErrorInfo(new ErrorInfo("Message serial cannot be empty", 400, 40003));
        }
        HttpCore.BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(channelOptions);
        final Param[] params = clientOptions.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams;
        return (new BasePaginatedQuery<>(http, basePath + "/messages/" + HttpUtils.encodeURIComponent(serial) + "/versions",
            HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol), params, bodyHandler)).get();
    }

}
