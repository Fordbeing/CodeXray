package com.codexray.common;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret-key-for-unit-testing-at-least-256-bits-long!", 86400000);
    }

    @Test
    void generateToken_shouldCreateValidToken() {
        String token = jwtUtil.generateToken(1L, "testuser");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void parseToken_shouldReturnCorrectClaims() {
        String token = jwtUtil.generateToken(42L, "alice");

        Claims claims = jwtUtil.parseToken(token);
        assertEquals("42", claims.getSubject());
        assertEquals("alice", claims.get("username"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void getUserId_shouldReturnUserId() {
        String token = jwtUtil.generateToken(100L, "bob");
        assertEquals(100L, jwtUtil.getUserId(token));
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        String token = jwtUtil.generateToken(1L, "user");
        assertTrue(jwtUtil.isTokenValid(token));
    }

    @Test
    void isTokenValid_shouldReturnFalseForInvalidToken() {
        assertFalse(jwtUtil.isTokenValid("invalid.token.here"));
    }

    @Test
    void isTokenValid_shouldReturnFalseForNull() {
        assertFalse(jwtUtil.isTokenValid(null));
    }

    @Test
    void isTokenValid_shouldReturnFalseForEmptyString() {
        assertFalse(jwtUtil.isTokenValid(""));
    }

    @Test
    void isTokenValid_shouldReturnFalseForTamperedToken() {
        String token = jwtUtil.generateToken(1L, "user");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtUtil.isTokenValid(tampered));
    }

    @Test
    void constructor_shouldThrowIfSecretIsBlank() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("", 86400000));
        assertThrows(IllegalStateException.class, () -> new JwtUtil(null, 86400000));
    }

    @Test
    void tokensWithDifferentSecretsShouldNotValidateEachOther() {
        JwtUtil otherJwt = new JwtUtil("different-secret-key-for-unit-testing-at-least-256-bits!", 86400000);
        String token = jwtUtil.generateToken(1L, "user");
        assertFalse(otherJwt.isTokenValid(token));
    }
}
