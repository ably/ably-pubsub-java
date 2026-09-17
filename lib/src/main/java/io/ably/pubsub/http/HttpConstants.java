package io.ably.pubsub.http;


public class HttpConstants {
    public static class ContentTypes {
        public static final String JSON                = "application/json";
        public static final String FORM_ENCODING       = "application/x-www-form-urlencoded";
    }

    public static class Headers {
        public static final String CONTENT_LENGTH      = "Content-Length";
        public static final String ACCEPT              = "Accept";
        public static final String CONTENT_TYPE        = "Content-Type";
        public static final String WWW_AUTHENTICATE    = "WWW-Authenticate";
        public static final String PROXY_AUTHENTICATE  = "Proxy-Authenticate";
        public static final String AUTHORIZATION       = "Authorization";
        public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
        public static final String LINK                = "Link";
    }

    public static class Methods {
        public static final String GET    = "GET";
        public static final String PUT    = "PUT";
        public static final String POST   = "POST";
        public static final String DELETE = "DELETE";
        public static final String PATCH  = "PATCH";
    }
}
