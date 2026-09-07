package edu.escuelaing.tdse.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Section 2.1 - Baseline verification.
 *
 * <p>The starting point of the laboratory: the server binds a TCP port, accepts exactly one
 * connection, reads one HTTP request, writes one small HTML response and stops. It exists to make
 * the protocol exchange visible before any feature is added.</p>
 */
public class HttpServer {

    private final int port;

    public HttpServer(int port) {
        this.port = port;
    }

    /** Accepts a single connection, echoes the request to the console and answers with HTML. */
    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Listening on port " + port + " (single connection)");

            try (Socket clientSocket = serverSocket.accept();
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {

                String requestLine = in.readLine();
                System.out.println("Request line: " + requestLine);

                String headerLine;
                while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                    System.out.println("Header: " + headerLine);
                }

                String body = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                        + "<title>Minimal HTTP server</title></head>"
                        + "<body><h1>Minimal HTTP server</h1>"
                        + "<p>One connection, one request, one response.</p></body></html>";

                out.print("HTTP/1.1 200 OK\r\n");
                out.print("Content-Type: text/html; charset=UTF-8\r\n");
                out.print("Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n");
                out.print("Connection: close\r\n");
                out.print("\r\n");
                out.print(body);
                out.flush();
            }
        }
    }
}
