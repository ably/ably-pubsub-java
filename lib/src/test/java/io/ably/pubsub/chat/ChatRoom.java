package io.ably.pubsub.chat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.HttpPaginatedResponse;
import io.ably.pubsub.types.Param;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class ChatRoom {
    private final PubSubHttpClient pubSubHttpClient;
    private final String roomId;
    private final Gson gson = new Gson();

    protected ChatRoom(String roomId, PubSubHttpClient pubSubHttpClient) {
        this.roomId = roomId;
        this.pubSubHttpClient = pubSubHttpClient;
    }

    public JsonElement sendMessage(SendMessageParams params) throws Exception {
        return makeAuthorizedRequest("/chat/v2/rooms/" + roomId + "/messages", "POST", gson.toJsonTree(params))
            .orElseThrow(() -> AblyException.fromErrorInfo(new ErrorInfo("Failed to send message", 500)));
    }

    public JsonElement updateMessage(String serial, UpdateMessageParams params) throws Exception {
        return makeAuthorizedRequest("/chat/v2/rooms/" + roomId + "/messages/" + serial, "PUT", gson.toJsonTree(params))
            .orElseThrow(() -> AblyException.fromErrorInfo(new ErrorInfo("Failed to update message", 500)));
    }

    public JsonElement deleteMessage(String serial, DeleteMessageParams params) throws Exception {
        return makeAuthorizedRequest("/chat/v2/rooms/" + roomId + "/messages/" + serial + "/delete", "POST", gson.toJsonTree(params))
            .orElseThrow(() -> AblyException.fromErrorInfo(new ErrorInfo("Failed to delete message", 500)));
    }

    public static class SendMessageParams {
        public String text;
        public JsonObject metadata;
        public Map<String, String> headers;
    }

    public static class UpdateMessageParams {
        public SendMessageParams message;
        public String description;
        public Map<String, String> metadata;
    }

    public static class DeleteMessageParams {
        public String description;
        public Map<String, String> metadata;
    }

    protected Optional<JsonElement> makeAuthorizedRequest(String url, String method, JsonElement body) throws AblyException {
        HttpCore.RequestBody httpRequestBody = HttpUtils.requestBodyFromGson(body, pubSubHttpClient.options.useBinaryProtocol);
        HttpPaginatedResponse response = pubSubHttpClient.request(method, url, new Param[] { new Param("v", 3) }, httpRequestBody, null);
        return Arrays.stream(response.items()).findFirst();
    }
}
