package edu.escuelaing.tdse.httpserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Translation of a request path into a safe relative resource name.
 *
 * <p>The request path arrives already percent-decoded, which is exactly why the check has to be
 * performed here and not before: {@code /%2e%2e/etc/passwd} and {@code /../etc/passwd} are the same
 * request and both must be refused.</p>
 */
public final class ResourcePaths {

    /** File returned when a directory, or the site root, is requested. */
    public static final String INDEX_FILE = "index.html";

    private ResourcePaths() {
    }

    /**
     * Normalises a decoded request path.
     *
     * @param path decoded path taken from the request target
     * @return the relative resource name inside the public area, or empty when the path tries to
     *         escape that area or contains characters that are not acceptable in a resource name
     */
    public static Optional<String> normalize(String path) {
        if (path == null || path.isEmpty() || !path.startsWith("/") || path.indexOf('\0') >= 0) {
            return Optional.empty();
        }

        String candidate = path.replace('\\', '/');
        List<String> segments = new ArrayList<>();
        for (String segment : candidate.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                // Refuse instead of climbing: nothing above the public area is publishable.
                return Optional.empty();
            }
            segments.add(segment);
        }

        if (segments.isEmpty() || candidate.endsWith("/")) {
            segments.add(INDEX_FILE);
        }
        return Optional.of(String.join("/", segments));
    }
}
