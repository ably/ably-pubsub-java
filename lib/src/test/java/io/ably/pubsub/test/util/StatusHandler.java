package io.ably.pubsub.test.util;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

import java.io.InputStream;
import java.util.Map;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

/**
 * Created by gokhanbarisaker on 2/11/16.
 */
public class StatusHandler extends RouterNanoHTTPD.DefaultStreamHandler {

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public NanoHTTPD.Response.IStatus getStatus() {
        throw new IllegalStateException("this method should not be called in a status handler");
    }

    @Override
    public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
        String codeParam = urlParams.get("code");
        int code = Integer.parseInt(codeParam);

        return newFixedLengthResponse(newStatus(code, ""), getMimeType(), "{code:" + codeParam + "}");
    }

    @Override
    public InputStream getData() {
        throw new IllegalStateException("this method should not be called in a status handler");
    }

    private static NanoHTTPD.Response.IStatus newStatus(final int status, final String description) {
        return new NanoHTTPD.Response.IStatus() {
            @Override
            public String getDescription() {
                return "" + status + " " + description;
            }

            @Override
            public int getRequestStatus() {
                return status;
            }
        };
    }
}
