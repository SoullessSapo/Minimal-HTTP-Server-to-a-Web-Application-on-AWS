package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the API the application uses and of the externalised port. */
class WebFrameworkTest {

    @AfterEach
    void tearDown() {
        WebFramework.reset();
    }

    @Test
    @DisplayName("A service registered through the facade lands in the router")
    void registersRoutes() {
        WebFramework.get("/hello", (req, resp) -> "Hello world");
        WebFramework.get("/pi", (req, resp) -> String.valueOf(Math.PI));

        assertEquals(2, WebFramework.router().size());
        assertTrue(WebFramework.router().find("GET", "/hello").isPresent());
        assertTrue(WebFramework.router().find("GET", "/pi").isPresent());
    }

    @Test
    @DisplayName("Without a PORT variable the documented default is used")
    void defaultsToPort8080() {
        assertEquals(8080, WebFramework.DEFAULT_PORT);
        assertEquals(8080, WebFramework.resolvePort(null));
        assertEquals(8080, WebFramework.resolvePort(""));
        assertEquals(8080, WebFramework.resolvePort("   "));
    }

    @Test
    @DisplayName("The port comes from the environment, which is what a cloud host imposes")
    void readsThePortFromTheEnvironment() {
        assertEquals(80, WebFramework.resolvePort("80"));
        assertEquals(5000, WebFramework.resolvePort(" 5000 "));
    }

    @Test
    @DisplayName("An unusable PORT value falls back to the default instead of refusing to start")
    void fallsBackOnAnInvalidPort() {
        assertEquals(8080, WebFramework.resolvePort("not-a-number"));
        assertEquals(8080, WebFramework.resolvePort("70000"));
        assertEquals(8080, WebFramework.resolvePort("-1"));
    }

    @Test
    @DisplayName("The static folder can be reconfigured by the application")
    void configuresTheStaticFolder() {
        WebFramework.staticfiles("/webroot");

        assertTrue(new StaticFileService("/webroot").find("/index.html").isPresent());
    }
}
