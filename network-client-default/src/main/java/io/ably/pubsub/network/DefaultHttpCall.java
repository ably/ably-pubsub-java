package io.ably.pubsub.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class DefaultHttpCall implements HttpCall {
    private final Proxy proxy;
    private final HttpRequest request;
    private HttpURLConnection connection;

    DefaultHttpCall(HttpRequest request, Proxy proxy) {
        this.request = request;
        this.proxy = proxy;
    }

    @Override
    public HttpResponse execute() {
        URL url = request.getUrl();
        try {
            connection = (HttpURLConnection) url.openConnection(proxy);
            /* prepare connection */
            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(request.getHttpOpenTimeout());
            connection.setReadTimeout(request.getHttpReadTimeout());
            connection.setDoInput(true);

            for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
                String headerName = entry.getKey();
                List<String> values = entry.getValue();
                for (String headerValue : values) {
                    connection.setRequestProperty(headerName, headerValue);
                }
            }

            /* prepare request body */
            if (request.getBody() != null) {
                byte[] body = prepareRequestBody(request.getBody());
                writeRequestBody(body);
            }

            return readResponse();
        } catch (ConnectException | SocketTimeoutException | UnknownHostException | NoRouteToHostException fce) {
            throw new FailedConnectionException(fce);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            cancel();
        }
    }

    @Override
    public void cancel() {
        if (connection != null) {
            connection.disconnect();
        }
    }

    /**
     * Emit the request body for an HTTP request
     */
    private byte[] prepareRequestBody(HttpBody requestBody) throws IOException {
        connection.setDoOutput(true);
        byte[] body = requestBody.getContent();
        int length = body.length;
        connection.setFixedLengthStreamingMode(length);
        return body;
    }


    private void writeRequestBody(byte[] body) throws IOException {
        OutputStream os = connection.getOutputStream();
        os.write(body);
    }

    private HttpResponse readResponse() throws IOException {
        HttpResponse.HttpResponseBuilder builder = HttpResponse.builder();
        int statusCode = connection.getResponseCode();

        builder
            .code(statusCode)
            .message(connection.getResponseMessage());

        /* Store all header field names in lower-case to eliminate case insensitivity */
        Map<String, List<String>> caseSensitiveHeaders = connection.getHeaderFields();
        Map<String, List<String>> headers = new HashMap<>(caseSensitiveHeaders.size(), 1f);

        for (Map.Entry<String, List<String>> entry : caseSensitiveHeaders.entrySet()) {
            if (entry.getKey() != null) {
                headers.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }

        builder.headers(headers);

        if (statusCode == HttpURLConnection.HTTP_NO_CONTENT) {
            return builder.build();
        }

        String contentType = connection.getContentType();
        int contentLength = connection.getContentLength();

        InputStream is = null;
        try {
            is = connection.getInputStream();
        } catch (Throwable ignored) {}

        if (is == null) is = connection.getErrorStream();

        try {
            byte[] body = readInputStream(is, contentLength);
            builder.body(new HttpBody(contentType, body));
        } catch (NullPointerException e) {
            /* nothing to read */
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }

        return builder.build();
    }

    private byte[] readInputStream(InputStream inputStream, int bytes) throws IOException {
        /* If there is nothing to read */
        if (inputStream == null) {
            throw new NullPointerException("inputStream == null");
        }

        int bytesRead = 0;

        if (bytes == -1) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4 * 1024];
            while ((bytesRead = inputStream.read(buffer)) > -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return outputStream.toByteArray();
        } else {
            int idx = 0;
            byte[] output = new byte[bytes];
            while ((bytesRead = inputStream.read(output, idx, bytes - idx)) > -1) {
                idx += bytesRead;
            }

            return output;
        }
    }
}
