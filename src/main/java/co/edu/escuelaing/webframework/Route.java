package co.edu.escuelaing.webframework;

import java.util.Locale;
import java.util.Objects;

/**
 * One registered service: an HTTP method, a path and the lambda that answers it.
 *
 * <p>Keeping the three together as one value is what allows the router to be a lookup table
 * instead of a chain of conditions inside the server loop.</p>
 */
public class Route {

    private final String method;
    private final String path;
    private final RouteHandler handler;

    public Route(String method, String path, RouteHandler handler) {
        this.method = Objects.requireNonNull(method, "method").toUpperCase(Locale.ROOT);
        this.path = normalize(Objects.requireNonNull(path, "path"));
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Gives every registered path the same shape, so {@code hello}, {@code /hello} and
     * {@code /hello/} all name the same service.
     */
    static String normalize(String path) {
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public RouteHandler getHandler() {
        return handler;
    }

    /** @return the key under which the router stores this route, for example {@code GET /hello}. */
    String key() {
        return method + " " + path;
    }

    @Override
    public String toString() {
        return key();
    }
}
