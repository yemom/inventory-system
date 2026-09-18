package com.inventory.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

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
}