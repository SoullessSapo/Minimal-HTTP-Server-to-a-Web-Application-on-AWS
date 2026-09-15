package co.edu.escuelaing.app;

import co.edu.escuelaing.webframework.Response;

import static co.edu.escuelaing.webframework.WebFramework.get;
import static co.edu.escuelaing.webframework.WebFramework.staticfiles;
import static co.edu.escuelaing.webframework.WebFramework.start;
import static co.edu.escuelaing.webframework.WebFramework.stop;

/**
 * The example application.
 *
 * <p>Everything this class does is declare what the application offers: where its static files
 * live and which services exist. It never opens a socket, parses a request or writes a response,
 * and adding a service below does not change a single line of the framework.</p>
 */
public class Application {

    /** Environment that decides whether the development-only routes are published. */
    private static final String APP_ENV = environment("APP_ENV", "development");

    /** Non-sensitive value that shows how a deployment can change behaviour without a rebuild. */
    private static final String GREETING_PREFIX = environment("GREETING_PREFIX", "Hello");

    public static void main(String[] args) throws Exception {

        staticfiles("/webroot");

        // --- Services ------------------------------------------------------------------------

        get("/hello", (req, resp) -> {
            String name = req.getValue("name");
            if (name == null || name.isBlank()) {
                name = "world";
            }
            return GREETING_PREFIX + " " + name;
        });

        get("/pi", (req, resp) -> String.valueOf(Math.PI));

        get("/square", (req, resp) -> {
            String value = req.getValue("value");
            if (value == null || value.isBlank()) {
                resp.status(400);
                return "The query parameter 'value' is required, for example /square?value=7";
            }
            try {
                double number = Double.parseDouble(value);
                return value + " squared is " + format(number * number);
            } catch (NumberFormatException e) {
                resp.status(400);
                return "'" + value + "' is not a number.";
            }
        });

        get("/greeting", (req, resp) -> {
            resp.type(Response.APPLICATION_JSON);
            String name = req.getValueOrDefault("name", "world");
            String language = req.getValueOrDefault("language", "en");
            return "{\"greeting\":\"" + escape(GREETING_PREFIX + " " + name)
                    + "\",\"language\":\"" + escape(language) + "\"}";
        });

        // Evidence that the deployment is configured from the environment. It reports which
        // variables are in use and their non-sensitive values; no credential is ever exposed.
        get("/config", (req, resp) -> {
            resp.type(Response.APPLICATION_JSON);
            return "{\"appEnv\":\"" + escape(APP_ENV)
                    + "\",\"greetingPrefix\":\"" + escape(GREETING_PREFIX)
                    + "\",\"port\":" + co.edu.escuelaing.webframework.WebFramework.port()
                    + ",\"shutdownRouteEnabled\":" + isDevelopment() + "}";
        });

        // --- Development-only lifecycle route ---------------------------------------------------

        if (isDevelopment()) {
            get("/shutdown", (req, resp) -> {
                stop();
                return "Server will stop after this response.";
            });
            System.out.println("APP_ENV=" + APP_ENV + ": the /shutdown route is enabled.");
        } else {
            System.out.println("APP_ENV=" + APP_ENV + ": the /shutdown route is NOT published.");
        }

        start();
    }

    /** @return true when the application runs in development, the only environment that may stop. */
    private static boolean isDevelopment() {
        return "development".equalsIgnoreCase(APP_ENV);
    }

    /** Prints whole results without an artificial decimal part: 49 rather than 49.0. */
    private static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** Escapes a value before it is written inside a JSON string. */
    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '<' -> escaped.append("\\u003c");
                case '>' -> escaped.append("\\u003e");
                case '&' -> escaped.append("\\u0026");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
