package edu.escuelaing.tdse.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One parsed HTTP request: the request line, the headers and the decoded query string.
 *
 * <p>The parser is deliberately small. It understands exactly what this laboratory needs: a
 * request line, a header block and an empty line. No request body is read because only GET is
 * accepted.</p>
 */
public final class HttpRequest {

    private static final int MAX_REQUEST_LINE_LENGTH = 8 * 1024;
    private static final int MAX_HEADERS = 64;

    private final String method;
    private final String target;
    private final String path;
    private final String protocol;
    private final Map<String, String> headers;
    private final Map<String, String> queryParameters;

    HttpRequest(String method, String target, String path, String protocol,
                Map<String, String> headers, Map<String, String> queryParameters) {
        this.method = method;
        this.target = target;
        this.path = path;
        this.protocol = protocol;
        this.headers = Collections.unmodifiableMap(headers);
        this.queryParameters = Collections.unmodifiableMap(queryParameters);
    }

    /**
     * Reads one request from an already connected client.
     *
     * @param reader reader positioned at the first byte of the request line
     * @return the parsed request
     * @throws MalformedRequestException if the request line is absent or does not have the
     *                                   {@code METHOD TARGET PROTOCOL} shape
     * @throws IOException               if the connection fails while reading
     */
    public static HttpRequest parse(BufferedReader reader) throws MalformedRequestException, IOException {
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
        String rawQuery = questionMark < 0 ? "" : target.substring(questionMark + 1);

        Map<String, String> headers = readHeaders(reader);
        Map<String, String> queryParameters = parseQuery(rawQuery);

        return new HttpRequest(method, target, decode(rawPath), protocol, headers, queryParameters);
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
                String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).trim();
                headers.put(name, value);
            }
        }
        return headers;
    }

    private static Map<String, String> parseQuery(String rawQuery) throws MalformedRequestException {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (rawQuery.isEmpty()) {
            return parameters;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            parameters.put(decode(name), decode(value));
        }
        return parameters;
    }

    /** Percent-decodes a value, translating {@code +} into a space as browsers do in queries. */
    private static String decode(String value) throws MalformedRequestException {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new MalformedRequestException("Invalid percent-encoding: " + value, e);
        }
    }

    public String getMethod() {
        return method;
    }

    /** @return the raw request target, still carrying the query string. */
    public String getTarget() {
        return target;
    }

    /** @return the decoded path portion of the request target. */
    public String getPath() {
        return path;
    }

    public String getProtocol() {
        return protocol;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQueryParameters() {
        return queryParameters;
    }

    /** @return the decoded value of a query parameter, if it was present. */
    public Optional<String> getQueryParameter(String name) {
        return Optional.ofNullable(queryParameters.get(name));
    }

    @Override
    public String toString() {
        return method + " " + target + " " + protocol;
    }
}
