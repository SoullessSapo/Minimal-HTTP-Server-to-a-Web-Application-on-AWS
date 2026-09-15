package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the service that answers when no route matches. */
class StaticFileServiceTest {

    private StaticFileService service;

    @BeforeEach
    void setUp() {
        service = new StaticFileService("/webroot");
    }

    @Test
    @DisplayName("The root of the site is the index page")
    void servesTheIndexPage() {
        StaticFile file = service.find("/").orElseThrow();

        assertEquals("index.html", file.name());
        assertEquals("text/html; charset=UTF-8", file.contentType());
        assertTrue(new String(file.content(), StandardCharsets.UTF_8).contains("<!DOCTYPE html>"));
    }

    @Test
    @DisplayName("Each resource is described with the content type the browser needs")
    void describesEachResource() {
        assertEquals("text/css; charset=UTF-8", service.find("/styles.css").orElseThrow().contentType());
        assertEquals("text/javascript; charset=UTF-8", service.find("/app.js").orElseThrow().contentType());
        assertEquals("image/png", service.find("/images/logo.png").orElseThrow().contentType());
    }

    @Test
    @DisplayName("A binary resource is read as bytes and keeps its signature")
    void servesBinaryResources() {
        byte[] png = service.find("/images/logo.png").orElseThrow().content();

        assertTrue(png.length > 1000);
        // The eight bytes that identify a PNG file.
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'},
                java.util.Arrays.copyOf(png, 8));
    }

    @Test
    @DisplayName("A resource that does not exist is not found, so the server can answer 404")
    void doesNotFindMissingResources() {
        assertTrue(service.find("/missing.html").isEmpty());
        assertTrue(service.find("/images/missing.png").isEmpty());
    }

    @Test
    @DisplayName("An extension the framework cannot describe is not published")
    void doesNotPublishUnknownExtensions() {
        assertTrue(service.find("/application.zip").isEmpty());
        assertTrue(service.find("/README").isEmpty());
    }

    @Test
    @DisplayName("Any attempt to leave the static folder is refused")
    void refusesPathTraversal() {
        assertTrue(service.find("/../pom.xml").isEmpty());
        assertTrue(service.find("/images/../../pom.xml").isEmpty());
        assertTrue(service.find("/..\\..\\pom.xml").isEmpty());
    }

    @Test
    @DisplayName("The configured folder is what is served")
    void usesTheConfiguredFolder() {
        service.setFolder("/does-not-exist");

        assertTrue(service.find("/index.html").isEmpty());

        service.setFolder("webroot");

        assertTrue(service.find("/index.html").isPresent());
    }

    @Test
    @DisplayName("An external directory takes precedence over the packaged resources")
    void externalDirectoryWins(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("index.html"), "<!DOCTYPE html><p>from disk</p>");
        service.setExternalRoot(directory);

        StaticFile file = service.find("/").orElseThrow();

        assertEquals("<!DOCTYPE html><p>from disk</p>",
                new String(file.content(), StandardCharsets.UTF_8));
        // A file that only exists in the jar is still served.
        assertTrue(service.find("/app.js").isPresent());
    }

    @Test
    @DisplayName("A file outside the external directory is never read, even through a link path")
    void externalDirectoryIsABoundary(@TempDir Path directory) {
        service.setExternalRoot(directory);

        assertTrue(service.find("/../pom.xml").isEmpty());
    }
}
