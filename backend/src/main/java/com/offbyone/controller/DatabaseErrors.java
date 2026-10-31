package com.offbyone.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
@RestControllerAdvice
public class DatabaseErrors {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseErrors.class);
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict(DataIntegrityViolationException e) {
        log.error("Database constraint rejected a request", e);
        return ResponseEntity.status(409).body(Map.of("message", "This change could not be saved. If the guest name was just taken, choose another. Otherwise refresh and retry."));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> unavailable(DataAccessException e) {
        log.error("Database request failed", e);
        return ResponseEntity.status(503).body(Map.of("message", "The arena could not save your request. Please try again shortly."));
    }
}
