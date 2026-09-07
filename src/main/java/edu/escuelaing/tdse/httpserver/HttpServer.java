package edu.escuelaing.tdse.httpserver;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Section 2.2 - A sequential server that accepts many successive requests.
 *
 * <p>The listening socket stays open for the whole life of the process. Every accepted connection
 * is handled completely - read the request, produce the response, write it, close the client
 * socket - before the next connection is accepted. This is repetition, not concurrency: no thread,
 * executor or asynchronous mechanism exists in this class on purpose.</p>
 */
public class HttpServer {

    /** Guards the process against a client that opens a socket and never sends a request. */
    private static final int READ_TIMEOUT_MILLIS = 3_000;

    private static final int BACKLOG = 50;

    private final int requestedPort;
    private final RequestHandler handler;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public HttpServer(int port, RequestHandler handler) {
        this.requestedPort = port;
        this.handler = handler;
    }

    /**
     * Binds the port and serves requests until {@link #stop()} is called.
     *
     * @throws IOException if the port cannot be bound
     */
    public void start() throws IOException {
        // Binding on the wildcard address is what later makes the application reachable from
        // outside the EC2 instance instead of only from its own loopback interface.
        serverSocket = new ServerSocket(requestedPort, BACKLOG);
        running = true;
        System.out.println("Server listening on port " + getPort() + " - press Ctrl+C to stop");

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
        }
    }

    /** Reads one request from an accepted connection and writes exactly one response. */
    private void serve(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());

        HttpResponse response;
        String requestSummary;
        try {
            HttpRequest request = HttpRequest.parse(in);
            requestSummary = request.toString();
            response = handler.handle(request);
        } catch (MalformedRequestException e) {
            requestSummary = "<malformed request>";
            response = HttpResponse.text(400, "Bad Request: " + e.getMessage());
        } catch (SocketTimeoutException e) {
            // Browsers frequently pre-open sockets they never use. Dropping them keeps the
            // single request loop available for real requests.
            System.out.println("Idle connection closed after " + READ_TIMEOUT_MILLIS + " ms");
            return;
        } catch (RuntimeException e) {
            requestSummary = "<failed request>";
            System.err.println("Unhandled error: " + e);
            response = HttpResponse.text(500, "Internal Server Error");
        }

        response.writeTo(out);
        System.out.println(requestSummary + " -> " + response.getStatusCode() + " "
                + response.getContentType() + " (" + response.getContentLength() + " bytes)");
    }

    /** Closes the listening socket and ends the accept loop. */
    public void stop() {
        running = false;
        ServerSocket socket = serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error while closing the listening socket: " + e.getMessage());
            }
        }
    }

    /** @return the port actually bound, useful when the server is started on port 0 in tests. */
    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? requestedPort : socket.getLocalPort();
    }

    public boolean isRunning() {
        return running;
    }
}
