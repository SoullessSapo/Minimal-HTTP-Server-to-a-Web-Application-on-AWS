package edu.escuelaing.tdse.httpserver;

import java.io.IOException;

/** Entry point of the application. */
public final class Main {

    private static final int DEFAULT_PORT = 35000;

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = new HttpServer(DEFAULT_PORT, new WebApplication(new StaticResourceHandler()));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
