package edu.escuelaing.tdse.httpserver;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entry point of the application.
 *
 * <p>Builds the configuration, assembles the mini web application on top of the public resources
 * area and hands both to the sequential server.</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        ServerConfig config;
        try {
            config = ServerConfig.from(args, System.getenv());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println(ServerConfig.usage());
            System.exit(2);
            return;
        }

        Path staticDirectory = config.getStaticDirectory().orElse(null);
        if (staticDirectory != null) {
            System.out.println("Public resources are read from " + staticDirectory.toAbsolutePath());
        }

        HttpServer server = new HttpServer(config.getPort(),
                new WebApplication(new StaticResourceHandler(staticDirectory)));

        // systemctl stop sends SIGTERM: the listening socket must be released cleanly.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown"));

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("The server could not start on port " + config.getPort()
                    + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
