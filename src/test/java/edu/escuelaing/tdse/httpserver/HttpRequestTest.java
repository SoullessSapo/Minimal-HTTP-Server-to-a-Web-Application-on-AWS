package edu.escuelaing.tdse.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the request parser. */
class HttpRequestTest {

    private static HttpRequest parse(String rawRequest) throws Exception {
        return HttpRequest.parse(new BufferedReader(new StringReader(rawRequest)));
    }

    @Test
    @DisplayName("The request line is split into method, target and protocol")
    void parsesRequestLine() throws Exception {
        HttpRequest request = parse("GET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n");

        assertEquals("GET", request.getMethod());
        assertEquals("/index.html", request.getTarget());
        assertEquals("/index.html", request.getPath());
        assertEquals("HTTP/1.1", request.getProtocol());
        assertEquals("localhost", request.getHeaders().get("host"));
    }

    @Test
    @DisplayName("Header names are matched without case sensitivity")
    void normalisesHeaderNames() throws Exception {
        HttpRequest request = parse("GET / HTTP/1.1\r\nAccept: application/json\r\n\r\n");

        assertEquals("application/json", request.getHeaders().get("accept"));
    }

    @Test
    @DisplayName("The path and the query string are separated and decoded")
    void decodesQueryParameters() throws Exception {
        HttpRequest request = parse("GET /app/hello?name=Ana%20Mar%C3%ADa&value=2 HTTP/1.1\r\n\r\n");

        assertEquals("/app/hello", request.getPath());
        assertEquals("Ana María", request.getQueryParameter("name").orElseThrow());
        assertEquals("2", request.getQueryParameter("value").orElseThrow());
        assertTrue(request.getQueryParameter("missing").isEmpty());
    }

    @Test
    @DisplayName("A plus sign in a query value is a space")
    void decodesPlusAsSpace() throws Exception {
        HttpRequest request = parse("GET /app/hello?name=Ana+Maria HTTP/1.1\r\n\r\n");

        assertEquals("Ana Maria", request.getQueryParameter("name").orElseThrow());
    }

    @Test
    @DisplayName("An encoded path is decoded before it is used")
    void decodesPath() throws Exception {
        HttpRequest request = parse("GET /images/%2e%2e/pom.xml HTTP/1.1\r\n\r\n");

        assertEquals("/images/../pom.xml", request.getPath());
    }

    @Test
    @DisplayName("A request line that is not METHOD TARGET PROTOCOL is rejected")
    void rejectsMalformedRequestLine() {
        assertThrows(MalformedRequestException.class, () -> parse("GARBAGE\r\n\r\n"));
        assertThrows(MalformedRequestException.class, () -> parse("GET /only-two-parts\r\n\r\n"));
        assertThrows(MalformedRequestException.class, () -> parse("GET / SPDY/3\r\n\r\n"));
        assertThrows(MalformedRequestException.class,
                () -> parse("GET http://elsewhere/ HTTP/1.1\r\n\r\n"));
    }

    @Test
    @DisplayName("An empty request is rejected instead of producing a null request")
    void rejectsEmptyRequest() {
        assertThrows(MalformedRequestException.class, () -> parse(""));
    }

    @Test
    @DisplayName("Invalid percent-encoding is rejected")
    void rejectsInvalidEncoding() {
        assertThrows(MalformedRequestException.class,
                () -> parse("GET /app/hello?name=%ZZ HTTP/1.1\r\n\r\n"));
    }

    @Test
    @DisplayName("Reading stops at the blank line, no body is consumed")
    void stopsAtTheBlankLine() throws IOException, MalformedRequestException {
        BufferedReader reader = new BufferedReader(
                new StringReader("GET / HTTP/1.1\r\nHost: localhost\r\n\r\nleftover"));

        HttpRequest request = HttpRequest.parse(reader);

        assertEquals("/", request.getPath());
        assertEquals("leftover", reader.readLine());
    }
}
