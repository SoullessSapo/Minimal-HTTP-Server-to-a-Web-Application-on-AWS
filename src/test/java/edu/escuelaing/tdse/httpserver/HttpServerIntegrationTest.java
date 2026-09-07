package edu.escuelaing.tdse.httpserver;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end tests over a real socket.
 *
 * <p>The server itself stays sequential. The extra thread here belongs to the test: it only exists
 * because {@link edu.escuelaing.tdse.httpserver.HttpServer#start()} blocks while it owns the accept
 * loop.</p>
 */
class HttpServerIntegrationTest {

    private static edu.escuelaing.tdse.httpserver.HttpServer server;
    private static HttpClient client;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        server = new edu.escuelaing.tdse.httpserver.HttpServer(0,
                new WebApplication(new StaticResourceHandler()));

        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "test-http-server");
        serverThread.setDaemon(true);
        serverThread.start();

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && (!server.isRunning() || server.getPort() <= 0)) {
            Thread.sleep(20);
        }
        port = server.getPort();
        assertTrue(port > 0, "the server did not bind a port");

        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return client.send(request(path).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<byte[]> getBytes(String path) throws Exception {
        return client.send(request(path).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30));
    }

    /** Sends a request line verbatim, without letting any client library normalise it. */
    private static String rawRequest(String requestLine) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write(
                    (requestLine + "\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("The home page and every resource it references are served with their own type")
    void servesTheHomePageAndItsResources() throws Exception {
        HttpResponse<String> home = get("/");
        assertEquals(200, home.statusCode());
        assertEquals("text/html; charset=UTF-8", home.headers().firstValue("content-type").orElseThrow());
        assertTrue(home.body().contains("<script src=\"/app.js\">"));

        assertEquals("text/css; charset=UTF-8",
                get("/styles.css").headers().firstValue("content-type").orElseThrow());
        assertEquals("text/javascript; charset=UTF-8",
                get("/app.js").headers().firstValue("content-type").orElseThrow());
        assertEquals("image/jpeg",
                get("/images/request-flow.jpg").headers().firstValue("content-type").orElseThrow());
    }

    @Test
    @DisplayName("An image arrives byte for byte, described by its length")
    void servesImagesAsBytes() throws Exception {
        byte[] expected;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("public/images/logo.png")) {
            expected = stream.readAllBytes();
        }

        HttpResponse<byte[]> response = getBytes("/images/logo.png");

        assertEquals(200, response.statusCode());
        assertEquals("image/png", response.headers().firstValue("content-type").orElseThrow());
        assertEquals(String.valueOf(expected.length),
                response.headers().firstValue("content-length").orElseThrow());
        assertArrayEquals(expected, response.body());
    }

    @Test
    @DisplayName("The three services answer JSON through real HTTP requests")
    void servesTheHardcodedServices() throws Exception {
        HttpResponse<String> greeting = get("/app/hello?name=Ana%20Mar%C3%ADa");
        assertEquals(200, greeting.statusCode());
        assertEquals("application/json; charset=UTF-8",
                greeting.headers().firstValue("content-type").orElseThrow());
        assertTrue(greeting.body().contains("Hello, Ana María!"));

        assertTrue(get("/app/square?value=9").body().contains("\"square\":81"));
        assertTrue(get("/app/time").body().contains("\"serverTime\""));
        assertTrue(get("/health").body().contains("\"status\":\"ok\""));
    }

    @Test
    @DisplayName("Invalid input produces a controlled 400 instead of a stack trace")
    void rejectsInvalidInput() throws Exception {
        HttpResponse<String> response = get("/app/square?value=abc");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"error\""));
        assertFalse(response.body().contains("Exception"));
    }

    @Test
    @DisplayName("A missing static file returns 404")
    void returnsNotFoundForMissingFiles() throws Exception {
        assertEquals(404, get("/does-not-exist.html").statusCode());
    }

    @Test
    @DisplayName("A method that is not GET returns 405 with the Allow header")
    void returnsMethodNotAllowed() throws Exception {
        HttpResponse<String> response = client.send(
                request("/").POST(HttpRequest.BodyPublishers.ofString("x")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("GET", response.headers().firstValue("allow").orElseThrow());
    }

    @Test
    @DisplayName("A path that tries to leave the public area is rejected and discloses nothing")
    void rejectsPathTraversal() throws Exception {
        String response = rawRequest("GET /../pom.xml HTTP/1.1");

        assertTrue(response.startsWith("HTTP/1.1 403 Forbidden"), response);
        assertFalse(response.contains("modelVersion"));
    }

    @Test
    @DisplayName("A malformed request is answered with 400 and does not stop the server")
    void survivesMalformedRequests() throws Exception {
        String response = rawRequest("THIS IS NOT HTTP");

        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request"), response);
        assertEquals(200, get("/health").statusCode(), "the server must still be serving");
    }

    @Test
    @DisplayName("Ten consecutive operations succeed in one server run")
    void servesManyConsecutiveRequests() throws Exception {
        for (int i = 1; i <= 10; i++) {
            assertEquals(200, get("/app/square?value=" + i).statusCode(), "request number " + i);
        }
        assertEquals(200, get("/").statusCode());
        assertTrue(server.isRunning());
    }
}
