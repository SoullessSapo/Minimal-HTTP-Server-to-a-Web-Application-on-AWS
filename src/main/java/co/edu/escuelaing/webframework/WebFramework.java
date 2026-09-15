package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The API an application developer uses.
 *
 * <p>Everything an application needs is here: where the static files live, which services exist,
 * and when to start and stop. Sockets, parsing and response writing stay behind this facade.</p>
 *
 * <pre>{@code
 * import static co.edu.escuelaing.webframework.WebFramework.*;
 *
 * public class Application {
 *     public static void main(String[] args) throws Exception {
 *         staticfiles("/webroot");
 *         get("/hello", (req, resp) -> "Hello " + req.getValueOrDefault("name", "world"));
 *         get("/pi", (req, resp) -> String.valueOf(Math.PI));
 *         start();
 *     }
 * }
 * }</pre>
 */
public final class WebFramework {

    /** Port used when the environment does not configure one. */
    public static final int DEFAULT_PORT = 8080;

    /** Environment variable that the cloud platform uses to impose a port. */
    public static final String PORT_VARIABLE = "PORT";

    /** Environment variable with an optional external folder of static resources. */
    public static final String STATIC_FILES_PATH_VARIABLE = "STATIC_FILES_PATH";

    private static final Router ROUTER = new Router();
    private static final StaticFileService STATIC_FILES = new StaticFileService();
    private static final HttpServer SERVER = new HttpServer(ROUTER, STATIC_FILES);

    private WebFramework() {
    }

    /**
     * Configures the folder that holds the static resources.
     *
     * @param folder folder of the classpath, for example {@code /webroot}, which corresponds to
     *               {@code src/main/resources/webroot}
     */
    public static void staticfiles(String folder) {
        STATIC_FILES.setFolder(folder);
    }

    /**
     * Registers a GET service.
     *
     * @param path    path that triggers the service, for example {@code /hello}
     * @param handler lambda that produces the answer
     */
    public static void get(String path, RouteHandler handler) {
        ROUTER.get(path, handler);
    }

    /**
     * Starts the server on the port given by the {@code PORT} environment variable, or on
     * {@value #DEFAULT_PORT} when that variable is not set.
     *
     * <p>This is the form a cloud platform expects: the host decides the port, the application
     * does not hardcode it.</p>
     */
    public static void start() throws IOException {
        start(resolvePort(System.getenv(PORT_VARIABLE)));
    }

    /** Starts the server on an explicit port. */
    public static void start(int port) throws IOException {
        String externalFolder = System.getenv(STATIC_FILES_PATH_VARIABLE);
        if (externalFolder != null && !externalFolder.isBlank()) {
            STATIC_FILES.setExternalRoot(Path.of(externalFolder.trim()));
            System.out.println("Static resources are also read from " + externalFolder.trim());
        }

        System.out.println("Registered routes: " + ROUTER.getRoutes());
        Runtime.getRuntime().addShutdownHook(new Thread(SERVER::stop, "shutdown-hook"));
        SERVER.start(port);
    }

    /**
     * Asks the server to stop once the current request has been answered.
     *
     * <p>Meant to be called from inside a handler, which is what makes a {@code /shutdown} route
     * possible without breaking the response being written.</p>
     */
    public static void stop() {
        SERVER.stop();
    }

    /**
     * Reads the port from a raw environment value.
     *
     * @return the configured port, or {@value #DEFAULT_PORT} when the value is absent or not a
     *         number, because an unreadable port must not prevent the application from starting
     */
    static int resolvePort(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(rawValue.trim());
            if (port < 0 || port > 65_535) {
                System.err.println(PORT_VARIABLE + "=" + rawValue + " is out of range; using "
                        + DEFAULT_PORT);
                return DEFAULT_PORT;
            }
            return port;
        } catch (NumberFormatException e) {
            System.err.println(PORT_VARIABLE + "=" + rawValue + " is not a number; using " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    /** @return the port the server is listening on, or -1 when it has not been started. */
    public static int port() {
        return SERVER.getPort();
    }

    public static boolean isRunning() {
        return SERVER.isRunning();
    }

    /** @return the routing table, so an application can list what it registered. */
    public static Router router() {
        return ROUTER;
    }

    /** Forgets every registered route. Used by the tests to isolate one case from the next. */
    static void reset() {
        ROUTER.clear();
        STATIC_FILES.setFolder(StaticFileService.DEFAULT_FOLDER);
        STATIC_FILES.setExternalRoot(null);
    }
}
