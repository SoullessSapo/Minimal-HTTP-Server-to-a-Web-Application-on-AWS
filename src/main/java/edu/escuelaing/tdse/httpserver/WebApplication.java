package edu.escuelaing.tdse.httpserver;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The mini web application: a deliberately hardcoded set of service paths in front of the public
 * resources area.
 *
 * <p>There is no router, no annotation scanning and no reflection. Each special URL is one explicit
 * condition, because the point of the laboratory is to expose the mechanism by which a path selects
 * behaviour. Anything that is not one of those paths is a static resource.</p>
 */
public class WebApplication implements RequestHandler {

    /** Accepts integers and decimals, in plain or scientific notation, and nothing else. */
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

    private static final int MAX_NAME_LENGTH = 60;

    /** Upper bound of the slow service, used to observe the sequential limitation. */
    private static final int MAX_DELAY_SECONDS = 20;

    private final StaticResourceHandler staticResources;
    private final long startedAtMillis = System.currentTimeMillis();

    public WebApplication(StaticResourceHandler staticResources) {
        this.staticResources = staticResources;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return Responses.methodNotAllowed(request.getMethod());
        }

        // --- The four hardcoded services, plus the slow one used in section 6.2 ---
        String path = request.getPath();
        if ("/app/hello".equals(path)) {
            return greeting(request);
        }
        if ("/app/square".equals(path)) {
            return square(request);
        }
        if ("/app/time".equals(path)) {
            return serverTime();
        }
        if ("/app/slow".equals(path)) {
            return slow(request);
        }
        if ("/health".equals(path)) {
            return health();
        }

        // --- Everything else is a file of the public resources area ---
        return staticResources.handle(request);
    }

    /** Greeting service: a name in the query string becomes a JSON greeting. */
    private HttpResponse greeting(HttpRequest request) {
        Optional<String> name = request.getQueryParameter("name").map(String::trim);
        if (name.isEmpty() || name.get().isEmpty()) {
            return HttpResponse.json(400, Json.error("The query parameter 'name' is required."));
        }
        if (name.get().length() > MAX_NAME_LENGTH) {
            return HttpResponse.json(400,
                    Json.error("The name must not be longer than " + MAX_NAME_LENGTH + " characters."));
        }

        String value = name.get();
        return HttpResponse.json(200, Json.object()
                .put("service", "greeting")
                .put("name", value)
                .put("greeting", "Hello, " + value + "!")
                .build());
    }

    /** Square service: the numeric continuation of the socket exercise of part 1. */
    private HttpResponse square(HttpRequest request) {
        Optional<String> raw = request.getQueryParameter("value").map(String::trim);
        if (raw.isEmpty() || raw.get().isEmpty()) {
            return HttpResponse.json(400, Json.error("The query parameter 'value' is required."));
        }
        if (!NUMBER.matcher(raw.get()).matches()) {
            return HttpResponse.json(400,
                    Json.error("'" + raw.get() + "' is not a number. Use for example 7 or -2.5."));
        }

        double value = Double.parseDouble(raw.get());
        double square = value * value;
        if (!Double.isFinite(square)) {
            return HttpResponse.json(400, Json.error("The result is outside the representable range."));
        }

        return HttpResponse.json(200, Json.object()
                .put("service", "square")
                .put("input", value)
                .put("square", square)
                .build());
    }

    /** Server-time service: proves the value is produced by the host, not by the browser clock. */
    private HttpResponse serverTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return HttpResponse.json(200, Json.object()
                .put("service", "time")
                .put("serverTime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .put("zone", now.getZone().getId())
                .put("epochMillis", now.toInstant().toEpochMilli())
                .build());
    }

    /**
     * Deliberately slow service used in section 6.2 to observe the sequential limitation. It does
     * not add concurrency: it simply keeps the single request loop busy.
     */
    private HttpResponse slow(HttpRequest request) {
        String raw = request.getQueryParameter("seconds").map(String::trim).orElse("5");
        if (!NUMBER.matcher(raw).matches()) {
            return HttpResponse.json(400, Json.error("'" + raw + "' is not a number of seconds."));
        }

        double requested = Double.parseDouble(raw);
        double seconds = Math.max(0, Math.min(requested, MAX_DELAY_SECONDS));
        long startedAt = System.currentTimeMillis();
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return HttpResponse.json(200, Json.object()
                .put("service", "slow")
                .put("requestedSeconds", seconds)
                .put("actualMillis", System.currentTimeMillis() - startedAt)
                .put("note", "While this request was running the server could not answer any other request.")
                .build());
    }

    /** Health service: the smallest possible proof that the process can still serve requests. */
    private HttpResponse health() {
        Duration uptime = Duration.ofMillis(System.currentTimeMillis() - startedAtMillis);
        return HttpResponse.json(200, Json.object()
                .put("status", "ok")
                .put("uptimeSeconds", uptime.toSeconds())
                .build());
    }
}
