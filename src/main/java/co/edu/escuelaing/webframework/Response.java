package co.edu.escuelaing.webframework;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The response an application is preparing.
 *
 * <p>A handler that only returns text never has to touch this object; the framework fills in a
 * sensible status and content type. A handler that needs to answer JSON, or to report a client
 * error, changes it here.</p>
 *
 * <pre>{@code
 * get("/api/pi", (req, resp) -> {
 *     resp.type("application/json; charset=UTF-8");
 *     return "{\"pi\":" + Math.PI + "}";
 * });
 * }</pre>
 */
public class Response {

    public static final String TEXT_PLAIN = "text/plain; charset=UTF-8";
    public static final String TEXT_HTML = "text/html; charset=UTF-8";
    public static final String APPLICATION_JSON = "application/json; charset=UTF-8";

    private int status = 200;
    private String contentType = TEXT_PLAIN;
    private final Map<String, String> headers = new LinkedHashMap<>();

    /** Sets the HTTP status code of the response. */
    public Response status(int status) {
        this.status = status;
        return this;
    }

    /** Sets the content type, which is how the browser learns to interpret the body. */
    public Response type(String contentType) {
        this.contentType = contentType;
        return this;
    }

    /** Adds a response header. */
    public Response header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}
