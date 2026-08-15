package com.etheric.endpoint;

import com.etheric.entity.User;
import com.etheric.model.PasswordChangeRequest;
import com.etheric.model.UserCreateRequest;
import com.etheric.model.UserResponse;
import com.etheric.model.UserUpdateRequest;
import com.etheric.repository.UserRepository;
import com.etheric.service.PasswordService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin API for user management ({@code /admin/users}).
 * <p>
 * Requires {@code X-Admin-Api-Key} header. Supports create, list, get, update profile fields,
 * and password change. Password hashes are never returned.
 */
@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
public class AdminUsersEndpoint {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final List<String> DEFAULT_ROLES = List.of("user");

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> create(UserCreateRequest request) {
        if (request == null
                || request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return Uni.createFrom().item(badRequest("invalid_request", "username and password are required"));
        }
        if (request.getPassword().length() < MIN_PASSWORD_LENGTH) {
            return Uni.createFrom().item(badRequest("invalid_request",
                    "password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
        }

        String username = request.getUsername().trim();
        return userRepository.usernameExists(username).flatMap(exists -> {
            if (exists) {
                return Uni.createFrom().item(conflict("conflict", "username already exists"));
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
                    .map(saved -> Response.status(Response.Status.CREATED).entity(toResponse(saved)).build());
        });
    }

    @GET
    public Uni<Response> list() {
        return userRepository.findAllUsers()
                .map(users -> users.stream().map(AdminUsersEndpoint::toResponse).toList())
                .map(list -> Response.ok(list).build());
    }

    @GET
    @Path("/{userId}")
    public Uni<Response> get(@PathParam("userId") UUID userId) {
        return userRepository.findUserById(userId)
                .map(opt -> opt.map(user -> Response.ok(toResponse(user)).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                                .entity(error("not_found", "User not found")).build()));
    }

    @PUT
    @Path("/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> update(@PathParam("userId") UUID userId, UserUpdateRequest request) {
        if (request == null) {
            return Uni.createFrom().item(badRequest("invalid_request", "request body is required"));
        }
        if (request.getEmail() == null && request.getRoles() == null && request.getEnabled() == null) {
            return Uni.createFrom().item(badRequest("invalid_request", "at least one field must be provided"));
        }

        return userRepository.findUserById(userId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND)
                        .entity(error("not_found", "User not found")).build());
            }
            User user = opt.get();
            if (request.getEmail() != null) {
                user.email = request.getEmail().isBlank() ? null : request.getEmail().trim();
            }
            if (request.getRoles() != null) {
                if (request.getRoles().isEmpty()) {
                    return Uni.createFrom().item(badRequest("invalid_request", "roles must not be empty"));
                }
                user.roles = List.copyOf(request.getRoles());
            }
            if (request.getEnabled() != null) {
                user.enabled = request.getEnabled();
            }
            return userRepository.updateUser(user)
                    .flatMap(ignored -> userRepository.findUserById(userId))
                    .map(updated -> Response.ok(toResponse(updated.orElseThrow())).build());
        });
    }

    @PUT
    @Path("/{userId}/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> changePassword(@PathParam("userId") UUID userId, PasswordChangeRequest request) {
        if (request == null || request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            return Uni.createFrom().item(badRequest("invalid_request", "new_password is required"));
        }
        if (request.getNewPassword().length() < MIN_PASSWORD_LENGTH) {
            return Uni.createFrom().item(badRequest("invalid_request",
                    "new_password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
        }

        return userRepository.findUserById(userId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND)
                        .entity(error("not_found", "User not found")).build());
            }
            User user = opt.get();
            user.passwordHash = passwordService.hashPassword(request.getNewPassword());
            return userRepository.updateUser(user)
                    .replaceWith(Response.noContent().build());
        });
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.id, user.username, user.email, user.roles, user.enabled, user.createdAt);
    }

    private static Response badRequest(String code, String description) {
        return Response.status(Response.Status.BAD_REQUEST).entity(error(code, description)).build();
    }

    private static Response conflict(String code, String description) {
        return Response.status(Response.Status.CONFLICT).entity(error(code, description)).build();
    }

    private static java.util.Map<String, String> error(String code, String description) {
        return java.util.Map.of("error", code, "error_description", description);
    }
}
