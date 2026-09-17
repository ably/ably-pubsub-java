package io.ably.pubsub.test.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AblyCommonsReader {
    private static final String BASE_URL = "https://raw.githubusercontent.com/ably/ably-common/refs/heads/main/";
    private static Gson gson = new Gson();

    public static String readAsString(String path) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        StringBuilder sb = new StringBuilder();
        String output;
        while ((output = br.readLine()) != null) {
            sb.append(output);
        }

        conn.disconnect();

        return sb.toString();
    }

    public static JsonObject readAsJsonObject(String path) {
        try {
            return JsonParser.parseString(readAsString(path)).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T read(String path, Class<T> classOfT) {
        try {
            return gson.fromJson(readAsString(path), classOfT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
