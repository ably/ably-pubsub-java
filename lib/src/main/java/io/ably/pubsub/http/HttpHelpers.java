package io.ably.pubsub.http;

import java.net.URL;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Param;

import static io.ably.pubsub.http.HttpUtils.buildURL;

public class HttpHelpers {
    /**
     * Make a synchronous HTTP request to an Ably endpoint, using the Ably auth credentials and fallback hosts if necessary
     * @param http
     * @param path
     * @param method
     * @param headers
     * @param params
     * @param requestBody
     * @param responseHandler
     * @return
     * @throws AblyException
     */
    public static <T> T ablyHttpExecute(Http http, final String path, final String method, final Param[] headers, final Param[] params, final HttpCore.RequestBody requestBody, final HttpCore.ResponseHandler<T> responseHandler, final boolean requireAblyAuth) throws AblyException {
        return http.request(new Http.Execute<T>() {
            @Override
            public void execute(HttpScheduler http, Callback<T> callback) throws AblyException {
                http.exec(path, method, headers, params, requestBody, responseHandler, requireAblyAuth, callback);
            }
        }).sync();
    }

    private static final String TAG = HttpHelpers.class.getName();

    /**
     * Simple HTTP GET; no auth, headers, returning response body as string
     * @param httpCore
     * @param url
     * @return
     * @throws AblyException
     */
    public static String getUrlString(HttpCore httpCore, String url) throws AblyException {
        byte[] bytes = getUrl(httpCore, url);
        if (bytes == null) {
            throw AblyException.fromErrorInfo(new ErrorInfo("Empty response body", 500, 50000));
        }
        return new String(bytes);
    }

    /**
     * Simple HTTP GET; no auth, headers, returning response body as byte[]
     * @param httpCore
     * @param url
     * @return
     * @throws AblyException
     */
    public static byte[] getUrl(HttpCore httpCore, String url) throws AblyException {
        try {
            return httpExecute(httpCore, new URL(url), HttpConstants.Methods.GET, null, null, new HttpCore.ResponseHandler<byte[]>() {
                @Override
                public byte[] handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                    if(error != null) {
                        throw AblyException.fromErrorInfo(error);
                    }
                    return response.body;
                }});
        } catch (Exception e) {
            throw AblyException.fromThrowable(e);
        }
    }

    /**
     * HTTP GET for non-Ably host
     * @param uri
     * @param headers
     * @param params
     * @param responseHandler
     * @return
     * @throws AblyException
     */
    public static <T> T getUri(HttpCore httpCore, String uri, Param[] headers, Param[] params, HttpCore.ResponseHandler<T> responseHandler) throws AblyException {
        return httpExecute(httpCore, buildURL(uri, params), HttpConstants.Methods.GET, headers, null, responseHandler);
    }

    /**
     * HTTP POST with data in form encoding for non-Ably host
     * @param uri
     * @param headers
     * @param queryParams
     * @param responseHandler
     * @return
     * @throws AblyException
     */
    public static <T> T postUri(HttpCore httpCore, String uri, Param[] headers, Param[] queryParams, Param[] bodyParams, HttpCore.ResponseHandler<T> responseHandler) throws AblyException {
        return httpExecute(httpCore, buildURL(uri, queryParams), HttpConstants.Methods.POST, headers, new HttpUtils.FormRequestBody(bodyParams), responseHandler);
    }

    /**
     * Make a synchronous HTTP request to non-Ably endpoint, specified by URL and using the configured proxy, if any
     * @param httpCore
     * @param url
     * @param method
     * @param headers
     * @param requestBody
     * @param responseHandler
     * @return
     * @throws AblyException
     */
    public static <T> T httpExecute(HttpCore httpCore, URL url, String method, Param[] headers, HttpCore.RequestBody requestBody, HttpCore.ResponseHandler<T> responseHandler) throws AblyException {
        return httpCore.httpExecuteWithRetry(url, method, headers, requestBody, responseHandler, false);
    }

    public static <T> T postSync(final Http http, final String path, final Param[] headers, final Param[] params, final HttpCore.RequestBody requestBody, final HttpCore.ResponseHandler<T> responseHandler, final boolean requireAblyAuth) throws AblyException {
        return http.request(new Http.Execute<T>() {
            @Override
            public void execute(HttpScheduler http, Callback<T> callback) throws AblyException {
                http.post(path, headers, params, requestBody, responseHandler, requireAblyAuth, callback);
            }
        }).sync();
    }
}
