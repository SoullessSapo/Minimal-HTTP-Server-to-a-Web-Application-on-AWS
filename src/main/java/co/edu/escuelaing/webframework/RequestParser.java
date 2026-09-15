package co.edu.escuelaing.webframework;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the text a client sends into a {@link Request}.
 *
 * <p>Parsing lives here, and not inside {@link Request}, so the request seen by the application is
 * a plain value with no knowledge of sockets or of the wire format.</p>
 */
public final class RequestParser {

    private static final int MAX_REQUEST_LINE_LENGTH = 8 * 1024;
    private static final int MAX_HEADERS = 64;

    private RequestParser() {
    }

    /**
     * Reads one request: the request line, the header block and the empty line that ends it.
     *
     * @param reader reader positioned at the first byte of the request line
     * @throws MalformedRequestException when the request line is absent or does not have the
     *                                   {@code METHOD TARGET PROTOCOL} shape
     */
    public static Request parse(BufferedReader reader) throws IOException, MalformedRequestException {
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            throw new MalformedRequestException("Empty request line");
        }
        if (requestLine.length() > MAX_REQUEST_LINE_LENGTH) {
            throw new MalformedRequestException("Request line is too long");
        }

        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new MalformedRequestException("Unexpected request line: " + requestLine);
        }

        String method = parts[0].toUpperCase(Locale.ROOT);
        String target = parts[1];
        String protocol = parts[2];
        if (!protocol.startsWith("HTTP/")) {
            throw new MalformedRequestException("Unsupported protocol: " + protocol);
        }
        if (!target.startsWith("/")) {
            throw new MalformedRequestException("Unsupported request target: " + target);
        }

        int questionMark = target.indexOf('?');
        String rawPath = questionMark < 0 ? target : target.substring(0, questionMark);
        String queryString = questionMark < 0 ? "" : target.substring(questionMark + 1);

        return new Request(method, decode(rawPath), queryString,
                parseQuery(queryString), readHeaders(reader));
    }

    private static Map<String, String> readHeaders(BufferedReader reader)
            throws IOException, MalformedRequestException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (headers.size() >= MAX_HEADERS) {
                throw new MalformedRequestException("Too many headers");
            }
            int separator = line.indexOf(':');
            if (separator > 0) {
                headers.put(line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                        line.substring(separator + 1).trim());
            }
        }
        return headers;
    }

    /** Splits a query string into decoded values, keeping every repetition of a parameter. */
    static Map<String, List<String>> parseQuery(String queryString) throws MalformedRequestException {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return values;
        }
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            values.computeIfAbsent(decode(name), key -> new ArrayList<>()).add(decode(value));
        }
        return values;
    }

    /** Percent-decodes a value, translating {@code +} into a space as browsers do in queries. */
    private static String decode(String value) throws MalformedRequestException {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new MalformedRequestException("Invalid percent-encoding: " + value, e);
        }
    }
}
