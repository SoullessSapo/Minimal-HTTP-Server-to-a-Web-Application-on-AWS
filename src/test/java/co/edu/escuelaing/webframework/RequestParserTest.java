package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the parser and of the query-string values the application reads. */
class RequestParserTest {

    private static Request parse(String rawRequest) throws Exception {
        return RequestParser.parse(new BufferedReader(new StringReader(rawRequest)));
    }

    @Test
    @DisplayName("The request line is split into method, path and query string")
    void parsesTheRequestLine() throws Exception {
        Request request = parse("GET /hello?name=Pedro HTTP/1.1\r\nHost: localhost\r\n\r\n");

        assertEquals("GET", request.getMethod());
        assertEquals("/hello", request.getPath());
        assertEquals("name=Pedro", request.getQueryString());
        assertEquals("localhost", request.getHeader("Host"));
    }

    @Test
    @DisplayName("getValue returns the value of a query parameter")
    void readsAQueryValue() throws Exception {
        Request request = parse("GET /hello?name=Pedro HTTP/1.1\r\n\r\n");

        assertEquals("Pedro", request.getValue("name"));
    }

    @Test
    @DisplayName("Several parameters can be read from the same request")
    void readsSeveralQueryValues() throws Exception {
        Request request = parse("GET /hello?name=Pedro&language=en HTTP/1.1\r\n\r\n");

        assertEquals("Pedro", request.getValue("name"));
        assertEquals("en", request.getValue("language"));
    }

    @Test
    @DisplayName("A missing parameter returns null instead of failing")
    void missingParameterIsNull() throws Exception {
        Request request = parse("GET /hello HTTP/1.1\r\n\r\n");

        assertNull(request.getValue("name"));
        assertEquals("", request.getQueryString());
        assertEquals("world", request.getValueOrDefault("name", "world"));
        assertTrue(request.getValues("name").isEmpty());
    }

    @Test
    @DisplayName("A parameter present but empty falls back to the default value")
    void blankParameterUsesTheDefault() throws Exception {
        Request request = parse("GET /hello?name= HTTP/1.1\r\n\r\n");

        assertEquals("", request.getValue("name"));
        assertEquals("world", request.getValueOrDefault("name", "world"));
    }

    @Test
    @DisplayName("Values are percent-decoded, and a plus sign is a space")
    void decodesValues() throws Exception {
        assertEquals("Ana María", parse("GET /hello?name=Ana%20Mar%C3%ADa HTTP/1.1\r\n\r\n")
                .getValue("name"));
        assertEquals("Ana Maria", parse("GET /hello?name=Ana+Maria HTTP/1.1\r\n\r\n")
                .getValue("name"));
    }

    @Test
    @DisplayName("A repeated parameter keeps every value")
    void keepsRepeatedValues() throws Exception {
        Request request = parse("GET /tags?tag=java&tag=http HTTP/1.1\r\n\r\n");

        assertEquals("java", request.getValue("tag"));
        assertEquals(List.of("java", "http"), request.getValues("tag"));
    }

    @Test
    @DisplayName("The path is decoded before anything else looks at it")
    void decodesThePath() throws Exception {
        assertEquals("/images/../logo.png",
                parse("GET /images/%2e%2e/logo.png HTTP/1.1\r\n\r\n").getPath());
    }

    @Test
    @DisplayName("Header names are matched without case sensitivity")
    void normalisesHeaderNames() throws Exception {
        Request request = parse("GET / HTTP/1.1\r\nAccept: text/html\r\n\r\n");

        assertEquals("text/html", request.getHeader("accept"));
        assertEquals("text/html", request.getHeader("ACCEPT"));
    }

    @Test
    @DisplayName("A request that is not HTTP is rejected instead of crashing the server")
    void rejectsMalformedRequests() {
        assertThrows(MalformedRequestException.class, () -> parse("GARBAGE\r\n\r\n"));
        assertThrows(MalformedRequestException.class, () -> parse("GET /only-two\r\n\r\n"));
        assertThrows(MalformedRequestException.class, () -> parse("GET / SPDY/3\r\n\r\n"));
        assertThrows(MalformedRequestException.class, () -> parse("GET http://elsewhere/ HTTP/1.1\r\n\r\n"));
        assertThrows(MalformedRequestException.class, () -> parse(""));
        assertThrows(MalformedRequestException.class, () -> parse("GET /hello?name=%ZZ HTTP/1.1\r\n\r\n"));
    }
}
