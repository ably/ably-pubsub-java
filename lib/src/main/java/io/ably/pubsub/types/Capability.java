package io.ably.pubsub.types;


import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * A class representing an Ably Capability, providing
 * convenience methods to simplify creation of token requests
 */
public class Capability {

    /**
     * Convenience method to canonicalise a JSON capability expression.
     *
     * @param capability a capability string, which is the JSON text for the capability
     * @return a capability string which has been canonicalised
     * @throws AblyException if there is an error processing the given string
     * (if for example it is not valid JSON)
     */
    public static String c14n(String capability) throws AblyException {
        if (capability == null || capability.isEmpty()) return null;
        try {
            JsonObject json = (JsonObject)gsonParser.parse(capability);
            return (new Capability(json)).toString();
        } catch(ClassCastException | JsonParseException e) {
            throw AblyException.fromThrowable(e);
        }
    }

    /**
     * Construct a new empty Capability
     */
    public Capability() {
        json = new JsonObject();
    }

    /**
     * Private constructor; create a new Capability instance given a JSONObject
     *
     * @param json the JSONObject
     */
    private Capability(JsonObject json) {
        this.json = json;
        dirty = true;
    }

    /**
     * Add a resource to an existing Capability instance with the
     * given set of operations. If the resource already exists,
     * it is wholly replaced by the given set of operations.
     *
     * @param resource the resource string
     * @param ops a String varargs of the operations permitted for this resource;
     * the arguments do not need to be sorted
     */
    public void addResource(String resource, String... ops) {
        JsonArray jsonOps = (JsonArray)gson.toJsonTree(ops);
        json.add(resource, jsonOps);
        dirty = true;
    }

    /**
     * Remove a resource from an existing Capability instance
     *
     * @param resource the (possibly existing) resource
     */
    public void removeResource(String resource) {
        json.remove(resource);
        /* removal doesn't break the sort order, so dirty can remain true */
    }

    /**
     * Add an operation to an existing Capability instance for a
     * given resource. If the resource does not already exist,
     * it is created.
     *
     * @param resource the resource string
     * @param op a single operation String to be added for this resource;
     */
    public void addOperation(String resource, String op) {
        JsonArray jsonOps = (JsonArray)json.get(resource);
        if(jsonOps == null) {
            jsonOps = new JsonArray();
            json.add(resource, jsonOps);
        }
        int opCount = jsonOps.size();
        for(int i = 0; i < opCount; i++)
            if(jsonOps.get(i).getAsString().equals(op))
                return;

        jsonOps.add(op);
        dirty = true;
    }

    /**
     * Remove an operation for a given resource.
     * If the resource becomes empty as a result,
     * it is removed.
     *
     * @param resource the resource string
     * @param op a operation String to be removed for this resource;
     */
    public void removeOperation(String resource, String op) {
        JsonArray jsonOps = (JsonArray)json.get(resource);
        if(jsonOps == null)
            return;

        int opCount = jsonOps.size();
        for(int i = 0; i < opCount; i++)
            if(jsonOps.get(i).getAsString().equals(op)) {
                if(opCount == 1) {
                    json.remove(resource);
                } else {
                    jsonOps.remove(i);
                }
                return;
            }
        /* removal doesn't break sort order, so dirty can remain false */
    }

    /**
     * Get the canonicalised String text for a Capability instance.
     * The json object and its members are sorted if the object has been modified
     * since the last time it was canonicalised.
     *
     * @return the canonicalised String text
     */
    public String toString() {
        if(dirty) {
            Set<Entry<String, JsonElement>> entries = json.entrySet();
            if(entries.isEmpty())
                return "";
            String[] resources = new String[entries.size()];
            int idx = 0;
            for(Entry<String, JsonElement> entry : entries)
                resources[idx++] = entry.getKey();
            Arrays.sort(resources);
            JsonObject c14nJson = new JsonObject();
            for(String resource : resources) {
                JsonArray jsonOps = json.get(resource).getAsJsonArray();
                int count = jsonOps.size();
                String[] ops = new String[count];
                for(int i = 0; i < count; i++)
                    ops[i] = jsonOps.get(i).getAsString();
                Arrays.sort(ops);
                c14nJson.add(resource, gson.toJsonTree(ops));
            }
            json = c14nJson;
            dirty = false;
        }
        return gson.toJson(json);
    }

    private JsonObject json;
    private boolean dirty;
    private static final Gson gson = new Gson();
    private static final JsonParser gsonParser = new JsonParser();
}
