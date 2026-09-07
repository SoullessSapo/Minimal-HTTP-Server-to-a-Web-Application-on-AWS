package edu.escuelaing.tdse.httpserver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON writer.
 *
 * <p>The laboratory forbids a serialisation framework, but a value that came from the query string
 * must never be pasted into a document without escaping. This class is the single place where that
 * escaping happens.</p>
 */
public final class Json {

    private final Map<String, String> members = new LinkedHashMap<>();

    private Json() {
    }

    public static Json object() {
        return new Json();
    }

    /** Adds a text member, escaping the value. */
    public Json put(String name, String value) {
        members.put(name, quote(value));
        return this;
    }

    public Json put(String name, long value) {
        members.put(name, Long.toString(value));
        return this;
    }

    /** Adds a numeric member, printing whole values without a decimal part. */
    public Json put(String name, double value) {
        members.put(name, number(value));
        return this;
    }

    public Json put(String name, boolean value) {
        members.put(name, Boolean.toString(value));
        return this;
    }

    public String build() {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> member : members.entrySet()) {
            if (!first) {
                json.append(',');
            }
            json.append(quote(member.getKey())).append(':').append(member.getValue());
            first = false;
        }
        return json.append('}').toString();
    }

    /** @return a JSON document describing a rejected request. */
    public static String error(String message) {
        return object().put("error", message).build();
    }

    /** @return the value wrapped in quotes with every character that JSON forbids escaped. */
    public static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                case '\b' -> quoted.append("\\b");
                case '\f' -> quoted.append("\\f");
                case '<' -> quoted.append("\\u003c");
                case '>' -> quoted.append("\\u003e");
                case '&' -> quoted.append("\\u0026");
                default -> {
                    if (character < 0x20 || character == 0x7f) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    /** Formats a double without an artificial decimal part for whole numbers. */
    static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
