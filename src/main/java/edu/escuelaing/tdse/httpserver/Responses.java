package edu.escuelaing.tdse.httpserver;

/** Small factory of the error responses shared by the static area and the services. */
public final class Responses {

    private Responses() {
    }

    public static HttpResponse notFound(String path) {
        return HttpResponse.html(404, page("404 - Not found",
                "The resource <code>" + escapeHtml(path) + "</code> is not published by this server."));
    }

    public static HttpResponse forbidden(String reason) {
        return HttpResponse.html(403, page("403 - Forbidden", escapeHtml(reason)));
    }

    /** A 405 response must announce which methods are acceptable. */
    public static HttpResponse methodNotAllowed(String method) {
        return HttpResponse.html(405, page("405 - Method not allowed",
                        "This laboratory server only implements <code>GET</code>; <code>"
                                + escapeHtml(method) + "</code> is not supported."))
                .withHeader("Allow", "GET");
    }

    private static String page(String title, String message) {
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n"
                + "<title>" + escapeHtml(title) + "</title>\n"
                + "<style>body{font-family:system-ui,sans-serif;margin:3rem auto;max-width:40rem;"
                + "color:#0f172a}code{background:#e2e8f0;padding:.1rem .3rem;border-radius:.2rem}"
                + "a{color:#1d4ed8}</style>\n</head>\n<body>\n<h1>" + escapeHtml(title) + "</h1>\n"
                + "<p>" + message + "</p>\n<p><a href=\"/\">Back to the home page</a></p>\n"
                + "</body>\n</html>\n";
    }

    /** Escapes a value that came from the request before echoing it back inside HTML. */
    static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
