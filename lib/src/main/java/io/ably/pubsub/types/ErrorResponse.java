package io.ably.pubsub.types;

import io.ably.pubsub.util.Serialisation;

public class ErrorResponse {
    public ErrorInfo error;

    /**
     * Get an ErrorInfo from a response body with error details
     * @param jsonText
     * @return
     * @throws AblyException
     */
    public static ErrorResponse fromJSON(String jsonText) throws AblyException {
        ErrorResponse errorResponse = (ErrorResponse)Serialisation.gson.fromJson(jsonText, ErrorResponse.class);
        return errorResponse;
    }
}
