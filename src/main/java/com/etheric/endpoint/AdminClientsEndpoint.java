package com.etheric.endpoint;

import com.etheric.entity.Client;
import com.etheric.model.ClientRegistrationRequest;
import com.etheric.model.ClientRegistrationResponse;
import com.etheric.repository.ClientRepository;
import com.etheric.service.PasswordService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public Response register(ClientRegistrationRequest request) {
        if (request == null
                || request.getClientName() == null || request.getClientName().isBlank()
                || request.getRedirectUris() == null || request.getRedirectUris().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error("invalid_request", "client_name and redirect_uris are required"))
                    .build();
        }

        for (String uri : request.getRedirectUris()) {
            if (uri == null || uri.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(error("invalid_request", "redirect_uris must not contain blank values"))
                        .build();
            }
        }

        String clientId = request.getClientId();
        if (clientId == null || clientId.isBlank()) {
            clientId = "client-" + UUID.randomUUID();
        } else if (clientRepository.findByClientId(clientId).isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(error("invalid_request", "client_id already exists"))
                    .build();
        }

        String plaintextSecret = UUID.randomUUID().toString() + UUID.randomUUID();
        String secretHash = passwordService.hashPassword(plaintextSecret);

        List<String> scopes = (request.getScopes() == null || request.getScopes().isEmpty())
                ? DEFAULT_SCOPES
                : List.copyOf(request.getScopes());
        List<String> grantTypes = (request.getGrantTypes() == null || request.getGrantTypes().isEmpty())
                ? DEFAULT_GRANT_TYPES
                : List.copyOf(request.getGrantTypes());

        Client client = new Client(
                UUID.randomUUID(),
                clientId,
                secretHash,
                request.getClientName().trim(),
                List.copyOf(request.getRedirectUris()),
                scopes,
                grantTypes,
                true,
                LocalDateTime.now(),
                request.getClientLogo(),
                request.getClientDescription()
        );
        clientRepository.save(client);

        return Response.status(Response.Status.CREATED)
                .entity(toResponse(client, plaintextSecret))
                .build();
    }

    @GET
    public Response list() {
        List<ClientRegistrationResponse> clients = clientRepository.findAll().stream()
                .map(c -> toResponse(c, null))
                .collect(Collectors.toList());
        return Response.ok(clients).build();
    }

    @GET
    @Path("/{clientId}")
    public Response get(@PathParam("clientId") String clientId) {
        return clientRepository.findByClientId(clientId)
                .map(c -> Response.ok(toResponse(c, null)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(error("not_found", "Client not found"))
                        .build());
    }

    private static ClientRegistrationResponse toResponse(Client client, String plaintextSecret) {
        return new ClientRegistrationResponse(
                client.getClientId(),
                plaintextSecret,
                client.getClientName(),
                client.getRedirectUris(),
                client.getScopes(),
                client.getGrantTypes(),
                client.isEnabled(),
                client.getClientLogo(),
                client.getClientDescription()
        );
    }

    private static java.util.Map<String, String> error(String code, String description) {
        return java.util.Map.of("error", code, "error_description", description);
    }
}
