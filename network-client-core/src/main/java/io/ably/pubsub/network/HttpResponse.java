package io.ably.pubsub.network;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Data
@Setter(AccessLevel.NONE)
@Builder
@AllArgsConstructor
public class HttpResponse {
    private final int code;
    private final String message;
    private final HttpBody body;
    private final Map<String, List<String>> headers;
}
