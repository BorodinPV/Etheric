package com.etheric.repository;

import com.etheric.entity.User;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class UserRepository {

    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final Map<UUID, User> usersById = new ConcurrentHashMap<>();

    public UserRepository() {
        initTestData();
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(usersById.get(id));
    }

    public boolean isValidCredentials(String username, String passwordHash) {
        return usersByUsername.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .filter(User::isEnabled)
                .anyMatch(u -> u.getPasswordHash().equals(passwordHash));
    }

    public boolean isUserEnabled(String username) {
        return usersByUsername.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .anyMatch(User::isEnabled);
    }

    public User authenticate(String username, String password) {
        var userOpt = findByUsername(username);
        if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
            return null;
        }
        // For testing: direct password comparison (not hashed)
        if ("password".equals(password) && "user".equals(username)) {
            return userOpt.get();
        }
        return null;
    }

    public void updateUser(User user) {
        usersByUsername.put(user.getUsername(), user);
        usersById.put(user.getId(), user);
    }

    private void initTestData() {
        User testUser = new User(
                UUID.randomUUID(),
                "user",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                "user@example.com",
                List.of("user"),
                true,
                LocalDateTime.now()
        );
        usersByUsername.put(testUser.getUsername(), testUser);
        usersById.put(testUser.getId(), testUser);
    }
}
