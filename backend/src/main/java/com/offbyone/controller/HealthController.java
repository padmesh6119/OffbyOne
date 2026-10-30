package com.offbyone.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;

@RestController
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try (var connection = dataSource.getConnection()) {
            if (connection.isValid(2)) return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (java.sql.SQLException ignored) {
            // Keep connection details out of the public readiness response.
        }
        return ResponseEntity.status(503).body(Map.of("status", "unavailable"));
    }
}
