package edu.escuelaing.tdse.httpserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One HTTP response ready to be written to a socket.
 *
 * <p>The body is always kept as bytes. Text and binary resources therefore travel through the very
 * same code path, and {@code Content-Length} is always the real number of bytes instead of the
 * number of characters of a string.</p>
 */
public final class HttpResponse {

    public static final String TEXT_HTML = "text/html; charset=UTF-8";
    public static final String TEXT_PLAIN = "text/plain; charset=UTF-8";
    public static final String APPLICATION_JSON = "application/json; charset=UTF-8";

    private static final String SERVER_NAME = "minimal-http-server/1.0";

    /** RFC 7231 date format: a fixed two digit day and the GMT zone, always in English. */
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    private static final byte[] NO_BODY = new byte[0];

    private final int statusCode;
    private final String reasonPhrase;
    private final String contentType;
    private final byte[] body;
    private final Map<String, String> headers = new LinkedHashMap<>();

    public HttpResponse(int statusCode, String contentType, byte[] body) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhraseFor(statusCode);
        this.contentType = contentType;
        this.body = body == null ? NO_BODY : body;
    }

    public static HttpResponse html(int statusCode, String html) {
        return new HttpResponse(statusCode, TEXT_HTML, html.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse text(int statusCode, String text) {
        return new HttpResponse(statusCode, TEXT_PLAIN, text.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse json(int statusCode, String json) {
        return new HttpResponse(statusCode, APPLICATION_JSON, json.getBytes(StandardCharsets.UTF_8));
    }

    /** Adds a response header, for example {@code Allow} on a 405 response. */
    public HttpResponse withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /** Serialises status line, headers, the mandatory blank line and the body. */
    public void writeTo(OutputStream output) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(statusCode).append(' ').append(reasonPhrase).append("\r\n");
        head.append("Date: ").append(HTTP_DATE.format(ZonedDateTime.now(ZoneOffset.UTC))).append("\r\n");
        head.append("Server: ").append(SERVER_NAME).append("\r\n");
        head.append("Content-Type: ").append(contentType).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        head.append("Connection: close\r\n");
        head.append("\r\n");

        output.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public String getContentType() {
        return contentType;
    }

    public int getContentLength() {
        return body.length;
    }

    public byte[] getBody() {
        return body.clone();
    }

    private static String reasonPhraseFor(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 414 -> "URI Too Long";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }
}
