package co.edu.escuelaing.webframework;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The lookup table that turns a method and a path into the lambda that answers it.
 *
 * <p>This is the component that makes the server loop stable: registering a new service adds an
 * entry to this map, and the code that accepts connections never changes.</p>
 */
public class Router {

    private final Map<String, Route> routes = new LinkedHashMap<>();

    /** Registers a service. Registering the same method and path again replaces the handler. */
    public Route register(String method, String path, RouteHandler handler) {
        Route route = new Route(method, path, handler);
        routes.put(route.key(), route);
        return route;
    }

    /** Registers a GET service. */
    public Route get(String path, RouteHandler handler) {
        return register("GET", path, handler);
    }

    /**
     * Finds the service registered for a request.
     *
     * @return the route, or empty when no service was registered for that method and path, in
     *         which case the request has to be answered from the static resources
     */
    public Optional<Route> find(String method, String path) {
        if (method == null || path == null) {
            return Optional.empty();
        }
        String key = method.toUpperCase(Locale.ROOT) + " " + Route.normalize(path);
        return Optional.ofNullable(routes.get(key));
    }

    /** @return every registered route, in registration order. */
    public Collection<Route> getRoutes() {
        return routes.values();
    }

    public int size() {
        return routes.size();
    }

    /** Forgets every registered service. */
    public void clear() {
        routes.clear();
    }
}
