package co.edu.escuelaing.webframework;

/**
 * A static resource that was found: its bytes and the content type that describes them.
 *
 * @param name        relative name inside the static folder, for example {@code images/logo.png}
 * @param contentType value of the {@code Content-Type} header
 * @param content     the file, always as bytes, so text and images travel the same path
 */
public record StaticFile(String name, String contentType, byte[] content) {

    public int size() {
        return content.length;
    }
}
