package io.ably.pubsub.types;

/**
 * A class encapsulating a key/value pair
 */
public class Param {

    public Param(String key, String value) { this.key = key; this.value = value; }
    public Param(String key, Object value) { this(key, value.toString()); }
    public String key;
    public String value;

    public static Param[] array(final Param val) {
        return new Param[] { val };
    }

    public static Param[] push(Param[] params, Param val) {
        if (params == null) {
            return new Param[] { val };
        }

        int len = params.length;
        Param[] result = new Param[len + 1];
        System.arraycopy(params, 0, result, 0, len);
        result[len] = val;
        return result;
    }

    public static Param[] push(Param[] params, String key, String value) {
        return push(params, new Param(key, value));
    }

    public static Param[] set(Param[] params, Param val) {
        if (params == null) {
            return new Param[] { val };
        }

        for (int i = 0; i < params.length; i++) {
            if (params[i].key.equals(val.key)) {
                params[i] = val;
                return params;
            }
        }

        return push(params, val);
    }

    public static Param[] set(Param[] params, String key, String value) {
        return set(params, new Param(key, value));
    }

    public static boolean containsKey(Param[] params, String key) {
        return getFirst(params, key) != null;
    }

    public static String getFirst(Param[] params, String key) {
        if(params == null)
            return null;
        for(Param param : params)
            if(param.key.equals(key))
                return param.value;
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Param param = (Param) o;

        if (key != null ? !key.equals(param.key) : param.key != null) return false;
        return value != null ? value.equals(param.value) : param.value == null;

    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return key + ":" + value;
    }
}
