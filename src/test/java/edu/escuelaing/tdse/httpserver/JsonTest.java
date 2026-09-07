package edu.escuelaing.tdse.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Unit tests of the JSON writer, in particular of the escaping of untrusted values. */
class JsonTest {

    @Test
    @DisplayName("Members keep the order in which they were added")
    void writesAnObject() {
        String json = Json.object()
                .put("service", "square")
                .put("input", 3.0)
                .put("square", 9.0)
                .build();

        assertEquals("{\"service\":\"square\",\"input\":3,\"square\":9}", json);
    }

    @Test
    @DisplayName("Whole numbers are written without an artificial decimal part")
    void formatsNumbers() {
        assertEquals("7", Json.number(7.0));
        assertEquals("-2", Json.number(-2.0));
        assertEquals("6.25", Json.number(6.25));
    }

    @Test
    @DisplayName("Quotes and backslashes cannot break out of a JSON string")
    void escapesQuotesAndBackslashes() {
        String json = Json.object().put("name", "a\"b\\c").build();

        assertEquals("{\"name\":\"a\\\"b\\\\c\"}", json);
    }

    @Test
    @DisplayName("Control characters are written as escape sequences")
    void escapesControlCharacters() {
        String json = Json.object().put("name", "line\nbreak\ttab").build();

        assertEquals("{\"name\":\"line\\nbreak\\ttab\"}", json);
        assertFalse(json.contains("\n"));
    }

    @Test
    @DisplayName("Markup characters are escaped so a value can never become active content")
    void escapesMarkupCharacters() {
        String json = Json.object().put("name", "<script>alert(1)</script>").build();

        assertFalse(json.contains("<"));
        assertFalse(json.contains(">"));
        assertEquals("{\"name\":\"\\u003cscript\\u003ealert(1)\\u003c/script\\u003e\"}", json);
    }

    @Test
    @DisplayName("An error document has a single error member")
    void writesAnErrorDocument() {
        assertEquals("{\"error\":\"missing value\"}", Json.error("missing value"));
    }
}
