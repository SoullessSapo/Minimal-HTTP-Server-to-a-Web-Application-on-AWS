package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the lookup table that replaces the conditions of the old server loop. */
class RouterTest {

    private Router router;

    @BeforeEach
    void setUp() {
        router = new Router();
    }

    @Test
    @DisplayName("A registered service is found by its method and path")
    void findsARegisteredRoute() {
        RouteHandler handler = (req, resp) -> "pong";
        router.get("/ping", handler);

        assertEquals(1, router.size());
        assertSame(handler, router.find("GET", "/ping").orElseThrow().getHandler());
    }

    @Test
    @DisplayName("The handler is what produces the answer")
    void executesTheHandler() {
        router.get("/pi", (req, resp) -> String.valueOf(Math.PI));

        RouteHandler handler = router.find("GET", "/pi").orElseThrow().getHandler();

        assertEquals(String.valueOf(Math.PI), handler.handle(null, new Response()));
    }

    @Test
    @DisplayName("Paths are matched after normalising leading and trailing slashes")
    void normalisesPaths() {
        router.get("hello", (req, resp) -> "hi");

        assertTrue(router.find("GET", "/hello").isPresent());
        assertTrue(router.find("GET", "/hello/").isPresent());
        assertTrue(router.find("GET", "hello").isPresent());
    }

    @Test
    @DisplayName("The method is part of the key and is not case sensitive")
    void matchesTheMethod() {
        router.get("/hello", (req, resp) -> "hi");

        assertTrue(router.find("get", "/hello").isPresent());
        assertTrue(router.find("POST", "/hello").isEmpty());
    }

    @Test
    @DisplayName("An unregistered path is not found, which is what sends the request to the files")
    void doesNotFindUnknownPaths() {
        router.get("/hello", (req, resp) -> "hi");

        assertTrue(router.find("GET", "/unknown").isEmpty());
        assertTrue(router.find("GET", "/hello/world").isEmpty());
        assertTrue(router.find(null, null).isEmpty());
    }

    @Test
    @DisplayName("Registering the same path again replaces the handler")
    void replacesAnExistingRoute() {
        router.get("/hello", (req, resp) -> "first");
        router.get("/hello", (req, resp) -> "second");

        assertEquals(1, router.size());
        assertEquals("second",
                router.find("GET", "/hello").orElseThrow().getHandler().handle(null, new Response()));
    }

    @Test
    @DisplayName("Routes keep the order in which the application registered them")
    void keepsRegistrationOrder() {
        router.get("/one", (req, resp) -> "1");
        router.get("/two", (req, resp) -> "2");

        assertEquals("[GET /one, GET /two]", router.getRoutes().toString());
    }
}
