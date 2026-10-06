package com.inventory.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String TEST_SECRET =
            "94f6c46cb32145b0a7019f3900350d32b845187740e5dcbd41369d71c897f262";
    private static final long TEST_EXPIRATION = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", TEST_EXPIRATION);
    }

    @Test
    @DisplayName("Generate token - extracts valid subject and expiration")
    void generateToken_Success() {
        UserDetails userDetails = new User("admin@stockflow.local", "password", Collections.emptyList());

        String token = jwtUtil.generateToken(userDetails);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin@stockflow.local");
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());
    }

    @Test
    @DisplayName("Validate token - returns true for matching user")
    void validateToken_ValidUser() {
        UserDetails userDetails = new User("staff@stockflow.local", "password", Collections.emptyList());
        String token = jwtUtil.generateToken(userDetails);

        Boolean isValid = jwtUtil.validateToken(token, userDetails);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Validate token - returns false for mismatched user")
    void validateToken_MismatchedUser() {
        UserDetails user1 = new User("user1@stockflow.local", "password", Collections.emptyList());
        UserDetails user2 = new User("user2@stockflow.local", "password", Collections.emptyList());

        String token = jwtUtil.generateToken(user1);
        Boolean isValid = jwtUtil.validateToken(token, user2);

        assertThat(isValid).isFalse();
    }

    // ── Regression coverage for the signing-key derivation ──────────────────
    // The key used to be derived with Decoders.BASE64.decode(secret), which
    // throws io.jsonwebtoken.io.DecodingException for any secret that is not
    // base64. The compose/CI default "change-this-jwt-secret" contains '-', so
    // token issuing blew up and every login returned HTTP 500 on a
    // default-configured deployment.

    @ParameterizedTest(name = "secret \"{0}\"")
    @ValueSource(strings = {
            "change-this-jwt-secret",                                  // not base64: contains '-'
            "not_base64_at_all",                                       // not base64: contains '_'
            "a-very-long-passphrase-that-is-definitely-not-base64-encoded", // longer than 32 chars
            "short",                                                   // below HS256's 32-byte minimum
            "with spaces and $ymbols !@#",                             // illegal base64 characters
    })
    @DisplayName("Token round-trips regardless of the secret's shape")
    void tokenRoundTripsForNonBase64Secrets(String secret) {
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        UserDetails userDetails = new User("admin@stockflow.local", "password", Collections.emptyList());

        String token = jwtUtil.generateToken(userDetails);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin@stockflow.local");
        assertThat(jwtUtil.validateToken(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("A base64 secret and a raw secret produce different, self-consistent keys")
    void keyDerivationIsDeterministicPerSecret() {
        UserDetails userDetails = new User("admin@stockflow.local", "password", Collections.emptyList());

        ReflectionTestUtils.setField(jwtUtil, "secret", "plain-passphrase-secret-value-12345");
        String fromPlain = jwtUtil.generateToken(userDetails);

        ReflectionTestUtils.setField(jwtUtil, "secret",
                "94f6c46cb32145b0a7019f3900350d32b845187740e5dcbd41369d71c897f262");
        String fromBase64 = jwtUtil.generateToken(userDetails);

        // Both verify with their own key...
        ReflectionTestUtils.setField(jwtUtil, "secret", "plain-passphrase-secret-value-12345");
        assertThat(jwtUtil.validateToken(fromPlain, userDetails)).isTrue();
        // ...and a token signed with one is rejected under the other, i.e. the
        // derivation is genuinely key-dependent and not a constant.
        assertThat(jwtUtil.validateToken(fromBase64, userDetails)).isFalse();
    }

    @Test
    @DisplayName("A missing secret fails loudly rather than signing with an empty key")
    void missingSecretIsRejected() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "   ");
        UserDetails userDetails = new User("admin@stockflow.local", "password", Collections.emptyList());

        assertThatThrownBy(() -> jwtUtil.generateToken(userDetails))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }
}