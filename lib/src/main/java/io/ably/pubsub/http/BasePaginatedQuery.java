package io.ably.pubsub.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.AsyncPaginatedResult;
import io.ably.pubsub.types.BasePaginatedResult;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;


/**
 * A paginated query base implementation that can be used sync or asynchronously.
 *
 * @param <T>
 */
public class BasePaginatedQuery<T> implements HttpCore.ResponseHandler<BasePaginatedResult<T>> {

    /**
     * Construct a PaginatedQuery
     *
     * @param http the http instance
     * @param path the path of the resource being queried
     * @param headers headers to pass into the first and all relative queries
     * @param params params to pass into the initial query
     * @param bodyHandler handler to parse response bodies for first and all relative queries
     */
    public BasePaginatedQuery(Http http, String path, Param[] headers, Param[] params, HttpCore.BodyHandler<T> bodyHandler) {
        this(http, path, headers, params, null, bodyHandler);
    }

    /**
     * Construct a PaginatedQuery
     *
     * @param http the http instance
     * @param path the path of the resource being queried
     * @param headers headers to pass into the first and all relative queries
     * @param params params to pass into the initial query
     * @param bodyHandler handler to parse response bodies for first and all relative queries
     */
    public BasePaginatedQuery(Http http, String path, Param[] headers, Param[] params, HttpCore.RequestBody requestBody, HttpCore.BodyHandler<T> bodyHandler) {
        this.http = http;
        this.path = path;
        this.requestHeaders = headers;
        this.requestParams = params;
        this.requestBody = requestBody;
        this.bodyHandler = bodyHandler;
    }

    /**
     * Get the result of the first query
     * @return A ResultRequest<T> giving the first page of results
     * together with any available links to related results pages.
     */
    public ResultRequest<T> get() {
        return new ResultRequest<T>(this.exec(HttpConstants.Methods.GET));
    }

    /**
     * Get the result of the first query
     * @return A Http.Request<BasePaginatedResult<T>> giving the first page of results
     * together with any available links to related results pages.
     */
    public Http.Request<BasePaginatedResult<T>> exec(final String method) {
        final HttpCore.ResponseHandler<BasePaginatedResult<T>> responseHandler = this;
        return http.request(new Http.Execute<BasePaginatedResult<T>>() {
            @Override
            public void execute(HttpScheduler http, Callback<BasePaginatedResult<T>> callback) throws AblyException {
                http.exec(path, method, requestHeaders, requestParams, requestBody, responseHandler, true, callback);
            }
        });
    }

    /**
     * A private class encapsulating the result of a single page response
     */
    private class ResultPage implements BasePaginatedResult<T> {
        private T[] contents;

        private ResultPage(T[] contents, Collection<String> linkHeaders) throws AblyException {
            this.contents = contents;

            if(linkHeaders != null) {
                HashMap<String, String> links = parseLinks(linkHeaders);
                relFirst = links.get("first");
                relCurrent = links.get("current");
                relNext = links.get("next");
            }
        }

        @Override
        public T[] items() { return contents; }

        @Override
        public Http.Request<BasePaginatedResult<T>> first() { return getRel(relFirst); }

        @Override
        public Http.Request<BasePaginatedResult<T>> current() { return getRel(relCurrent); }

        @Override
        public Http.Request<BasePaginatedResult<T>> next() { return getRel(relNext); }

        private Http.Request<BasePaginatedResult<T>> getRel(final String linkUrl) {
            return http.request(new Http.Execute<BasePaginatedResult<T>>() {
                @Override
                public void execute(HttpScheduler http, Callback<BasePaginatedResult<T>> callback) throws AblyException {
                    if(linkUrl == null) {
                        callback.onSuccess(null);
                        return;
                    }
                    /* we're expecting the format to be ./path-component?name=value&name=value... */
                    Matcher urlMatch = urlPattern.matcher(linkUrl);
                    if(!urlMatch.matches()) {
                        throw AblyException.fromErrorInfo(new ErrorInfo("Unexpected link URL format", 500, 50000));
                    }
                    String[] paramSpecs = urlMatch.group(2).split("&");
                    Param[] params = new Param[paramSpecs.length];
                    try {
                        for(int i = 0; i < paramSpecs.length; i++) {
                            String[] split = paramSpecs[i].split("=");
                            params[i] = new Param(split[0], URLDecoder.decode(split[1], "UTF-8"));
                        }
                    } catch(UnsupportedEncodingException uee) {}
                    http.get(path, requestHeaders, params, BasePaginatedQuery.this, true, callback);
                }
            });
        }

        private String relFirst, relCurrent, relNext;

        @Override
        public boolean hasFirst() { return relFirst != null; }

        @Override
        public boolean hasCurrent() { return relCurrent != null; }

        @Override
        public boolean hasNext() { return relNext != null; }

        @Override
        public boolean isLast() {
            return relNext == null;
        }
    }

    @Override
    public BasePaginatedResult<T> handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
        if(error != null) {
            throw AblyException.fromErrorInfo(error);
        }
        T[] responseContents = bodyHandler.handleResponseBody(response.contentType, response.body);
        return new BasePaginatedQuery.ResultPage(responseContents, response.getHeaderFields(HttpConstants.Headers.LINK));
    }

    /****************
     * internal
     ****************/

    protected static Pattern linkPattern = Pattern.compile("\\s*<(.*)>;\\s*rel=\"(.*)\"");
    protected static Pattern urlPattern = Pattern.compile("\\./(.*)\\?(.*)");

    protected static HashMap<String, String> parseLinks(Collection<String> linkHeaders) {
        HashMap<String, String> result = new HashMap<String, String>();
        for(String link : linkHeaders) {
            /* we're expecting the format to be <url>; rel="first current ..." */
            Matcher linkMatch = linkPattern.matcher(link);
            if(linkMatch.matches()) {
                String linkUrl = linkMatch.group(1);
                for (String linkRel : linkMatch.group(2).toLowerCase(Locale.ENGLISH).split("\\s"))
                    result.put(linkRel, linkUrl);
            }
        }
        return result;
    }

    private final Http http;
    private final String path;
    private final Param[] requestHeaders;
    private final Param[] requestParams;
    private final HttpCore.RequestBody requestBody;
    private final HttpCore.BodyHandler<T> bodyHandler;

    /**
     * Wraps a Http.Request<BasePaginatedResult<T>> to fixate on either a sync or an async interface.
     *
     * The Http.Request gives you a BasePaginatedResult<T> whether you call its sync() or its async()
     * methods, but we'd like to give it us a PaginatedResult<T> or a AsyncPaginatedResult<T>
     * respectively, so this does the necessary bridging.
     *
     * @param <T>
     */
    public static class ResultRequest<T> {
        private final Http.Request<BasePaginatedResult<T>> wrappedRequest;

        private ResultRequest(Http.Request<BasePaginatedResult<T>> wrappedRequest) {
            this.wrappedRequest = wrappedRequest;
        }

        public PaginatedResult<T> sync() throws AblyException {
            return new SyncResultPage<T>(wrappedRequest.sync());
        }

        public void async(final Callback<AsyncPaginatedResult<T>> callback) {
            wrappedRequest.async(new Callback<BasePaginatedResult<T>>() {
                @Override
                public void onSuccess(BasePaginatedResult<T> result) {
                    callback.onSuccess(new AsyncResultPage<T>(result));
                }

                @Override
                public void onError(ErrorInfo reason) {
                    callback.onError(reason);
                }
            });
        }

        /**
         * A ResultRequest that has already failed due to a previous condition.
         *
         * Useful when a method must return a ResultRequest but fails before it can make the "real"
         * one. Such errors are reported as thrown AblyExceptions in sync scenarios, and as
         * Callback.onError calls in async ones. This class helps converting from plain exceptions
         * to cover the async case too.
         *
         * @param <T>
         */
        public static class Failed<T> extends ResultRequest<T> {
            private final AblyException reason;

            public Failed(AblyException reason) {
                super(null);
                this.reason = reason;
            }

            @Override
            public PaginatedResult<T> sync() throws AblyException {
                throw reason;
            }

            @Override
            public void async(Callback<AsyncPaginatedResult<T>> callback) {
                callback.onError(reason.errorInfo);
            }
        }
    }

    /**
     * Base class for SyncResultPage and AsyncResultPage. For code sharing purposes only.
     * @param <T>
     */
    private static abstract class ResultPageWrapper<T> {
        protected final BasePaginatedResult<T> resultBase;

        protected ResultPageWrapper(BasePaginatedResult<T> resultBase) {
            this.resultBase = resultBase;
        }

        public T[] items() { return resultBase.items(); }

        public boolean hasFirst() { return resultBase.hasFirst(); }

        public boolean hasCurrent() { return resultBase.hasCurrent(); }

        public boolean hasNext() { return resultBase.hasNext(); }

        public boolean isLast() {
            return resultBase.isLast();
        }
    }

    /**
     * Bridge from BasePaginatedResult to the synchronous PaginatedResult.
     * @param <T>
     */
    private static class SyncResultPage<T> extends ResultPageWrapper<T> implements PaginatedResult<T> {
        SyncResultPage(BasePaginatedResult<T> resultBase) {
            super(resultBase);
        }

        @Override
        public PaginatedResult<T> first() throws AblyException { return new SyncResultPage(resultBase.first().sync()); }

        @Override
        public PaginatedResult<T> current() throws AblyException { return new SyncResultPage(resultBase.current().sync()); }

        @Override
        public PaginatedResult<T> next() throws AblyException { return new SyncResultPage(resultBase.next().sync()); }
    }

    /**
     * Bridge from BasePaginatedResult to the asynchronous AsyncPaginatedResult.
     * @param <T>
     */
    private static class AsyncResultPage<T> extends ResultPageWrapper<T> implements AsyncPaginatedResult<T> {
        AsyncResultPage(BasePaginatedResult<T> resultBase) {
            super(resultBase);
        }

        @Override
        public void first(Callback<AsyncPaginatedResult<T>> callback) {
            resultBase.first().async(new CallbackBridge(callback));
        }

        @Override
        public void current(Callback<AsyncPaginatedResult<T>> callback) {
            resultBase.current().async(new CallbackBridge(callback));
        }

        @Override
        public void next(Callback<AsyncPaginatedResult<T>> callback) {
            resultBase.next().async(new CallbackBridge(callback));
        }
    }

    /**
     * Bridge from Callback<AsyncPaginatedResult<T>> to Callback<BasePaginatedResult<T>>.
     *
     * @param <T>
     */
    private static class CallbackBridge<T> implements Callback<BasePaginatedResult<T>> {
        private final Callback<AsyncPaginatedResult<T>> callback;

        CallbackBridge(Callback<AsyncPaginatedResult<T>> callback) {
            this.callback = callback;
        }

        @Override
        public void onSuccess(BasePaginatedResult<T> result) {
            this.callback.onSuccess(new AsyncResultPage(result));
        }

        @Override
        public void onError(ErrorInfo reason) {
            callback.onError(reason);
        }
    }
}
