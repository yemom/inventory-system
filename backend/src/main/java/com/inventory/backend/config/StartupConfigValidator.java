package com.inventory.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start the application when a credential that must be secret is
 * missing, too weak, or still set to the value that used to be committed in
 * {@code application.properties}.
 *
 * <p>Those repository defaults were removed for a concrete reason: a JWT signing
 * key and an administrator password that exist in version control are not
 * secrets. Anyone who can read the repository can forge a SUPER_ADMIN token or
 * simply log in. Failing fast at startup turns a silent, invisible compromise
 * into one loud, actionable deploy-time error.
 *
 * <p>Nothing here logs or echoes a secret value — only whether it was accepted.
 *
 * <p>Values are expected to be supplied through the environment
 * ({@code JWT_SECRET}, {@code SUPER_ADMIN_PASSWORD}); see {@code .env-example}
 * and {@code render.yaml}.
 */
@Component
public class StartupConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    /** HMAC-SHA256 needs at least 256 bits (32 bytes) of key material. */
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    /** Below this an admin password offers no real protection. */
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final Environment environment;

    public StartupConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateOnStartup() {
        List<String> problems = new ArrayList<>();

        checkJwtSecret(problems);
        checkSuperAdminPassword(problems);

        if (problems.isEmpty()) {
            return;
        }

        throw new IllegalStateException(
                "Refusing to start: required secrets are missing or unsafe." + System.lineSeparator()
                        + "  * " + String.join(System.lineSeparator() + "  * ", problems)
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "Set them in the environment before starting. For local development, copy "
                        + ".env-example to .env. On Render, set them in the service's Environment tab "
                        + "(SUPER_ADMIN_PASSWORD is marked 'sync: false' in render.yaml for this reason)."
                        + System.lineSeparator()
                        + "Never commit a real value to the repository.");
    }

    private void checkJwtSecret(List<String> problems) {
        String jwtSecret = environment.getProperty("jwt.secret");
        if (isUnset(jwtSecret)) {
            problems.add("JWT_SECRET is not set. Generate one with: openssl rand -hex 32");
        } else if (isKnownPlaceholder(jwtSecret)) {
            // Checked before length: naming the placeholder is a far more
            // specific diagnosis than "too short", and both have one fix.
            problems.add("JWT_SECRET is still the development placeholder committed to the repository. "
                    + "Generate a real one with: openssl rand -hex 32");
        } else if (jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            problems.add("JWT_SECRET must be at least " + MIN_JWT_SECRET_LENGTH
                    + " characters for HS256 (currently " + jwtSecret.length() + ").");
        }
    }

    private void checkSuperAdminPassword(List<String> problems) {
        String password = environment.getProperty("app.super-admin.password");
        if (isUnset(password)) {
            problems.add("SUPER_ADMIN_PASSWORD is not set. The bootstrap super-admin account cannot be "
                    + "created safely without it.");
        } else if (isKnownPlaceholder(password)) {
            problems.add("SUPER_ADMIN_PASSWORD is still the development placeholder committed to the "
                    + "repository. Set a unique value before deploying.");
        } else if (password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            problems.add("SUPER_ADMIN_PASSWORD must be at least " + MIN_ADMIN_PASSWORD_LENGTH
                    + " characters (currently " + password.length() + ").");
        }
    }

    private static boolean isUnset(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Catches the values that used to be hardcoded here, so an environment that
     * still copies them from an old {@code .env} fails loudly instead of running
     * with publicly known credentials.
     */
    private static boolean isKnownPlaceholder(String value) {
        String normalised = value.trim().toLowerCase();
        return normalised.equals("change-this-jwt-secret")
                || normalised.equals("change-me")
                || normalised.equals("changeme")
                || normalised.equals("changethisjwtsecret")
                || normalised.equals("admin@stockflow2026!");
    }
}