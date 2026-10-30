package com.offbyone.config;

import com.offbyone.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentTest {
    @Test void renderRejectsDevelopmentStorage() {
        var env = new MockEnvironment().withProperty("RENDER", "true")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db/postgres");
        assertDoesNotThrow(() -> new RenderConfiguration(env));
        env.setActiveProfiles("preview");
        assertThrows(IllegalStateException.class, () -> new RenderConfiguration(env));
        env.setActiveProfiles("local");
        assertThrows(IllegalStateException.class, () -> new RenderConfiguration(env));
        env.setActiveProfiles();
        env.setProperty("spring.datasource.url", "jdbc:h2:file:/tmp/preview");
        assertThrows(IllegalStateException.class, () -> new RenderConfiguration(env));
    }

    @Test void signingSecretSurvivesApplicationRecreation() {
        var first = jwt("deployment-secret-at-least-thirty-two-bytes");
        var second = jwt("deployment-secret-at-least-thirty-two-bytes");
        String token = first.generate("player@test");
        assertTrue(second.isValid(token));
        assertEquals("player@test", second.extractEmail(token));
        assertThrows(IllegalStateException.class, () -> jwt("too-short"));
    }

    private JwtUtil jwt(String secret) {
        var jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", secret);
        ReflectionTestUtils.setField(jwt, "expiryMs", 86400000L);
        jwt.validateConfiguration();
        return jwt;
    }

    @Test void readinessChecksDatabaseAndClosesConnection() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true, false);
        var controller = new HealthController(source);
        assertEquals(200, controller.health().getStatusCode().value());
        assertEquals(503, controller.health().getStatusCode().value());
        verify(connection, times(2)).close();
        when(source.getConnection()).thenThrow(new SQLException("private connection details"));
        var failed = controller.health();
        assertEquals(503, failed.getStatusCode().value());
        assertEquals("unavailable", failed.getBody().get("status"));
        assertFalse(failed.getBody().toString().contains("private"));
    }
}
