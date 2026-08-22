package com.etheric.endpoint;

import com.etheric.model.PasswordChangeRequest;
import com.etheric.model.UserCreateRequest;
import com.etheric.model.UserUpdateRequest;
import com.etheric.service.AdminServiceResult;
import com.etheric.service.AdminUserService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Admin API for user management ({@code /admin/users}).
 * <p>
 * Requires {@code X-Admin-Api-Key} header.
 */
@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AdminUsersEndpoint {

    private final AdminUserService adminUserService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> create(UserCreateRequest request) {
        return adminUserService.create(request).map(AdminServiceResult::toCreatedResponse);
    }

    @GET
    public Uni<Response> list() {
        return adminUserService.list().map(list -> Response.ok(list).build());
    }

    @GET
    @Path("/{userId}")
    public Uni<Response> get(@PathParam("userId") UUID userId) {
        return adminUserService.get(userId).map(AdminServiceResult::toResponse);
    }

    @PUT
    @Path("/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> update(@PathParam("userId") UUID userId, UserUpdateRequest request) {
        return adminUserService.update(userId, request).map(AdminServiceResult::toResponse);
    }

    @PUT
    @Path("/{userId}/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> changePassword(@PathParam("userId") UUID userId, PasswordChangeRequest request) {
        return adminUserService.changePassword(userId, request).map(AdminServiceResult::toNoContentResponse);
    }
}
