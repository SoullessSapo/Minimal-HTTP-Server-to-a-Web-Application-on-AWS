package edu.escuelaing.tdse.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Serves the files of the public-resources area: the HTML page, the JavaScript client, the style
 * sheet and the images.
 *
 * <p>Resources are read from the classpath ({@code src/main/resources/public}) so that a single jar
 * is a complete deployable artifact. An external directory can be configured to override them,
 * which is useful when the page has to be edited on the EC2 instance without rebuilding.</p>
 */
public class StaticResourceHandler implements RequestHandler {

    private static final String CLASSPATH_PREFIX = "public/";

    private final Path externalRoot;

    public StaticResourceHandler() {
        this(null);
    }

    /**
     * @param externalRoot optional directory searched before the classpath, may be {@code null}
     */
    public StaticResourceHandler(Path externalRoot) {
        this.externalRoot = externalRoot == null ? null : externalRoot.toAbsolutePath().normalize();
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return Responses.methodNotAllowed(request.getMethod());
        }

        Optional<String> resourceName = ResourcePaths.normalize(request.getPath());
        if (resourceName.isEmpty()) {
            return Responses.forbidden("The requested path is outside the public resources area.");
        }

        String name = resourceName.get();
        Optional<String> contentType = MediaTypes.forFileName(name);
        if (contentType.isEmpty()) {
            // An unknown extension is treated as a resource that does not exist: this server only
            // publishes the media types it can describe correctly.
            return Responses.notFound(request.getPath());
        }

        Optional<byte[]> content = read(name);
        if (content.isEmpty()) {
            return Responses.notFound(request.getPath());
        }
        return new HttpResponse(200, contentType.get(), content.get());
    }

    /** Reads a resource as bytes, so that text and binary files follow the same path. */
    private Optional<byte[]> read(String resourceName) {
        Optional<byte[]> fromDisk = readFromExternalRoot(resourceName);
        if (fromDisk.isPresent()) {
            return fromDisk;
        }
        return readFromClasspath(resourceName);
    }

    private Optional<byte[]> readFromExternalRoot(String resourceName) {
        if (externalRoot == null) {
            return Optional.empty();
        }
        Path candidate = externalRoot.resolve(resourceName).normalize();
        // Second barrier: even if the normalisation above ever changed, the resolved file must
        // still live inside the configured directory.
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
        ClassLoader loader = StaticResourceHandler.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(CLASSPATH_PREFIX + resourceName)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(stream.readAllBytes());
        } catch (IOException e) {
            System.err.println("Could not read classpath resource " + resourceName + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}
