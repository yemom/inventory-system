package com.inventory.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repository previously shipped a real JWT signing key and a real
 * super-admin password as {@code application.properties} fallbacks. Those were
 * removed; these tests pin the fail-fast behaviour that replaced them, so a
 * future "just add a default back" change fails the build rather than silently
 * reintroducing a publicly known credential.
 */
class StartupConfigValidatorTest {

    private static final String VALID_JWT =
            "94f6c46cb32145b0a7019f3900350d32b845187740e5dcbd41369d71c897f262";
    private static final String VALID_ADMIN_PASSWORD = "Fekerte@zegeye1221";

    private MockEnvironment environment(String jwtSecret, String adminPassword) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("jwt.secret", jwtSecret);
        environment.setProperty("app.super-admin.password", adminPassword);
        return environment;
    }

    @Test
    @DisplayName("properly supplied secrets pass")
    void validSecretsPass() {
        assertDoesNotThrow(() -> new StartupConfigValidator(
                environment(VALID_JWT, VALID_ADMIN_PASSWORD)).validateOnStartup());
    }

    @Test
    @DisplayName("a missing JWT secret stops the boot")
    void missingJwtSecretFails() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new StartupConfigValidator(environment("", VALID_ADMIN_PASSWORD)).validateOnStartup());
        assertTrue(ex.getMessage().contains("JWT_SECRET is not set"), ex.getMessage());
    }

    @Test
    @DisplayName("a JWT secret too short for HS256 stops the boot")
    void shortJwtSecretFails() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new StartupConfigValidator(environment("tooshort", VALID_ADMIN_PASSWORD)).validateOnStartup());
        assertTrue(ex.getMessage().contains("at least 32 characters"), ex.getMessage());
    }

    @Test
    @DisplayName("the development placeholder JWT secret stops the boot")
    void placeholderJwtSecretFails() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new StartupConfigValidator(
                        environment("change-this-jwt-secret", VALID_ADMIN_PASSWORD)).validateOnStartup());
        assertTrue(ex.getMessage().contains("placeholder"), ex.getMessage());
    }

    @Test
    @DisplayName("a missing super-admin password stops the boot")
    void missingAdminPasswordFails() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new StartupConfigValidator(environment(VALID_JWT, "")).validateOnStartup());
        assertTrue(ex.getMessage().contains("SUPER_ADMIN_PASSWORD is not set"), ex.getMessage());
    }

    @Test
    @DisplayName("a short super-admin password stops the boot")
    void shortAdminPasswordFails() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new StartupConfigValidator(environment(VALID_JWT, "short")).validateOnStartup());
        assertTrue(ex.getMessage().contains("at least 12 characters"), ex.getMessage());
    }

    @Test
    @DisplayName("every problem is reported at once, not one restart at a time")
    void allProblemsAreReportedTogether() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new StartupConfigValidator(environment("", "")).validateOnStartup());
        assertTrue(ex.getMessage().contains("JWT_SECRET"), ex.getMessage());
        assertTrue(ex.getMessage().contains("SUPER_ADMIN_PASSWORD"), ex.getMessage());
    }

    @Test
    @DisplayName("the failure message never echoes the secret it is complaining about")
    void failureMessageDoesNotLeakSecrets() {
        String leaky = "leaky-secret-value-that-is-long-enough";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new StartupConfigValidator(environment(leaky, "")).validateOnStartup());
        assertTrue(!ex.getMessage().contains(leaky), "secret value leaked into the error message");
    }
}