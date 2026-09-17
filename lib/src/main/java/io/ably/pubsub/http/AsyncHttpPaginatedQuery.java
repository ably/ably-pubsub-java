package io.ably.pubsub.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;

import com.google.gson.JsonElement;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.AsyncHttpPaginatedResponse;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Param;

public class AsyncHttpPaginatedQuery implements HttpCore.ResponseHandler<AsyncHttpPaginatedResponse> {

    public AsyncHttpPaginatedQuery(Http http, String method, String path, Param[] headers, Param[] params,
                                   HttpCore.RequestBody requestBody) {
        this.http = http;
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.params = params;
        this.requestBody = requestBody;
        this.bodyHandler = HttpPaginatedQuery.jsonArrayResponseHandler;
    }

    public void exec(final AsyncHttpPaginatedResponse.Callback callback) {
        exec(params, callback);
    }

    public void exec(final Param[] params, final AsyncHttpPaginatedResponse.Callback callback) {
        final HttpCore.ResponseHandler<AsyncHttpPaginatedResponse> responseHandler = this;
        http.request(new Http.Execute<AsyncHttpPaginatedResponse>() {
            @Override
            public void execute(HttpScheduler http, Callback<AsyncHttpPaginatedResponse> callback) throws AblyException {
                http.exec(path, method, headers, params, requestBody, responseHandler, true, callback);
            }
        }).async(wrap(callback));
    }

    /**
     * A private class encapsulating the result of a single page response
     *
     */
    public class AsyncHttpPaginatedResult extends AsyncHttpPaginatedResponse {
        private JsonElement[] contents;

        private AsyncHttpPaginatedResult(HttpCore.Response response, ErrorInfo error) {
            statusCode = response.statusCode;
            headers = HttpUtils.toParamArray(response.headers);
            if(error != null) {
                errorCode = error.code;
                errorMessage = error.message;
            } else {
                success = true;
                if(response.body != null) {
                    try {
                        contents = bodyHandler.handleResponseBody(response.contentType, response.body);
                    } catch (AblyException e) {
                        success = false;
                        errorCode = e.errorInfo.code;
                        errorMessage = e.errorInfo.message;
                    }
                }
            }

            List<String> linkHeaders = response.getHeaderFields(HttpConstants.Headers.LINK);
            if(linkHeaders != null) {
                HashMap<String, String> links = BasePaginatedQuery.parseLinks(linkHeaders);
                relFirst = links.get("first");
                relCurrent = links.get("current");
                relNext = links.get("next");
            } else {
                relFirst = null;
                relCurrent = null;
                relNext = null;
            }
        }

        @Override
        public JsonElement[] items() { return contents; }

        @Override
        public void first(AsyncHttpPaginatedResponse.Callback callback) {
            execRel(relFirst, callback);
        }

        @Override
        public void current(AsyncHttpPaginatedResponse.Callback callback) {
            execRel(relCurrent, callback);
        }

        @Override
        public void next(AsyncHttpPaginatedResponse.Callback callback) {
            execRel(relNext, callback);
        }

        private void execRel(String linkUrl, AsyncHttpPaginatedResponse.Callback callback) {
            if(linkUrl == null) {
                callback.onResponse(null);
                return;
            }

            /* we're expecting the format to be ./path-component?name=value&name=value... */
            Matcher urlMatch = BasePaginatedQuery.urlPattern.matcher(linkUrl);
            if(!urlMatch.matches()) {
                callback.onError(new ErrorInfo("Unexpected link URL format", 500, 50000));
                return;
            }

            String[] paramSpecs = urlMatch.group(2).split("&");
            Param[] params = new Param[paramSpecs.length];
            try {
                for(int i = 0; i < paramSpecs.length; i++) {
                    String[] split = paramSpecs[i].split("=");
                    String paramKey = split[0];
                    String paramValue = (split.length >= 2) ? split[1] : "";
                    params[i] = new Param(paramKey, URLDecoder.decode(paramValue, "UTF-8"));
                }
            } catch(UnsupportedEncodingException uee) {}
            exec(params, callback);
        }
    
        @Override
        public boolean hasFirst() { return relFirst != null; }

        @Override
        public boolean hasCurrent() { return relCurrent != null; }

        @Override
        public boolean hasNext() { return relNext != null; }

        private final String relFirst, relCurrent, relNext;
    }

    @Override
    public AsyncHttpPaginatedResponse handleResponse(HttpCore.Response response, ErrorInfo error) {
        return new AsyncHttpPaginatedResult(response, error);
    }

    private static Callback<AsyncHttpPaginatedResponse> wrap(final AsyncHttpPaginatedResponse.Callback callback) {
        return new Callback<AsyncHttpPaginatedResponse>() {
            @Override
            public void onSuccess(AsyncHttpPaginatedResponse result) {
                callback.onResponse(result);
            }
            @Override
            public void onError(ErrorInfo reason) {
                callback.onError(reason);
            }
        };
    }

    private final Http http;
    private final String method;
    private final String path;
    private final Param[] headers;
    private final Param[] params;
    private final HttpCore.RequestBody requestBody;
    private final HttpCore.BodyHandler<JsonElement> bodyHandler;
}
