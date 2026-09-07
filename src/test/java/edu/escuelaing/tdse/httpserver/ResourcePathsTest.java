package edu.escuelaing.tdse.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the path normalisation that protects the public resources area. */
class ResourcePathsTest {

    @Test
    @DisplayName("The root path is the home page")
    void rootIsTheIndexFile() {
        assertEquals("index.html", ResourcePaths.normalize("/").orElseThrow());
    }

    @Test
    @DisplayName("A path that ends with a slash asks for the index of that folder")
    void directoryPathAddsTheIndexFile() {
        assertEquals("images/index.html", ResourcePaths.normalize("/images/").orElseThrow());
    }

    @Test
    @DisplayName("Ordinary paths keep their segments")
    void keepsOrdinaryPaths() {
        assertEquals("images/logo.png", ResourcePaths.normalize("/images/logo.png").orElseThrow());
        assertEquals("app.js", ResourcePaths.normalize("/app.js").orElseThrow());
        assertEquals("images/logo.png", ResourcePaths.normalize("/./images//logo.png").orElseThrow());
    }

    @Test
    @DisplayName("Any attempt to climb out of the public area is refused")
    void refusesPathTraversal() {
        assertTrue(ResourcePaths.normalize("/../pom.xml").isEmpty());
        assertTrue(ResourcePaths.normalize("/images/../../etc/passwd").isEmpty());
        assertTrue(ResourcePaths.normalize("/..").isEmpty());
        // The parser decodes before normalising, so an encoded attempt arrives here already decoded.
        assertTrue(ResourcePaths.normalize("/images/../..").isEmpty());
    }

    @Test
    @DisplayName("Backslashes and null bytes cannot be used to disguise a path")
    void refusesDisguisedSeparators() {
        assertTrue(ResourcePaths.normalize("/..\\..\\pom.xml").isEmpty());
        assertTrue(ResourcePaths.normalize("/index.html\0.png").isEmpty());
    }

    @Test
    @DisplayName("A target that is not a path is refused")
    void refusesNonPaths() {
        assertTrue(ResourcePaths.normalize("").isEmpty());
        assertTrue(ResourcePaths.normalize(null).isEmpty());
        assertTrue(ResourcePaths.normalize("index.html").isEmpty());
    }
}
