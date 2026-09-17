package io.ably.pubsub.http;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.util.Base64Coder;

public class HttpAuth {

    public enum Type {
        BASIC,
        DIGEST,
        X_ABLY_TOKEN;

        static Type parse(final String value) {
            final String conformedValue = value.toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                return Type.valueOf(conformedValue);
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException("Failed to parse conformed form '" + conformedValue + "' of raw value '" + value + "'.", e);
            }
        }
    }

    HttpAuth(String username, String password, Type prefType) {
        this.username = username;
        this.password = password;
        this.prefType = prefType;
    }

    boolean hasChallenge() {
        return (type != null);
    }

    /**
     * Split a compound authenticate header string to get details for each auth type
     * @param authenticateHeaders
     * @return
     * @throws AblyException
     */
    public static Map<Type, String> sortAuthenticateHeaders(Collection<String> authenticateHeaders) throws AblyException {
        Map<Type, String> sortedHeaders = new HashMap<>();
        for(String header : authenticateHeaders) {
            int delimiterIdx = header.indexOf(' ');
            if(delimiterIdx == -1) { throw AblyException.fromErrorInfo(new ErrorInfo("Invalid authenticate header (no delimiter)", 40000, 400)); }
            String authType = header.substring(0,  delimiterIdx).trim();
            String authDetails = header.substring(delimiterIdx + 1).trim();
            sortedHeaders.put(Type.parse(authType), authDetails);
        }
        return sortedHeaders;
    }

    /**
     * Get authorization header based on the last-received server nonce.
     * This increments nc, and generates a new cnonce
     * @param method
     * @param uri
     * @param requestBody
     * @return
     * @throws AblyException
     */
    public String getAuthorizationHeader(String method, String uri, byte[] requestBody) throws AblyException {
        switch(type) {
        case BASIC:
            return "Basic " + Base64Coder.encodeString(username + ':' + password);
        case DIGEST:
            return getDigestHeader(method, uri, requestBody);
        default:
            return null;
        }
    }

    /**
     * Process a challenge; this selects the auth type to use and caches all
     * possible values based on the challenge in the case of digest auth
     * @param authenticateHeaders
     * @throws AblyException
     */
    public void processAuthenticateHeaders(Map<Type, String> authenticateHeaders) throws AblyException {
        String authDetails = authenticateHeaders.get(type = prefType);
        if(authDetails == null) {
            Entry<Type, String> firstEntry = authenticateHeaders.entrySet().iterator().next();
            if(firstEntry == null) { throw AblyException.fromErrorInfo(new ErrorInfo("Invalid authenticate header (no entries)", 40000, 400)); }
            type = firstEntry.getKey();
            authDetails = firstEntry.getValue();
        }
        if(type == Type.DIGEST) {
            processDigestHeader(authDetails);
        }
    }

    /**
     * For digest auth, process the challenge details
     * @param detailsString
     * @throws AblyException
     */
    private synchronized void processDigestHeader(String detailsString) throws AblyException {
        HashMap<String, String> authFields = splitAuthFields(detailsString);
        realm = authFields.get("realm");
        nonce = authFields.get("nonce");
        opaque = authFields.get("opaque");
        HA1 = digestString(username + ':' + realm + ':' + password);

        String qopStr = authFields.get("qop");
        if(qopStr != null) {
            qops = qopStr.split(",");
        }
    }

    /**
     * Get the Digest authorization header for a given request, based on already-processed challenge
     * @param method
     * @param uri
     * @param requestBody
     * @return
     * @throws AblyException
     */
    private String getDigestHeader(String method, String uri, byte[] requestBody) throws AblyException {
        String qop = null;
        if(qops != null) {
            for(String candidateQop : qops) {
                if(requestBody != null && candidateQop.trim().equals("auth-int")) {
                    qop = "auth-int";
                    break;
                }
                if(candidateQop.trim().equals("auth")) {
                    qop = "auth";
                    break;
                }
            }
        }

        String HA2, HA3, nc = null, cnonce = null;
        if(qop == null) {
            HA2 = digestString(method + ':' + uri);
            HA3 = digestString(HA1 + ':' + nonce + ':' + HA2);
        } else if(qop.equals("auth")) {
            nc = String.format("%08X", ncCounter++);
            cnonce = getClientNonce();
            HA2 = digestString(method + ':' + uri);
            HA3 = digestString(HA1 + ':' + nonce + ':' + nc + ':' + cnonce + ':' + qop + ':' + HA2);
        } else {
            nc = String.format("%08X", ncCounter++);
            cnonce = getClientNonce();
            HA2 = digestString(method + ':' + uri + ':' + digestBytes(requestBody));
            HA3 = digestString(HA1 + ':' + nonce + ':' + nc + ':' + cnonce + ':' + qop + ':' + HA2);
        }

        StringBuilder sb = new StringBuilder(128);
        sb.append("Digest ");
        sb.append("username"    ).append("=\"").append(username).append("\",");
        sb.append("realm"       ).append("=\"").append(realm   ).append("\",");
        sb.append("nonce"       ).append("=\"").append(nonce   ).append("\",");
        sb.append("uri"         ).append("=\"").append(uri     ).append("\",");
        sb.append("algorithm"   ).append("=\"").append("MD5"   ).append("\",");

        if(qop != null) {
            sb.append("qop"     ).append("=\"").append(qop     ).append("\",");
            sb.append("nc"      ).append("="  ).append(nc      ).append(",");
            sb.append("cnonce"  ).append("=\"").append(cnonce  ).append("\",");
        }

        if(opaque != null) {
            sb.append("response").append("=\"").append(HA3     ).append("\",");
            sb.append("opaque"  ).append("=\"").append(opaque  ).append("\"");
        } else {
            sb.append("response").append("=\"").append(HA3     ).append("\"");
        }
        return sb.toString();
    }

    private static HashMap<String, String> splitAuthFields(String detailsString) {
        HashMap<String, String> values = new HashMap<String, String>();
        String keyValueArray[] = detailsString.split(",");
        for (String keyval : keyValueArray) {
            if (keyval.contains("=")) {
                String key = keyval.substring(0, keyval.indexOf("="));
                String value = keyval.substring(keyval.indexOf("=") + 1);
                values.put(key.trim(), value.replaceAll("\"", "").trim());
            }
        }
        return values;
    }

    private static String digestBytes(byte[] buf) {
        md5.reset();
        md5.update(buf);
        byte[] ha1bytes = md5.digest();
        return bytesToHexString(ha1bytes);
    }

    private static String digestString(String text) {
        try{
            return digestBytes(text.getBytes("ISO-8859-1"));
        }
        catch(UnsupportedEncodingException e){ return null; }
    }

    private static final String HEX_LOOKUP = "0123456789abcdef";
    private static String bytesToHexString(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for(int i = 0; i < bytes.length; i++){
            sb.append(HEX_LOOKUP.charAt((bytes[i] & 0xF0) >> 4));
            sb.append(HEX_LOOKUP.charAt((bytes[i] & 0x0F) >> 0));
        }
        return sb.toString();
    }

    private static String getClientNonce() {
        String fmtDate = (new SimpleDateFormat("yyyy:MM:dd:hh:mm:ss")).format(new Date());
        Integer randomInt = (new Random(100000)).nextInt();
        return digestString(fmtDate + randomInt.toString()).substring(0,  8);
    }

    private static MessageDigest md5 = null;
    static {
        try{
            md5 = MessageDigest.getInstance("MD5");
        }
        catch(NoSuchAlgorithmException e) {}
    }

    private String realm;
    private String nonce;
    private String[] qops;
    private String opaque;
    private Type type;
    private int ncCounter = 1;

    private String HA1;

    private final String username;
    private final String password;
    private final Type prefType;
}
