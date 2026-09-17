package io.ably.pubsub.http;

import io.ably.pubsub.types.AblyException;
import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

public class HttpHelpersTest {

    @Test
    public void getUrlString_validResponse_returnsString() throws Exception {
        HttpCore mockHttpCore = mock(HttpCore.class);
        HttpCore.Response mockResponse = new HttpCore.Response();
        mockResponse.body = "Test Response".getBytes();

        when(mockHttpCore.httpExecuteWithRetry(
            eq(new URL("http://example.com")),
            eq("GET"),
            eq(null),
            eq(null),
            any(HttpCore.ResponseHandler.class),
            eq(false)
        )).thenAnswer(invocation -> {
            HttpCore.ResponseHandler<byte[]> responseHandler = invocation.getArgumentAt(4, HttpCore.ResponseHandler.class);
            return responseHandler.handleResponse(mockResponse, null);
        });

        String result = HttpHelpers.getUrlString(mockHttpCore, "http://example.com");
        assertEquals("Test Response", result);
    }

    @Test
    public void getUrlString_emptyResponse_throwsAblyException() throws Exception {
        HttpCore mockHttpCore = mock(HttpCore.class);
        HttpCore.Response mockResponse = new HttpCore.Response();

        when(mockHttpCore.httpExecuteWithRetry(
            eq(new URL("http://example.com")),
            eq("GET"),
            eq(null),
            eq(null),
            any(HttpCore.ResponseHandler.class),
            eq(false)
        )).thenAnswer(invocation -> {
            HttpCore.ResponseHandler<byte[]> responseHandler = invocation.getArgumentAt(4, HttpCore.ResponseHandler.class);
            return responseHandler.handleResponse(mockResponse, null);
        });

        AblyException e = assertThrows(AblyException.class, () -> HttpHelpers.getUrlString(mockHttpCore, "http://example.com"));
        assertEquals(500, e.errorInfo.statusCode);
        assertEquals(50000, e.errorInfo.code);
        assertEquals("Empty response body", e.errorInfo.message);
    }
}
