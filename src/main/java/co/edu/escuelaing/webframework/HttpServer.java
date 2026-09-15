package co.edu.escuelaing.webframework;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The part of the framework that talks to the network: it accepts connections, reads requests,
 * asks the router or the static-file service for an answer and writes the response.
 *
 * <p>The loop is deliberately sequential. Each connection is served completely before the next one
 * is accepted, and no thread, executor or asynchronous mechanism exists in this class. What the
 * application developer adds are routes, never changes to this loop.</p>
 */
public class HttpServer {

    /** Guards the loop against a client that opens a socket and never sends a request. */
    private static final int READ_TIMEOUT_MILLIS = 3_000;

    private static final int BACKLOG = 50;
    private static final String SERVER_NAME = "webframework/1.0";

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    private final Router router;
    private final StaticFileService staticFiles;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public HttpServer(Router router, StaticFileService staticFiles) {
        this.router = router;
        this.staticFiles = staticFiles;
    }

    /**
     * Binds the port and serves requests until {@link #stop()} is called.
     *
     * <p>The socket is bound to the wildcard address and not to the loopback interface, which is
     * what makes the application reachable when it runs on a cloud host.</p>
     *
     * @param port TCP port; 0 asks the operating system for a free one, which the tests use
     * @throws IOException if the port cannot be bound
     */
    public void start(int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress("0.0.0.0", port), BACKLOG);
        running = true;
        System.out.println("Server listening on port " + getPort());

        while (running) {
            try (Socket clientSocket = serverSocket.accept()) {
                clientSocket.setSoTimeout(READ_TIMEOUT_MILLIS);
                serve(clientSocket);
            } catch (SocketException e) {
                if (running) {
                    System.err.println("Connection error: " + e.getMessage());
                }
            } catch (IOException e) {
                // A single failing connection must never terminate the whole server.
                System.err.println("Connection error: " + e.getMessage());
            }
            // The loop condition is checked only after the current response has been written and
            // the client socket closed: a shutdown request is answered before the server stops.
        }

        closeServerSocket();
        System.out.println("Server stopped gracefully.");
    }

    /**
     * Asks the server to stop once the request being served has been answered.
     *
     * <p>It does not interrupt the current exchange: the response is written, the client socket is
     * closed, the loop ends and only then is the listening socket released.</p>
     */
    public void stop() {
        running = false;
    }

    /** Reads one request from an accepted connection and writes exactly one response. */
    private void serve(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());

        Request request = null;
        Response response = new Response();
        byte[] body;
        String summary;

        try {
            request = RequestParser.parse(in);
            summary = request.toString();
            body = answer(request, response);
        } catch (MalformedRequestException e) {
            summary = "<malformed request>";
            response.status(400).type(Response.TEXT_PLAIN);
            body = ("400 Bad Request: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
        } catch (SocketTimeoutException e) {
            // Browsers routinely pre-open sockets they never use; dropping them keeps the single
            // request loop available for real requests.
            System.out.println("Idle connection closed after " + READ_TIMEOUT_MILLIS + " ms");
            return;
        } catch (RuntimeException e) {
            summary = request == null ? "<failed request>" : request.toString();
            System.err.println("Unhandled error while serving " + summary + ": " + e);
            response = new Response().status(500).type(Response.TEXT_PLAIN);
            body = "500 Internal Server Error".getBytes(StandardCharsets.UTF_8);
        }

        write(out, response, body);
        System.out.println(summary + " -> " + response.getStatus() + " "
                + response.getContentType() + " (" + body.length + " bytes)");
    }

    /**
     * Route resolution: a registered service first, then the static folder, then 404.
     */
    private byte[] answer(Request request, Response response) {
        Optional<Route> route = router.find(request.getMethod(), request.getPath());
        if (route.isPresent()) {
            String result = route.get().getHandler().handle(request, response);
            return result == null ? new byte[0] : result.getBytes(StandardCharsets.UTF_8);
        }

        if ("GET".equals(request.getMethod())) {
            Optional<StaticFile> file = staticFiles.find(request.getPath());
            if (file.isPresent()) {
                response.type(file.get().contentType());
                return file.get().content();
            }
        }

        response.status(404).type(Response.TEXT_PLAIN);
        return "404 Not Found".getBytes(StandardCharsets.UTF_8);
    }

    private void write(OutputStream output, Response response, byte[] body) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(response.getStatus()).append(' ')
                .append(reasonPhrase(response.getStatus())).append("\r\n");
        head.append("Date: ").append(HTTP_DATE.format(ZonedDateTime.now(ZoneOffset.UTC))).append("\r\n");
        head.append("Server: ").append(SERVER_NAME).append("\r\n");
        head.append("Content-Type: ").append(response.getContentType()).append("\r\n");
        // The length is the number of bytes, never the number of characters.
        head.append("Content-Length: ").append(body.length).append("\r\n");
        for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");

        output.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    private void closeServerSocket() {
        ServerSocket socket = serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error while closing the listening socket: " + e.getMessage());
            }
        }
    }

    /** @return the port actually bound, useful when the server was started on port 0. */
    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    public boolean isRunning() {
        return running;
    }

    private static String reasonPhrase(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }
}
