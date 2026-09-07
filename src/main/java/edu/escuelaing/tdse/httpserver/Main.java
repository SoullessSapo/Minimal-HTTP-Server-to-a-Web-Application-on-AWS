package edu.escuelaing.tdse.httpserver;

import java.io.IOException;

/** Entry point of the application. */
public final class Main {

    private static final int DEFAULT_PORT = 35000;

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        RequestHandler handler = request -> HttpResponse.html(200,
                "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                        + "<title>Sequential HTTP server</title></head>"
                        + "<body><h1>Sequential HTTP server</h1>"
                        + "<p>Handled request: " + request + "</p></body></html>");

        HttpServer server = new HttpServer(DEFAULT_PORT, handler);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
