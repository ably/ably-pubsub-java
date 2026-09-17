package io.ably.pubsub.network;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Setter(AccessLevel.NONE)
@AllArgsConstructor
public class HttpRequest {

    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";

    private final URL url;
    private final String method;
    private final int httpOpenTimeout;
    private final int httpReadTimeout;
    private final HttpBody body;
    @Getter(AccessLevel.NONE)
    private final Map<String, List<String>> headers;

    public Map<String, List<String>> getHeaders() {
        Map<String, List<String>> headersCopy = new HashMap<>(headers);
        if (body != null) {
            int length = body.getContent() == null ? 0 : body.getContent().length;
            headersCopy.put(CONTENT_TYPE, Collections.singletonList(body.getContentType()));
            headersCopy.put(CONTENT_LENGTH, Collections.singletonList(Integer.toString(length)));
        }
        return headersCopy;
    }

    public static HttpRequestBuilder builder() {
        return new HttpRequestBuilder();
    }

    public static class HttpRequestBuilder {
        private URL url;
        private String method;
        private int httpOpenTimeout;
        private int httpReadTimeout;
        private HttpBody body;
        private Map<String, List<String>> headers;

        HttpRequestBuilder() {
        }

        public HttpRequestBuilder url(URL url) {
            this.url = url;
            return this;
        }

        public HttpRequestBuilder method(String method) {
            this.method = method;
            return this;
        }

        public HttpRequestBuilder httpOpenTimeout(int httpOpenTimeout) {
            this.httpOpenTimeout = httpOpenTimeout;
            return this;
        }

        public HttpRequestBuilder httpReadTimeout(int httpReadTimeout) {
            this.httpReadTimeout = httpReadTimeout;
            return this;
        }

        public HttpRequestBuilder body(HttpBody body) {
            this.body = body;
            return this;
        }

        public HttpRequestBuilder headers(Map<String, String> headers) {
            Map<String, List<String>> result = new HashMap<>();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                result.put(key, Collections.singletonList(value));
            }
            this.headers = Collections.unmodifiableMap(result);
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(this.url, this.method, this.httpOpenTimeout, this.httpReadTimeout, this.body, this.headers);
        }

        public String toString() {
            return "HttpRequest.HttpRequestBuilder(url=" + this.url + ", method=" + this.method + ", httpOpenTimeout=" + this.httpOpenTimeout + ", httpReadTimeout=" + this.httpReadTimeout + ", body=" + this.body + ", headers=" + this.headers + ")";
        }
    }
}
