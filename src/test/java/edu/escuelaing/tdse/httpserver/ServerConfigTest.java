package edu.escuelaing.tdse.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the runtime configuration. */
class ServerConfigTest {

    private static final String[] NO_ARGUMENTS = new String[0];

    @Test
    @DisplayName("Without configuration the documented default port is used")
    void usesTheDefaultPort() {
        ServerConfig config = ServerConfig.from(NO_ARGUMENTS, Map.of());

        assertEquals(ServerConfig.DEFAULT_PORT, config.getPort());
        assertTrue(config.getStaticDirectory().isEmpty());
    }

    @Test
    @DisplayName("The port can come from the environment, as it does in the service unit")
    void readsThePortFromTheEnvironment() {
        ServerConfig config = ServerConfig.from(NO_ARGUMENTS, Map.of("PORT", "8080"));

        assertEquals(8080, config.getPort());
    }

    @Test
    @DisplayName("A command line option overrides the environment")
    void commandLineWins() {
        ServerConfig config = ServerConfig.from(new String[]{"--port", "9000"}, Map.of("PORT", "8080"));

        assertEquals(9000, config.getPort());
    }

    @Test
    @DisplayName("An external directory of public resources can be configured")
    void readsTheStaticDirectory() {
        ServerConfig fromArguments =
                ServerConfig.from(new String[]{"-s", "/opt/app/public"}, Map.of());
        ServerConfig fromEnvironment =
                ServerConfig.from(NO_ARGUMENTS, Map.of("STATIC_DIR", "/srv/public"));

        assertEquals(Path.of("/opt/app/public"), fromArguments.getStaticDirectory().orElseThrow());
        assertEquals(Path.of("/srv/public"), fromEnvironment.getStaticDirectory().orElseThrow());
    }

    @Test
    @DisplayName("An invalid port is refused with a message instead of failing later")
    void refusesInvalidPorts() {
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.from(new String[]{"--port", "not-a-number"}, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.from(new String[]{"--port", "70000"}, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.from(new String[]{"--port"}, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.from(new String[]{"--unknown"}, Map.of()));
    }

    @Test
    @DisplayName("The usage text names both the option and the environment variable")
    void describesItsOwnOptions() {
        assertTrue(ServerConfig.usage().contains("--port"));
        assertTrue(ServerConfig.usage().contains("STATIC_DIR"));
    }
}
