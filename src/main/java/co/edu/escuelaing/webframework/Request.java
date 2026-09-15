package co.edu.escuelaing.webframework;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An incoming HTTP request, as the application sees it.
 *
 * <p>The application never touches sockets or raw text: it asks this object for the path and for
 * the values of the query string. Asking for a parameter that was not sent returns {@code null}
 * instead of failing, so a missing parameter can never bring the server down.</p>
 */
public class Request {

    private final String method;
    private final String path;
    private final String queryString;
    private final Map<String, List<String>> queryValues;
    private final Map<String, String> headers;

    Request(String method, String path, String queryString,
            Map<String, List<String>> queryValues, Map<String, String> headers) {
        this.method = method;
        this.path = path;
        this.queryString = queryString;
        this.queryValues = Collections.unmodifiableMap(new LinkedHashMap<>(queryValues));
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * Returns the first value of a query-string parameter, already percent-decoded.
     *
     * <p>For {@code GET /hello?name=Pedro} the call {@code getValue("name")} returns
     * {@code "Pedro"}.</p>
     *
     * @param name name of the parameter
     * @return the decoded value, or {@code null} when the parameter was not sent
     */
    public String getValue(String name) {
        List<String> values = queryValues.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * Returns every value sent for a parameter, for requests such as
     * {@code /tags?tag=java&tag=http}.
     *
     * @param name name of the parameter
     * @return the decoded values, empty when the parameter was not sent
     */
    public List<String> getValues(String name) {
        return queryValues.getOrDefault(name, List.of());
    }

    /** @return the default value when the parameter is absent or blank. */
    public String getValueOrDefault(String name, String fallback) {
        String value = getValue(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    public String getMethod() {
        return method;
    }

    /** @return the path of the request, without the query string. */
    public String getPath() {
        return path;
    }

    /** @return the raw query string, empty when the request had none. */
    public String getQueryString() {
        return queryString;
    }

    /** @return every query parameter with all of its values. */
    public Map<String, List<String>> getQueryValues() {
        return queryValues;
    }

    /** @return a request header; names are matched without case sensitivity. */
    public String getHeader(String name) {
        return headers.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String toString() {
        return method + " " + path + (queryString.isEmpty() ? "" : "?" + queryString);
    }
}
