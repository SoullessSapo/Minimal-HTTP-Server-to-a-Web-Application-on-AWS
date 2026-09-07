package edu.escuelaing.tdse.httpserver;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime configuration of the application.
 *
 * <p>The port has to be configurable because the port used during local development is not
 * necessarily the port opened in the EC2 security group, and the deployed process must be able to
 * take it from the environment of the service unit without rebuilding the artifact.</p>
 *
 * <p>Precedence: command line arguments, then environment variables, then the defaults.</p>
 */
public final class ServerConfig {

    public static final int DEFAULT_PORT = 35000;

    public static final String PORT_VARIABLE = "PORT";
    public static final String STATIC_DIRECTORY_VARIABLE = "STATIC_DIR";

    private final int port;
    private final Path staticDirectory;

    private ServerConfig(int port, Path staticDirectory) {
        this.port = port;
        this.staticDirectory = staticDirectory;
    }

    /**
     * Builds the configuration.
     *
     * @param args        {@code --port <number>} and {@code --static-dir <path>} are recognised
     * @param environment usually {@code System.getenv()}
     * @throws IllegalArgumentException when an option is unknown, incomplete or out of range
     */
    public static ServerConfig from(String[] args, Map<String, String> environment) {
        Integer port = readPort(environment.get(PORT_VARIABLE), PORT_VARIABLE);
        String staticDirectory = environment.get(STATIC_DIRECTORY_VARIABLE);

        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            switch (option) {
                case "--port", "-p" -> port = readPort(valueOf(args, ++i, option), option);
                case "--static-dir", "-s" -> staticDirectory = valueOf(args, ++i, option);
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }

        return new ServerConfig(port == null ? DEFAULT_PORT : port,
                staticDirectory == null || staticDirectory.isBlank() ? null : Path.of(staticDirectory));
    }

    private static String valueOf(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Option " + option + " needs a value");
        }
        return args[index];
    }

    private static Integer readPort(String rawValue, String source) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(source + " must be a number, but was '" + rawValue + "'");
        }
        // Port 0 is deliberately allowed: the tests use it to bind an ephemeral port.
        if (parsed < 0 || parsed > 65_535) {
            throw new IllegalArgumentException(source + " must be between 0 and 65535, but was " + parsed);
        }
        return parsed;
    }

    public int getPort() {
        return port;
    }

    /** @return a directory whose files take precedence over the resources packaged in the jar. */
    public Optional<Path> getStaticDirectory() {
        return Optional.ofNullable(staticDirectory);
    }

    public static String usage() {
        return """
                Usage: java -jar minimal-http-server.jar [options]

                  -p, --port <number>      port to listen on (default %d, environment variable %s)
                  -s, --static-dir <path>  directory whose files override the packaged public
                                           resources (environment variable %s)
                """.formatted(DEFAULT_PORT, PORT_VARIABLE, STATIC_DIRECTORY_VARIABLE);
    }
}
