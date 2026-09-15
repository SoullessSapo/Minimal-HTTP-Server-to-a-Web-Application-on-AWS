package co.edu.escuelaing.webframework;

/** Raised when the bytes received from a client cannot be read as an HTTP request. */
public class MalformedRequestException extends Exception {

    public MalformedRequestException(String message) {
        super(message);
    }

    public MalformedRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
