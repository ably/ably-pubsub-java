package io.ably.pubsub.test.util;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

import java.io.InputStream;
import java.util.Map;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

public class TimeHandler extends RouterNanoHTTPD.DefaultStreamHandler {
    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public NanoHTTPD.Response.IStatus getStatus() {
        throw new IllegalStateException("this method should not be called in a time handler");
    }

    @Override
    public InputStream getData() {
        throw new IllegalStateException("this method should not be called in a time handler");
    }

    @Override
    public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
        try {
            long delay = 5000L;
            Thread.sleep(delay);
        } catch(InterruptedException ie) {}

        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, getMimeType(), "[" + System.currentTimeMillis() + "]");
    }

}
