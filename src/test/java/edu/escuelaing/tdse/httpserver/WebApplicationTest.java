package edu.escuelaing.tdse.httpserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the hardcoded services and of the fall through to the static resources. */
class WebApplicationTest {

    private WebApplication application;

    @BeforeEach
    void setUp() {
        application = new WebApplication(new StaticResourceHandler());
    }

    private HttpResponse get(String target) throws Exception {
        HttpRequest request = HttpRequest.parse(
                new BufferedReader(new StringReader(target + " HTTP/1.1\r\nHost: localhost\r\n\r\n")));
        return application.handle(request);
    }

    private static String bodyOf(HttpResponse response) {
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }

    private static String rawOf(HttpResponse response) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        response.writeTo(buffer);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    // ----------------------------------------------------------------- greeting

    @Test
    @DisplayName("The greeting service answers JSON built from the query parameter")
    void greetsWithTheSuppliedName() throws Exception {
        HttpResponse response = get("GET /app/hello?name=Ana");

        assertEquals(200, response.getStatusCode());
        assertEquals(HttpResponse.APPLICATION_JSON, response.getContentType());
        assertEquals("{\"service\":\"greeting\",\"name\":\"Ana\",\"greeting\":\"Hello, Ana!\"}",
                bodyOf(response));
    }

    @Test
    @DisplayName("A greeting without a name is a client error, not a server error")
    void rejectsMissingName() throws Exception {
        HttpResponse response = get("GET /app/hello");

        assertEquals(400, response.getStatusCode());
        assertEquals(HttpResponse.APPLICATION_JSON, response.getContentType());
        assertTrue(bodyOf(response).contains("required"));
    }

    @Test
    @DisplayName("A blank name is refused as well")
    void rejectsBlankName() throws Exception {
        assertEquals(400, get("GET /app/hello?name=%20%20").getStatusCode());
    }

    @Test
    @DisplayName("An excessively long name is refused")
    void rejectsLongName() throws Exception {
        HttpResponse response = get("GET /app/hello?name=" + "a".repeat(200));

        assertEquals(400, response.getStatusCode());
    }

    @Test
    @DisplayName("A name that contains markup is escaped before it enters the document")
    void escapesUntrustedNames() throws Exception {
        HttpResponse response = get("GET /app/hello?name=%3Cscript%3E");

        assertEquals(200, response.getStatusCode());
        assertTrue(bodyOf(response).contains("\\u003cscript\\u003e"));
        assertTrue(bodyOf(response).indexOf('<') < 0);
    }

    // ------------------------------------------------------------------- square

    @Test
    @DisplayName("The square service returns the input and its square")
    void calculatesTheSquare() throws Exception {
        HttpResponse response = get("GET /app/square?value=7");

        assertEquals(200, response.getStatusCode());
        assertEquals("{\"service\":\"square\",\"input\":7,\"square\":49}", bodyOf(response));
    }

    @Test
    @DisplayName("Decimal and negative values are supported")
    void calculatesTheSquareOfADecimal() throws Exception {
        assertTrue(bodyOf(get("GET /app/square?value=-2.5")).contains("\"square\":6.25"));
    }

    @Test
    @DisplayName("A value that is not a number is a client error")
    void rejectsNonNumericValues() throws Exception {
        assertEquals(400, get("GET /app/square?value=abc").getStatusCode());
        assertEquals(400, get("GET /app/square?value=NaN").getStatusCode());
        assertEquals(400, get("GET /app/square?value=Infinity").getStatusCode());
        assertEquals(400, get("GET /app/square").getStatusCode());
    }

    @Test
    @DisplayName("A result outside the representable range is refused instead of returning infinity")
    void rejectsOverflow() throws Exception {
        HttpResponse response = get("GET /app/square?value=1e300");

        assertEquals(400, response.getStatusCode());
        assertTrue(bodyOf(response).contains("range"));
    }

    // ------------------------------------------------------- time, health, slow

    @Test
    @DisplayName("The time service returns state produced by the server")
    void returnsTheServerTime() throws Exception {
        HttpResponse response = get("GET /app/time");

        assertEquals(200, response.getStatusCode());
        assertTrue(bodyOf(response).contains("\"serverTime\""));
        assertTrue(bodyOf(response).contains("\"epochMillis\""));
    }

    @Test
    @DisplayName("The health service reports that the process can serve requests")
    void reportsHealth() throws Exception {
        HttpResponse response = get("GET /health");

        assertEquals(200, response.getStatusCode());
        assertTrue(bodyOf(response).contains("\"status\":\"ok\""));
    }

    @Test
    @DisplayName("The slow service waits for the requested time before answering")
    void waitsBeforeAnswering() throws Exception {
        long startedAt = System.currentTimeMillis();

        HttpResponse response = get("GET /app/slow?seconds=1");

        assertEquals(200, response.getStatusCode());
        assertTrue(System.currentTimeMillis() - startedAt >= 900,
                "the service must really keep the request loop busy");
    }

    // ------------------------------------------------------------ static area

    @Test
    @DisplayName("A path that is not a service is served from the public resources area")
    void fallsThroughToStaticResources() throws Exception {
        HttpResponse home = get("GET /");
        HttpResponse script = get("GET /app.js");
        HttpResponse image = get("GET /images/logo.png");

        assertEquals(200, home.getStatusCode());
        assertEquals(HttpResponse.TEXT_HTML, home.getContentType());
        assertEquals(200, script.getStatusCode());
        assertEquals("text/javascript; charset=UTF-8", script.getContentType());
        assertEquals(200, image.getStatusCode());
        assertEquals("image/png", image.getContentType());
        assertTrue(image.getContentLength() > 0);
    }

    @Test
    @DisplayName("A missing resource returns 404")
    void returnsNotFound() throws Exception {
        assertEquals(404, get("GET /missing.html").getStatusCode());
        assertEquals(404, get("GET /app/unknown-service").getStatusCode());
    }

    @Test
    @DisplayName("A path that leaves the public area returns 403 and discloses nothing")
    void refusesPathTraversal() throws Exception {
        HttpResponse response = get("GET /images/%2e%2e/%2e%2e/pom.xml");

        assertEquals(403, response.getStatusCode());
        assertTrue(bodyOf(response).indexOf("modelVersion") < 0);
    }

    @Test
    @DisplayName("A method other than GET returns 405 and announces the accepted method")
    void refusesOtherMethods() throws Exception {
        HttpResponse response = get("POST /app/hello?name=Ana");

        assertEquals(405, response.getStatusCode());
        assertTrue(rawOf(response).contains("Allow: GET"));
    }

    @Test
    @DisplayName("Content-Length is the number of bytes, not the number of characters")
    void reportsTheByteLength() throws Exception {
        HttpResponse response = get("GET /app/hello?name=Ana%20Mar%C3%ADa");

        assertEquals(bodyOf(response).getBytes(StandardCharsets.UTF_8).length,
                response.getContentLength());
        assertTrue(response.getContentLength() > bodyOf(response).length(),
                "the accented name occupies more bytes than characters");
    }
}
