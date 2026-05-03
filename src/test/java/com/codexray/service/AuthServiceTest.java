package com.codexray.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Create AuthService with null dependencies - we're only testing password methods
        authService = new AuthService(null, null, null, null);
    }

    @Test
    void hashPassword_shouldReturnSaltAndHash() {
        String hashed = authService.hashPassword("myPassword123");
        assertNotNull(hashed);
        assertTrue(hashed.contains(":"));
        String[] parts = hashed.split(":");
        assertEquals(2, parts.length);
        assertFalse(parts[0].isEmpty());
        assertFalse(parts[1].isEmpty());
    }

    @Test
    void hashPassword_shouldGenerateDifferentHashes() {
        String hash1 = authService.hashPassword("samePassword");
        String hash2 = authService.hashPassword("samePassword");
        assertNotEquals(hash1, hash2, "Each hash should use a different salt");
    }

    @Test
    void verifyPassword_shouldReturnTrueForCorrectPassword() {
        String hashed = authService.hashPassword("correctPassword");
        assertTrue(authService.verifyPassword("correctPassword", hashed));
    }

    @Test
    void verifyPassword_shouldReturnFalseForWrongPassword() {
        String hashed = authService.hashPassword("correctPassword");
        assertFalse(authService.verifyPassword("wrongPassword", hashed));
    }

    @Test
    void verifyPassword_shouldReturnFalseForMalformedStoredHash() {
        assertFalse(authService.verifyPassword("password", "invalid-format"));
        assertFalse(authService.verifyPassword("password", ""));
        assertFalse(authService.verifyPassword("password", "onlyonepart"));
    }

    @Test
    void verifyPassword_shouldHandleEmptyPassword() {
        String hashed = authService.hashPassword("");
        assertTrue(authService.verifyPassword("", hashed));
        assertFalse(authService.verifyPassword("notempty", hashed));
    }

    @Test
    void verifyPassword_shouldHandleSpecialCharacters() {
        String password = "p@ss!w0rd#$%^&*()中文";
        String hashed = authService.hashPassword(password);
        assertTrue(authService.verifyPassword(password, hashed));
    }

    @Test
    void hashPassword_shouldHandleLongPassword() {
        String longPassword = "a".repeat(1000);
        String hashed = authService.hashPassword(longPassword);
        assertTrue(authService.verifyPassword(longPassword, hashed));
    }
}
