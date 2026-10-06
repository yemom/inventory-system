package com.inventory.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceReadinessVerifierTest {

    @Test
    @DisplayName("credentials in a JDBC URL are masked")
    void jdbcUrlCredentialsAreMasked() {
        String sanitized = DataSourceReadinessVerifier.sanitizeJdbcUrl(
                "jdbc:postgresql://stockflow:sup3rs3cret@db.internal:5432/stockflow_db");

        assertEquals("jdbc:postgresql://***@db.internal:5432/stockflow_db", sanitized);
        assertFalse(sanitized.contains("sup3rs3cret"));
        assertFalse(sanitized.contains("stockflow:"));
    }

    @Test
    @DisplayName("credentials in a URI-shaped connection string are masked")
    void uriCredentialsAreMasked() {
        String sanitized = DataSourceReadinessVerifier.sanitizeJdbcUrl(
                "postgresql://stockflow:sup3rs3cret@db.internal:5432/stockflow_db?sslmode=require");

        assertEquals("postgresql://***@db.internal:5432/stockflow_db?sslmode=require", sanitized);
        assertFalse(sanitized.contains("sup3rs3cret"));
    }

    @Test
    @DisplayName("a URL with no authority credentials is passed through unchanged")
    void urlWithoutCredentialsIsUnchanged() {
        assertEquals("jdbc:postgresql://db:5432/stockflow_db",
                DataSourceReadinessVerifier.sanitizeJdbcUrl("jdbc:postgresql://db:5432/stockflow_db"));
        assertEquals("jdbc:tc:postgresql:15-alpine:///inventory_test_db",
                DataSourceReadinessVerifier.sanitizeJdbcUrl("jdbc:tc:postgresql:15-alpine:///inventory_test_db"));
    }

    @Test
    @DisplayName("an authority-only URL still has its credentials masked")
    void authorityOnlyUrlIsMasked() {
        assertEquals("jdbc:postgresql://***@db.internal:5432",
                DataSourceReadinessVerifier.sanitizeJdbcUrl("jdbc:postgresql://u:p@db.internal:5432"));
    }

    @Test
    @DisplayName("a missing URL is reported rather than silently blank")
    void missingUrlIsReported() {
        assertEquals("(not configured)", DataSourceReadinessVerifier.sanitizeJdbcUrl(null));
        assertEquals("(not configured)", DataSourceReadinessVerifier.sanitizeJdbcUrl("   "));
    }

    @Test
    @DisplayName("sanitising never throws on odd input")
    void sanitisingIsTotal() {
        assertEquals("://", DataSourceReadinessVerifier.sanitizeJdbcUrl("://"));
        assertTrue(DataSourceReadinessVerifier.sanitizeJdbcUrl("jdbc:postgresql://").startsWith("jdbc:postgresql://"));
    }
}