package com.etheric.service;

import com.etheric.entity.User;
import com.etheric.model.PasswordChangeRequest;
import com.etheric.model.UserCreateRequest;
import com.etheric.model.UserResponse;
import com.etheric.model.UserUpdateRequest;
import com.etheric.repository.UserRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AdminUserService {

    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final List<String> DEFAULT_ROLES = List.of("user");

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    public Uni<AdminServiceResult<UserResponse>> create(UserCreateRequest request) {
        if (request == null
                || request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "username and password are required"));
        }
        if (request.getPassword().length() < MIN_PASSWORD_LENGTH) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
        }

        String username = request.getUsername().trim();
        return userRepository.usernameExists(username).flatMap(exists -> {
            if (exists) {
                return Uni.createFrom().item(AdminServiceResult.conflict(
                        "conflict", "username already exists"));
            }

            List<String> roles = (request.getRoles() == null || request.getRoles().isEmpty())
                    ? DEFAULT_ROLES : List.copyOf(request.getRoles());
            boolean enabled = request.getEnabled() == null || request.getEnabled();

            User user = new User(
                    UUID.randomUUID(),
                    username,
                    passwordService.hashPassword(request.getPassword()),
                    request.getEmail(),
                    roles,
                    enabled,
                    OffsetDateTime.now());

            return userRepository.persistUser(user)
                    .map(saved -> AdminServiceResult.ok(toResponse(saved)));
        });
    }

    public Uni<List<UserResponse>> list() {
        return userRepository.findAllUsers()
                .map(users -> users.stream().map(AdminUserService::toResponse).toList());
    }

    public Uni<AdminServiceResult<UserResponse>> get(UUID userId) {
        return userRepository.findUserById(userId)
                .map(opt -> opt.map(user -> AdminServiceResult.ok(toResponse(user)))
                        .orElseGet(() -> AdminServiceResult.notFound("not_found", "User not found")));
    }

    public Uni<AdminServiceResult<UserResponse>> update(UUID userId, UserUpdateRequest request) {
        if (request == null) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "request body is required"));
        }
        if (request.getEmail() == null && request.getRoles() == null && request.getEnabled() == null) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "at least one field must be provided"));
        }

        return userRepository.findUserById(userId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(AdminServiceResult.notFound("not_found", "User not found"));
            }
            User user = opt.get();
            if (request.getEmail() != null) {
                user.email = request.getEmail().isBlank() ? null : request.getEmail().trim();
            }
            if (request.getRoles() != null) {
                if (request.getRoles().isEmpty()) {
                    return Uni.createFrom().item(AdminServiceResult.badRequest(
                            "invalid_request", "roles must not be empty"));
                }
                user.roles = List.copyOf(request.getRoles());
            }
            if (request.getEnabled() != null) {
                user.enabled = request.getEnabled();
            }
            return userRepository.updateUser(user)
                    .flatMap(ignored -> userRepository.findUserById(userId))
                    .map(updated -> AdminServiceResult.ok(toResponse(updated.orElseThrow())));
        });
    }

    public Uni<AdminServiceResult<Void>> changePassword(UUID userId, PasswordChangeRequest request) {
        if (request == null || request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "new_password is required"));
        }
        if (request.getNewPassword().length() < MIN_PASSWORD_LENGTH) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "new_password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
        }

        return userRepository.findUserById(userId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(AdminServiceResult.notFound("not_found", "User not found"));
            }
            User user = opt.get();
            user.passwordHash = passwordService.hashPassword(request.getNewPassword());
            return userRepository.updateUser(user)
                    .replaceWith(AdminServiceResult.<Void>ok(null));
        });
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.id, user.username, user.email, user.roles, user.enabled, user.createdAt);
    }
}
