package com.inventory.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Verifies that {@code token} is a genuine, unexpired token for
     * {@code userDetails}.
     *
     * <p>Returns {@code false} rather than propagating a parsing failure. A
     * tampered or foreign-signed token is a routine event (a stale token after
     * a key rotation, a client sending garbage), and it must be an ordinary
     * "not authenticated" outcome. Letting {@code SignatureException} /
     * {@code ExpiredJwtException} escape a method that answers a yes/no question
     * invites the next caller to turn an invalid token into a 500. The
     * signature is still genuinely verified — jjwt rejects a mismatch before
     * this method can return {@code true}.
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(decodeSecret(secret));
    }

    /**
     * Derive the raw HMAC key bytes from the configured JWT secret.
     *
     * The legacy behaviour did {@code Decoders.BASE64.decode(secret)}, which
     * throws {@code io.jsonwebtoken.io.DecodingException} for any secret that is
     * not base64 (CI's default {@code change-this-jwt-secret} contains '-'),
     * turning token issuing/validating into a 500 on protected requests.
     *
     * Now: try base64 first (keeps compatibility for base64 secrets and yields
     * >= 32 bytes), otherwise fall back to the raw UTF-8 bytes of the string,
     * and hash with SHA-256 anything still too short for HS256 (32 bytes+).
     */
    private byte[] decodeSecret(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalStateException("jwt.secret is not configured");
        }
        try {
            byte[] decoded = Decoders.BASE64.decode(s);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (Exception ignored) {
            // fall through to raw-byte handling
        }
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive JWT signing key", e);
        }
    }
}
