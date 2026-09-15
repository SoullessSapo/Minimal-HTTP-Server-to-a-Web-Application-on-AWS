package co.edu.escuelaing.webframework;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Translation of a request path into a safe relative resource name.
 *
 * <p>The path arrives already percent-decoded, which is exactly why the check belongs here:
 * {@code /%2e%2e/etc/passwd} and {@code /../etc/passwd} are the same request and both must be
 * refused.</p>
 */
public final class ResourcePaths {

    /** File returned when a folder, or the site root, is requested. */
    public static final String INDEX_FILE = "index.html";

    private ResourcePaths() {
    }

    /**
     * @param path decoded path taken from the request
     * @return the relative resource name inside the static folder, or empty when the path tries to
     *         leave it
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
                // Refuse instead of climbing: nothing above the static folder is publishable.
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
