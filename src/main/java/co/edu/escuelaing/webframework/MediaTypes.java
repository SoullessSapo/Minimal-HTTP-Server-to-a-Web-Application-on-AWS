package co.edu.escuelaing.webframework;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Association between a file extension and the content type of the response.
 *
 * <p>The content type is the only thing that tells the browser how to interpret the bytes it
 * receives: the same sequence is drawn as an image, executed as a script or rendered as a
 * document depending on this single header.</p>
 */
public final class MediaTypes {

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("html", "text/html; charset=UTF-8"),
            Map.entry("htm", "text/html; charset=UTF-8"),
            Map.entry("css", "text/css; charset=UTF-8"),
            Map.entry("js", "text/javascript; charset=UTF-8"),
            Map.entry("mjs", "text/javascript; charset=UTF-8"),
            Map.entry("json", "application/json; charset=UTF-8"),
            Map.entry("txt", "text/plain; charset=UTF-8"),
            Map.entry("csv", "text/csv; charset=UTF-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"));

    private MediaTypes() {
    }

    /** @return the content type of a resource name, empty when the extension is not supported. */
    public static Optional<String> forFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_EXTENSION.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT)));
    }
}
