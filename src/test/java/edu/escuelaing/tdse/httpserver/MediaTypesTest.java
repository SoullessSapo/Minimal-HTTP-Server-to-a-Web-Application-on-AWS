package edu.escuelaing.tdse.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the extension to content-type association. */
class MediaTypesTest {

    @Test
    @DisplayName("Each supported extension has the content type the browser expects")
    void mapsSupportedExtensions() {
        assertEquals("text/html; charset=UTF-8", MediaTypes.forFileName("index.html").orElseThrow());
        assertEquals("text/javascript; charset=UTF-8", MediaTypes.forFileName("app.js").orElseThrow());
        assertEquals("text/css; charset=UTF-8", MediaTypes.forFileName("styles.css").orElseThrow());
        assertEquals("image/png", MediaTypes.forFileName("logo.png").orElseThrow());
        assertEquals("image/jpeg", MediaTypes.forFileName("photo.jpg").orElseThrow());
        assertEquals("image/jpeg", MediaTypes.forFileName("photo.JPEG").orElseThrow());
    }

    @Test
    @DisplayName("An unknown or absent extension has no content type")
    void refusesUnknownExtensions() {
        assertTrue(MediaTypes.forFileName("archive.zip").isEmpty());
        assertTrue(MediaTypes.forFileName("README").isEmpty());
        assertTrue(MediaTypes.forFileName("trailing.").isEmpty());
    }

    @Test
    @DisplayName("Text and binary content types are distinguishable")
    void recognisesTextualTypes() {
        assertTrue(MediaTypes.isTextual("text/html; charset=UTF-8"));
        assertTrue(MediaTypes.isTextual("application/json; charset=UTF-8"));
        assertFalse(MediaTypes.isTextual("image/png"));
    }
}
