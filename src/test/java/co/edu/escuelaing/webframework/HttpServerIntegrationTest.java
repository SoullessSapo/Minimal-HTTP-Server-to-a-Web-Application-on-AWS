package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end tests over real sockets.
 *
 * <p>The server itself stays sequential. The extra thread here belongs to the test: it exists only
 * because {@link HttpServer#start(int)} blocks while it owns the accept loop.</p>
 */
class HttpServerIntegrationTest {

    private static HttpServer server;
    private static HttpClient client;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        Router router = new Router();
        router.get("/hello", (req, resp) -> "Hello " + req.getValueOrDefault("name", "world"));
        router.get("/pi", (req, resp) -> String.valueOf(Math.PI));
        router.get("/json", (req, resp) -> {
            resp.type(Response.APPLICATION_JSON).header("X-Demo", "yes");
            return "{\"ok\":true}";
        });
        router.get("/invalid", (req, resp) -> {
            resp.status(400);
            return "The parameter is required";
        });
        router.get("/broken", (req, resp) -> {
            throw new IllegalStateException("the handler failed on purpose");
        });
        // A route wins over a file with the same name.
        router.get("/index.html", (req, resp) -> "handled by a route");

        server = new HttpServer(router, new StaticFileService("/webroot"));
        port = startInBackground(server);
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    /** Starts a server on an ephemeral port and returns the port it bound. */
    private static int startInBackground(HttpServer target) throws InterruptedException {
        Thread thread = new Thread(() -> {
            try {
                target.start(0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "test-http-server");
        thread.setDaemon(true);
        thread.start();

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && (!target.isRunning() || target.getPort() <= 0)) {
            Thread.sleep(20);
        }
        assertTrue(target.getPort() > 0, "the server did not bind a port");
        return target.getPort();
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return get(port, path);
    }

    private static HttpResponse<String> get(int serverPort, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + serverPort + path))
                .timeout(Duration.ofSeconds(20))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Sends a request line verbatim, without letting a client library normalise it. */
    private static String rawRequest(String requestLine) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write(
                    (requestLine + "\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ lambda services

    @Test
    @DisplayName("A lambda service answers with the value it returns")
    void servesALambdaRoute() throws Exception {
        HttpResponse<String> response = get("/pi");

        assertEquals(200, response.statusCode());
        assertEquals(String.valueOf(Math.PI), response.body());
        assertEquals("text/plain; charset=UTF-8",
                response.headers().firstValue("content-type").orElseThrow());
    }

    @Test
    @DisplayName("A query-string value reaches the lambda")
    void readsQueryValues() throws Exception {
        assertEquals("Hello Pedro", get("/hello?name=Pedro").body());
        assertEquals("Hello Ana María", get("/hello?name=Ana%20Mar%C3%ADa").body());
    }

    @Test
    @DisplayName("A missing query parameter does not fail the request")
    void survivesAMissingParameter() throws Exception {
        HttpResponse<String> response = get("/hello");

        assertEquals(200, response.statusCode());
        assertEquals("Hello world", response.body());
    }

    @Test
    @DisplayName("Content-Length is the number of bytes, not of characters")
    void reportsTheByteLength() throws Exception {
        HttpResponse<String> response = get("/hello?name=Ana%20Mar%C3%ADa");

        assertEquals(String.valueOf(response.body().getBytes(StandardCharsets.UTF_8).length),
                response.headers().firstValue("content-length").orElseThrow());
    }

    @Test
    @DisplayName("A handler can change the status, the content type and the headers")
    void handlerControlsTheResponse() throws Exception {
        HttpResponse<String> json = get("/json");
        assertEquals("application/json; charset=UTF-8",
                json.headers().firstValue("content-type").orElseThrow());
        assertEquals("yes", json.headers().firstValue("X-Demo").orElseThrow());

        assertEquals(400, get("/invalid").statusCode());
    }

    // ------------------------------------------------------------------ static resources

    @Test
    @DisplayName("Files of the static folder are served with their own content type")
    void servesStaticResources() throws Exception {
        assertEquals("text/css; charset=UTF-8",
                get("/styles.css").headers().firstValue("content-type").orElseThrow());
        assertEquals("text/javascript; charset=UTF-8",
                get("/app.js").headers().firstValue("content-type").orElseThrow());

        HttpResponse<String> home = get("/");
        assertEquals(200, home.statusCode());
        assertTrue(home.body().contains("<script src=\"/app.js\">"));
    }

    @Test
    @DisplayName("An image arrives byte for byte")
    void servesImages() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/images/logo.png")).build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        assertEquals("image/png", response.headers().firstValue("content-type").orElseThrow());
        assertEquals(String.valueOf(response.body().length),
                response.headers().firstValue("content-length").orElseThrow());
        assertEquals((byte) 0x89, response.body()[0]);
    }

    @Test
    @DisplayName("A registered route wins over a file with the same path")
    void routesWinOverFiles() throws Exception {
        assertEquals("handled by a route", get("/index.html").body());
    }

    // ------------------------------------------------------------------ error handling

    @Test
    @DisplayName("An unknown resource returns 404 and the server keeps serving")
    void returnsNotFound() throws Exception {
        HttpResponse<String> response = get("/unknown");

        assertEquals(404, response.statusCode());
        assertEquals("404 Not Found", response.body());
        assertEquals(200, get("/pi").statusCode());
    }

    @Test
    @DisplayName("A path that tries to leave the static folder discloses nothing")
    void refusesPathTraversal() throws Exception {
        String response = rawRequest("GET /../pom.xml HTTP/1.1");

        assertTrue(response.startsWith("HTTP/1.1 404 Not Found"), response);
        assertFalse(response.contains("modelVersion"));
    }

    @Test
    @DisplayName("A malformed request is answered with 400 and does not stop the server")
    void survivesMalformedRequests() throws Exception {
        String response = rawRequest("THIS IS NOT HTTP");

        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request"), response);
        assertEquals(200, get("/pi").statusCode(), "the server must still be serving");
    }

    @Test
    @DisplayName("A handler that throws produces 500 and does not stop the server")
    void survivesAFailingHandler() throws Exception {
        HttpResponse<String> response = get("/broken");

        assertEquals(500, response.statusCode());
        assertFalse(response.body().contains("IllegalStateException"));
        assertEquals(200, get("/pi").statusCode(), "the server must still be serving");
    }

    @Test
    @DisplayName("Ten consecutive requests succeed in one server run")
    void servesManyConsecutiveRequests() throws Exception {
        for (int i = 1; i <= 10; i++) {
            assertEquals(200, get("/hello?name=user" + i).statusCode(), "request number " + i);
        }
        assertTrue(server.isRunning());
    }

    // ------------------------------------------------------------------ graceful shutdown

    @Test
    @DisplayName("A shutdown route answers first and only then stops the server")
    void stopsGracefully() throws Exception {
        Router router = new Router();
        HttpServer stoppable = new HttpServer(router, new StaticFileService("/webroot"));
        router.get("/shutdown", (req, resp) -> {
            stoppable.stop();
            return "Server will stop after this response.";
        });

        int stoppablePort = startInBackground(stoppable);
        assertTrue(stoppable.isRunning(), "the server should be serving before the shutdown call");

        HttpResponse<String> response = get(stoppablePort, "/shutdown");

        // The request being served is answered completely before the loop ends.
        assertEquals(200, response.statusCode());
        assertEquals("Server will stop after this response.", response.body());

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && stoppable.isRunning()) {
            Thread.sleep(20);
        }
        assertFalse(stoppable.isRunning(), "the server should have left its loop");
    }
}
