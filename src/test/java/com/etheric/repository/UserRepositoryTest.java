package com.etheric.repository;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserRepositoryTest {

    @Inject
    UserRepository userRepository;

    @Test
    void findByUsername_existingUser() {
        var user = userRepository.findByUsername("user");
        assertTrue(user.isPresent());
        assertEquals("user", user.get().getUsername());
        assertEquals("user@example.com", user.get().getEmail());
    }

    @Test
    void findByUsername_nonExistingUser() {
        assertFalse(userRepository.findByUsername("nonexistent").isPresent());
    }

    @Test
    void findById_existingUser() {
        var user = userRepository.findByUsername("user");
        assertTrue(user.isPresent());

        var byId = userRepository.findById(user.get().getId());
        assertTrue(byId.isPresent());
        assertEquals("user", byId.get().getUsername());
    }

    @Test
    void findById_nonExistingId() {
        assertFalse(userRepository.findById(UUID.randomUUID()).isPresent());
    }

    @Test
    void isValidCredentials_validPassword() {
        // The test user password hash matches "password" via hardcoded check
        assertTrue(userRepository.isValidCredentials("user", "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"));
    }

    @Test
    void isValidCredentials_wrongHash() {
        assertFalse(userRepository.isValidCredentials("user", "wrong-hash"));
    }

    @Test
    void isValidCredentials_nonExistingUser() {
        assertFalse(userRepository.isValidCredentials("nonexistent", "any"));
    }

    @Test
    void isUserEnabled_existingUser() {
        assertTrue(userRepository.isUserEnabled("user"));
    }

    @Test
    void isUserEnabled_nonExistingUser() {
        assertFalse(userRepository.isUserEnabled("nonexistent"));
    }

    @Test
    void authenticate_validCredentials() {
        var user = userRepository.authenticate("user", "password");
        assertNotNull(user);
        assertEquals("user", user.getUsername());
        assertTrue(user.isEnabled());
    }

    @Test
    void authenticate_wrongPassword() {
        var user = userRepository.authenticate("user", "wrongpassword");
        assertNull(user);
    }

    @Test
    void authenticate_nonExistingUser() {
        var user = userRepository.authenticate("nonexistent", "password");
        assertNull(user);
    }

    @Test
    void authenticate_emptyPassword() {
        var user = userRepository.authenticate("user", "");
        assertNull(user);
    }

    @Test
    void updateUser() {
        var user = userRepository.findByUsername("user").get();
        String originalEmail = user.getEmail();

        user.setEmail("updated@example.com");
        userRepository.updateUser(user);

        var updated = userRepository.findByUsername("user").get();
        assertEquals("updated@example.com", updated.getEmail());

        user.setEmail(originalEmail);
        userRepository.updateUser(user);
    }

    @Test
    void userHasExpectedFields() {
        var user = userRepository.findByUsername("user").get();
        assertNotNull(user.getId());
        assertNotNull(user.getCreatedAt());
        assertEquals("user@example.com", user.getEmail());
        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().contains("user"));
        assertTrue(user.isEnabled());
    }
}
