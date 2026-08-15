package com.etheric.endpoint;

import com.etheric.entity.Client;
import com.etheric.model.ClientRegistrationRequest;
import com.etheric.model.ClientRegistrationResponse;
import com.etheric.repository.ClientRepository;
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
 * Admin API for OAuth client registration ({@code /admin/clients}).
 * <p>
 * Requires {@code X-Admin-Api-Key} header (enforced by {@link com.etheric.security.AdminAuthFilter}).
 * {@code POST}: JSON body with {@code client_name}, {@code redirect_uris} (required); optional
 * {@code scopes}, {@code grant_types}, {@code client_id}, {@code client_logo}, {@code client_description}.
 * Success: {@code 201} with {@code client_id} and one-time {@code client_secret}.
 * {@code GET}: list or fetch by {@code client_id} (no secret). Errors: {@code 401}, {@code 400}, {@code 409}, {@code 404}.
 */
@Path("/admin/clients")
@Produces(MediaType.APPLICATION_JSON)
public class AdminClientsEndpoint {

    private static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");
    private static final List<String> DEFAULT_GRANT_TYPES = List.of("authorization_code", "refresh_token");

    @Inject
    ClientRepository clientRepository;

    @Inject
    PasswordService passwordService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> register(ClientRegistrationRequest request) {
        if (request == null
                || request.getClientName() == null || request.getClientName().isBlank()
                || request.getRedirectUris() == null || request.getRedirectUris().isEmpty()) {
            return Uni.createFrom().item(badRequest("invalid_request", "client_name and redirect_uris are required"));
        }

        for (String uri : request.getRedirectUris()) {
            if (uri == null || uri.isBlank()) {
                return Uni.createFrom().item(badRequest("invalid_request", "redirect_uris must not contain blank values"));
            }
        }

        String clientId = request.getClientId();
        if (clientId == null || clientId.isBlank()) {
            clientId = "client-" + UUID.randomUUID();
        }

        final String resolvedClientId = clientId;
        return clientRepository.findByClientId(resolvedClientId).flatMap(existing -> {
            if (existing.isPresent()) {
                return Uni.createFrom().item(conflict("conflict", "client_id already exists"));
            }

            String plaintextSecret = UUID.randomUUID().toString() + UUID.randomUUID();
            String secretHash = passwordService.hashPassword(plaintextSecret);

            List<String> scopes = (request.getScopes() == null || request.getScopes().isEmpty())
                    ? DEFAULT_SCOPES : List.copyOf(request.getScopes());
            List<String> grantTypes = (request.getGrantTypes() == null || request.getGrantTypes().isEmpty())
                    ? DEFAULT_GRANT_TYPES : List.copyOf(request.getGrantTypes());

            Client client = new Client(
                    UUID.randomUUID(), resolvedClientId, secretHash, request.getClientName().trim(),
                    List.copyOf(request.getRedirectUris()), scopes, grantTypes, true,
                    OffsetDateTime.now(), request.getClientLogo(), request.getClientDescription());

            return clientRepository.persistClient(client)
                    .map(saved -> Response.status(Response.Status.CREATED)
                            .entity(toResponse(saved, plaintextSecret)).build());
        });
    }

    @GET
    public Uni<Response> list() {
        return clientRepository.findAllClients()
                .map(clients -> clients.stream().map(c -> toResponse(c, null)).toList())
                .map(list -> Response.ok(list).build());
    }

    @GET
    @Path("/{clientId}")
    public Uni<Response> get(@PathParam("clientId") String clientId) {
        return clientRepository.findByClientId(clientId)
                .map(opt -> opt.map(c -> Response.ok(toResponse(c, null)).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                                .entity(error("not_found", "Client not found")).build()));
    }

    private static ClientRegistrationResponse toResponse(Client client, String plaintextSecret) {
        return new ClientRegistrationResponse(
                client.clientId, plaintextSecret, client.clientName, client.redirectUris,
                client.scopes, client.grantTypes, client.enabled, client.clientLogo, client.clientDescription);
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
