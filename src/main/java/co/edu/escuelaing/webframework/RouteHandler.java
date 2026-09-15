package co.edu.escuelaing.webframework;

/**
 * The service an application developer writes.
 *
 * <p>This single interface is what lets a route be a lambda: the framework knows how to accept a
 * connection, read a request and write a response, and it asks this function only for the content
 * of the answer.</p>
 *
 * <pre>{@code
 * get("/hello", (req, resp) -> "Hello " + req.getValue("name"));
 * }</pre>
 */
@FunctionalInterface
public interface RouteHandler {

    /**
     * Produces the body of the response.
     *
     * @param request  the incoming request, including its query-string values
     * @param response the response being prepared; a handler may change its status, content type
     *                 or headers before returning
     * @return the text to send as the body of the response
     */
    String handle(Request request, Response response);
}
