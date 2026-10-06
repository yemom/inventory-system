package com.inventory.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the datasource-URL normalisation that Render (and other managed
 * platforms) depend on.
 *
 * <p>Regression context: a URI-form connection string
 * ({@code postgresql://user:pw@host:5432/db}) was handed straight to the
 * PostgreSQL driver. The driver rejects it, the connection fails, Hibernate ends
 * up with null JDBC metadata, and the deployed service died at boot with
 * "Unable to determine Dialect without JDBC metadata" — a message that points at
 * the JDBC URL setting even though the operator had set one.
 */
class DataSourceUrlNormalizerTest {

    private MockEnvironment environment(String... keyValuePairs) {
        MockEnvironment environment = new MockEnvironment();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            environment.setProperty(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return environment;
    }

    @Test
    @DisplayName("postgres:// URI gains the jdbc: prefix the driver requires")
    void postgresUriBecomesJdbcUrl() {
        var result = DataSourceUrlNormalizer.normalize(
                "postgres://stockflow:pw@dpg-abc123.oregon.postgres.database.azure.com:5432/stockflow_db");

        assertNotNull(result);
        assertEquals(
                "jdbc:postgresql://dpg-abc123.oregon.postgres.database.azure.com:5432/stockflow_db",
                result.jdbcUrl());
        assertEquals("stockflow", result.username());
        assertEquals("pw", result.password());
    }

    @Test
    @DisplayName("postgresql:// URI keeps query parameters such as sslmode")
    void sslmodeIsPreserved() {
        var result = DataSourceUrlNormalizer.normalize(
                "postgresql://stockflow:pw@db.internal:5432/stockflow_db?sslmode=require");

        assertNotNull(result);
        assertEquals("jdbc:postgresql://db.internal:5432/stockflow_db?sslmode=require", result.jdbcUrl());
        assertEquals("stockflow", result.username());
        assertEquals("pw", result.password());
    }

    @Test
    @DisplayName("an already-correct JDBC URL is returned unchanged with no credentials")
    void jdbcUrlIsIdempotent() {
        var result = DataSourceUrlNormalizer.normalize(
                "jdbc:postgresql://db:5432/stockflow_db");

        assertNotNull(result);
        assertEquals("jdbc:postgresql://db:5432/stockflow_db", result.jdbcUrl());
        assertNull(result.username());
        assertNull(result.password());
    }

    @Test
    @DisplayName("percent-encoded credentials are decoded")
    void percentEncodedCredentialsAreDecoded() {
        var result = DataSourceUrlNormalizer.normalize(
                "postgresql://stock%40flow:P%40ssw0rd%2F1@db:5432/stockflow_db");

        assertNotNull(result);
        assertEquals("jdbc:postgresql://db:5432/stockflow_db", result.jdbcUrl());
        assertEquals("stock@flow", result.username());
        assertEquals("P@ssw0rd/1", result.password());
    }

    @Test
    @DisplayName("a literal plus in the password is not turned into a space")
    void plusInPasswordSurvivesDecoding() {
        var result = DataSourceUrlNormalizer.normalize(
                "postgresql://stockflow:a+b%3Dc@db:5432/stockflow_db");

        assertNotNull(result);
        assertEquals("a+b=c", result.password());
    }

    @Test
    @DisplayName("a URL with no port omits the port")
    void portIsOptional() {
        var result = DataSourceUrlNormalizer.normalize("postgresql://stockflow:pw@db/stockflow_db");

        assertNotNull(result);
        assertEquals("jdbc:postgresql://db/stockflow_db", result.jdbcUrl());
    }

    @Test
    @DisplayName("the Testcontainers JDBC URL used by the test profile is left alone")
    void testcontainersUrlIsNotRewritten() {
        assertNull(DataSourceUrlNormalizer.normalize("jdbc:tc:postgresql:15-alpine:///inventory_test_db"));
    }

    @Test
    @DisplayName("an unrecognised URL shape is left alone rather than guessed at")
    void unknownUrlIsNotRewritten() {
        assertNull(DataSourceUrlNormalizer.normalize("mysql://user:pw@host:3306/db"));
        assertNull(DataSourceUrlNormalizer.normalize("not a url at all"));
    }

    @Test
    @DisplayName("a URL with no host cannot be parsed and is left alone")
    void hostlessUrlIsNotRewritten() {
        assertNull(DataSourceUrlNormalizer.normalize("postgresql:///stockflow_db"));
    }

    @Test
    @DisplayName("a PaaS DATABASE_URL rewrites url/username/password")
    void environmentIsRewrittenForPaaSUri() {
        MockEnvironment environment = environment(
                "spring.datasource.url",
                "postgresql://stockflow:pw@db.internal:5432/stockflow_db?sslmode=require");

        DataSourceUrlNormalizer.apply(environment);

        assertEquals("jdbc:postgresql://db.internal:5432/stockflow_db?sslmode=require",
                environment.getProperty("spring.datasource.url"));
        assertEquals("stockflow", environment.getProperty("spring.datasource.username"));
        assertEquals("pw", environment.getProperty("spring.datasource.password"));
    }

    @Test
    @DisplayName("URL credentials win over a mismatched configured username")
    void urlCredentialsTakePrecedenceOverMismatchedConfig() {
        MockEnvironment environment = environment(
                "spring.datasource.url", "postgresql://stockflow:P%40ssw0rd%2F1@db:5432/stockflow_db",
                "spring.datasource.username", "also-wrong",
                "spring.datasource.password", "deliberately-wrong");

        DataSourceUrlNormalizer.apply(environment);

        assertEquals("stockflow", environment.getProperty("spring.datasource.username"));
        assertEquals("P@ssw0rd/1", environment.getProperty("spring.datasource.password"));
        assertEquals("jdbc:postgresql://db:5432/stockflow_db",
                environment.getProperty("spring.datasource.url"));
    }

    @Test
    @DisplayName("a working Docker Compose URL is left completely untouched")
    void environmentIsUntouchedForValidJdbcUrl() {
        MockEnvironment environment = environment(
                "spring.datasource.url", "jdbc:postgresql://db:5432/stockflow_db",
                "spring.datasource.username", "stockflow",
                "spring.datasource.password", "stockflowpassword");

        DataSourceUrlNormalizer.apply(environment);

        assertEquals("jdbc:postgresql://db:5432/stockflow_db", environment.getProperty("spring.datasource.url"));
        assertEquals("stockflow", environment.getProperty("spring.datasource.username"));
        assertEquals("stockflowpassword", environment.getProperty("spring.datasource.password"));
        assertNull(environment.getPropertySources().get(DataSourceUrlNormalizer.PROPERTY_SOURCE_NAME));
    }

    @Test
    @DisplayName("a missing URL is a no-op")
    void environmentWithoutUrlIsANoOp() {
        MockEnvironment environment = environment("spring.datasource.username", "stockflow");

        DataSourceUrlNormalizer.apply(environment);

        assertNull(environment.getPropertySources().get(DataSourceUrlNormalizer.PROPERTY_SOURCE_NAME));
    }

    @Test
    @DisplayName("applying twice is idempotent, so a double registration cannot corrupt the URL")
    void applyIsIdempotent() {
        MockEnvironment environment = environment(
                "spring.datasource.url", "postgresql://stockflow:pw@db:5432/stockflow_db");

        DataSourceUrlNormalizer.apply(environment);
        String afterFirst = environment.getProperty("spring.datasource.url");
        DataSourceUrlNormalizer.apply(environment);

        assertEquals(afterFirst, environment.getProperty("spring.datasource.url"));
        assertEquals("jdbc:postgresql://db:5432/stockflow_db", afterFirst);
        assertEquals("pw", environment.getProperty("spring.datasource.password"));
    }

    @Test
    @DisplayName("the original URI value is never logged back out")
    void rewrittenUrlContainsNoCredentials() {
        MockEnvironment environment = environment(
                "spring.datasource.url", "postgresql://stockflow:sup3rs3cret@db:5432/stockflow_db");

        DataSourceUrlNormalizer.apply(environment);

        String url = environment.getProperty("spring.datasource.url");
        assertFalse(url.contains("sup3rs3cret"), "credentials must not survive in the URL: " + url);
    }
}