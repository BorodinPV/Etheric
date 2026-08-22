package com.etheric.endpoint;

import com.etheric.model.ClientRegistrationRequest;
import com.etheric.model.ClientUpdateRequest;
import com.etheric.service.AdminClientService;
import com.etheric.service.AdminServiceResult;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

/**
 * Admin API for OAuth client registration ({@code /admin/clients}).
 * <p>
 * Requires {@code X-Admin-Api-Key} header (enforced by {@link com.etheric.security.AdminAuthFilter}).
 */
@Path("/admin/clients")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AdminClientsEndpoint {

    private final AdminClientService adminClientService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> register(ClientRegistrationRequest request) {
        return adminClientService.register(request).map(AdminServiceResult::toCreatedResponse);
    }

    @GET
    public Uni<Response> list() {
        return adminClientService.list().map(list -> Response.ok(list).build());
    }

    @GET
    @Path("/{clientId}")
    public Uni<Response> get(@PathParam("clientId") String clientId) {
        return adminClientService.get(clientId).map(AdminServiceResult::toResponse);
    }

    @PUT
    @Path("/{clientId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> update(@PathParam("clientId") String clientId, ClientUpdateRequest request) {
        return adminClientService.update(clientId, request).map(AdminServiceResult::toResponse);
    }

    @PUT
    @Path("/{clientId}/secret")
    public Uni<Response> regenerateSecret(@PathParam("clientId") String clientId) {
        return adminClientService.regenerateSecret(clientId).map(AdminServiceResult::toResponse);
    }
}
