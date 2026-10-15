/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package io.ably.pubsub.test.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import fi.iki.elonen.NanoHTTPD;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Auth.TokenDetails;
import io.ably.pubsub.http.Auth.TokenParams;
import io.ably.pubsub.http.Auth.TokenRequest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.ErrorResponse;
import io.ably.pubsub.util.Serialisation;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

public class TokenServer extends NanoHTTPD {

    public TokenServer(PubSubHttpClient ably, int port) {
        super(port);
        this.ably = ably;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String target = session.getUri();
        Map<String, String> headers = session.getHeaders();

        if (method.equals(Method.POST)) {
            try {
                session.parseBody(new HashMap<String, String>());
            } catch (IOException | ResponseException e) {
                return error2Response(new ErrorInfo("Bad POST token request", 400, 40000));
            }
        }

        Map<String, String> params = session.getParms();

        if ((method.equals(Method.POST) && target.equals("/post-token-request")) ||
                (method.equals(Method.GET) && target.equals("/get-token-request"))) {
            TokenParams tokenParams = params2TokenParams(params);
            try {
                TokenRequest tokenRequest = ably.auth.createTokenRequest(tokenParams, null);
                return json2Response(tokenRequest.asJsonElement());
            } catch (AblyException e) {
                e.printStackTrace();
                return error2Response(e.errorInfo);
            }
        }

        if (!method.equals(Method.GET)) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method not supported");
        }

        if(target.equals("/get-token")) {
            TokenParams tokenParams = params2TokenParams(params);
            try {
                TokenDetails token = ably.auth.requestToken(tokenParams, null);
                return json2Response(token);
            } catch (AblyException e) {
                e.printStackTrace();
                return error2Response(e.errorInfo);
            }
        }
        else if(target.equals("/404")) {
            return error2Response(new ErrorInfo("Not found", 404, 0));
        }
        else if(target.equals("/wait")) {
            long delay = 30000;
            try {
                String strDelayMillis = params.get("delay");
                if(strDelayMillis != null) {
                    delay = Long.valueOf(strDelayMillis);
                }
            } catch(NumberFormatException nfe) {}
            try {
                Thread.sleep(delay);
            } catch(InterruptedException ie) {}
            return newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_JSON, "");
        }
        else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Unexpected path: " + target);
        }
    }

    private static Response params2ErrorResponse(Map<String, String> params, Response.Status status) {
        StringBuilder builder = new StringBuilder();
        for(Entry<String, String> entry : params.entrySet()) {
            if(builder.length() != 0) builder.append('&');
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return error2Response(new ErrorInfo(builder.toString(), status.getRequestStatus(), 0));
    }

    private static TokenParams params2TokenParams(Map<String, String> params) {
        TokenParams tokenParams = new TokenParams();
        if(params.containsKey("client_id"))
            tokenParams.clientId = params.get("client_id");
        if(params.containsKey("clientId"))
            tokenParams.clientId = params.get("clientId");
        if(params.containsKey("timestamp"))
            tokenParams.timestamp = Long.valueOf(params.get("timestamp"));
        if(params.containsKey("ttl"))
            tokenParams.ttl = Long.valueOf(params.get("ttl"));
        if(params.containsKey("capability"))
            tokenParams.capability = params.get("capability");
        return tokenParams;
    }

    private static Response json2Response(Object obj) {
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, Serialisation.gson.toJson(obj));
    }

    private static Response.Status getStatus(int statusCode) {
        switch(statusCode) {
        case 200:
            return Response.Status.OK;
        case 400:
            return Response.Status.BAD_REQUEST;
        case 401:
            return Response.Status.UNAUTHORIZED;
        case 404:
            return Response.Status.NOT_FOUND;
        case 500:
        default:
            return Response.Status.INTERNAL_ERROR;
        }
    }

    private static Response error2Response(ErrorInfo errorInfo) {
        ErrorResponse err = new ErrorResponse();
        err.error = errorInfo;
        return newFixedLengthResponse(getStatus(errorInfo.statusCode), MIME_JSON, Serialisation.gson.toJson(err));
    }

    private final PubSubHttpClient ably;
    private static final String MIME_JSON = "application/json";
}
