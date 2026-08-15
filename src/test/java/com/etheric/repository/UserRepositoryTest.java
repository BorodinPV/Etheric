package com.etheric.repository;

import com.etheric.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.etheric.testsupport.TestSupport.await;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserRepositoryTest {

    @Inject
    UserRepository userRepository;

    @Test
    void findByUsername_existingUser() {
        var user = await(() -> userRepository.findByUsername("user"));
        assertTrue(user.isPresent());
        assertEquals("user", user.get().username);
    }

    @Test
    void findByUsername_nonExistingUser() {
        assertFalse(await(() -> userRepository.findByUsername("nonexistent")).isPresent());
    }

    @Test
    void findUserById_existingUser() {
        var user = await(() -> userRepository.findByUsername("user"));
        assertTrue(user.isPresent());
        var byId = await(() -> userRepository.findUserById(user.get().id));
        assertTrue(byId.isPresent());
        assertEquals("user", byId.get().username);
    }

    @Test
    void findUserById_nonExistingId() {
        assertFalse(await(() -> userRepository.findUserById(UUID.randomUUID())).isPresent());
    }

    @Test
    void authenticate_validCredentials() {
        var user = await(() -> userRepository.authenticate("user", "password"));
        assertTrue(user.isPresent());
        assertTrue(user.get().enabled);
    }

    @Test
    void authenticate_invalidPassword() {
        assertTrue(await(() -> userRepository.authenticate("user", "wrongpassword")).isEmpty());
    }

    @Test
    void authenticate_nonExistingUser() {
        assertTrue(await(() -> userRepository.authenticate("nonexistent", "password")).isEmpty());
    }

    @Test
    void updateUser() {
        User user = await(() -> userRepository.findByUsername("user")).get();
        String originalEmail = user.email;
        user.email = "updated@example.com";
        await(() -> userRepository.updateUser(user));
        User updated = await(() -> userRepository.findByUsername("user")).get();
        assertEquals("updated@example.com", updated.email);
        user.email = originalEmail;
        await(() -> userRepository.updateUser(user));
    }
}
