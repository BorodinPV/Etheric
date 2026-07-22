package com.etheric.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PasswordServiceTest {

    @Inject
    PasswordService passwordService;

    @Test
    void hashPassword_returnsNonEmptyString() {
        String hash = passwordService.hashPassword("test123");
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }

    @Test
    void hashPassword_sameInputProducesSameHash() {
        String hash1 = passwordService.hashPassword("mypassword");
        String hash2 = passwordService.hashPassword("mypassword");
        assertEquals(hash1, hash2);
    }

    @Test
    void hashPassword_differentInputsProduceDifferentHashes() {
        String hash1 = passwordService.hashPassword("password1");
        String hash2 = passwordService.hashPassword("password2");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void verifyPassword_validPassword() {
        String hash = passwordService.hashPassword("correcthorse");
        assertTrue(passwordService.verifyPassword("correcthorse", hash));
    }

    @Test
    void verifyPassword_invalidPassword() {
        String hash = passwordService.hashPassword("correcthorse");
        assertFalse(passwordService.verifyPassword("wrongpassword", hash));
    }

    @Test
    void verifyPassword_emptyPassword() {
        String hash = passwordService.hashPassword("");
        assertTrue(passwordService.verifyPassword("", hash));
        assertFalse(passwordService.verifyPassword("notempty", hash));
    }

    @Test
    void verifyPassword_specialCharacters() {
        String hash = passwordService.hashPassword("p@$$w0rd!#%");
        assertTrue(passwordService.verifyPassword("p@$$w0rd!#%", hash));
        assertFalse(passwordService.verifyPassword("p@$$w0rd!#", hash));
    }

    @Test
    void verifyPassword_longPassword() {
        String longPassword = "a".repeat(10000);
        String hash = passwordService.hashPassword(longPassword);
        assertTrue(passwordService.verifyPassword(longPassword, hash));
    }
}
