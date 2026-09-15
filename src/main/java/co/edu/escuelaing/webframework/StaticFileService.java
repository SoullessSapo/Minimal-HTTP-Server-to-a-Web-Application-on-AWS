package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Serves the files of the static folder configured with {@code staticfiles(...)}.
 *
 * <p>Resources are read from the classpath, so that a single jar is a complete deployable
 * artifact. An external directory can be configured as well, which is useful to replace the page
 * on a server without rebuilding.</p>
 */
public class StaticFileService {

    /** Default folder, matching {@code src/main/resources/webroot}. */
    public static final String DEFAULT_FOLDER = "/webroot";

    private String classpathPrefix;
    private Path externalRoot;

    public StaticFileService() {
        this(DEFAULT_FOLDER);
    }

    public StaticFileService(String folder) {
        setFolder(folder);
    }

    /**
     * Configures the folder of the classpath that holds the static resources.
     *
     * @param folder for example {@code /webroot}, matching {@code src/main/resources/webroot}
     */
    public final void setFolder(String folder) {
        String normalized = folder == null || folder.isBlank() ? DEFAULT_FOLDER : folder.trim();
        normalized = normalized.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.classpathPrefix = normalized.isEmpty() ? "" : normalized + "/";
    }

    /**
     * Configures a directory of the file system whose files take precedence over the packaged
     * ones. Usually fed from the {@code STATIC_FILES_PATH} environment variable.
     */
    public void setExternalRoot(Path externalRoot) {
        this.externalRoot = externalRoot == null ? null : externalRoot.toAbsolutePath().normalize();
    }

    public String getClasspathPrefix() {
        return classpathPrefix;
    }

    public Optional<Path> getExternalRoot() {
        return Optional.ofNullable(externalRoot);
    }

    /**
     * Looks for the resource that answers a request path.
     *
     * @param requestPath decoded path of the request, for example {@code /images/logo.png}
     * @return the file, or empty when the path is unsafe, the extension is not supported or the
     *         file does not exist
     */
    public Optional<StaticFile> find(String requestPath) {
        Optional<String> name = ResourcePaths.normalize(requestPath);
        if (name.isEmpty()) {
            return Optional.empty();
        }

        String resourceName = name.get();
        Optional<String> contentType = MediaTypes.forFileName(resourceName);
        if (contentType.isEmpty()) {
            // Only media types the framework can describe correctly are published.
            return Optional.empty();
        }

        return read(resourceName)
                .map(content -> new StaticFile(resourceName, contentType.get(), content));
    }

    private Optional<byte[]> read(String resourceName) {
        Optional<byte[]> fromDisk = readFromExternalRoot(resourceName);
        return fromDisk.isPresent() ? fromDisk : readFromClasspath(resourceName);
    }

    private Optional<byte[]> readFromExternalRoot(String resourceName) {
        if (externalRoot == null) {
            return Optional.empty();
        }
        Path candidate = externalRoot.resolve(resourceName).normalize();
        // Second barrier: the resolved file must still live inside the configured directory.
        if (!candidate.startsWith(externalRoot) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(candidate));
        } catch (IOException e) {
            System.err.println("Could not read " + candidate + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<byte[]> readFromClasspath(String resourceName) {
        ClassLoader loader = StaticFileService.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(classpathPrefix + resourceName)) {
            return stream == null ? Optional.empty() : Optional.of(stream.readAllBytes());
        } catch (IOException e) {
            System.err.println("Could not read classpath resource " + resourceName + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}
