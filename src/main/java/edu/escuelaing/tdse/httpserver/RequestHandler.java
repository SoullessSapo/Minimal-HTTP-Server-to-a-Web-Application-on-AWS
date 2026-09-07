package edu.escuelaing.tdse.httpserver;

/**
 * Turns one parsed request into one response.
 *
 * <p>Keeping this contract explicit is what allows the transport (accept, read, write, close) to
 * stay separated from the decisions about paths and content, without introducing any routing
 * framework.</p>
 */
@FunctionalInterface
public interface RequestHandler {

    HttpResponse handle(HttpRequest request);
}
