package com.etheric.service;

import com.etheric.entity.Client;
import com.etheric.model.ClientRegistrationRequest;
import com.etheric.model.ClientUpdateRequest;
import com.etheric.model.ClientRegistrationResponse;
import com.etheric.repository.ClientRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AdminClientService {

    private static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");
    private static final List<String> DEFAULT_GRANT_TYPES = List.of("authorization_code", "refresh_token");

    @Inject
    ClientRepository clientRepository;

    @Inject
    PasswordService passwordService;

    public Uni<AdminServiceResult<ClientRegistrationResponse>> register(ClientRegistrationRequest request) {
        if (request == null
                || request.getClientName() == null || request.getClientName().isBlank()
                || request.getRedirectUris() == null || request.getRedirectUris().isEmpty()) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "client_name and redirect_uris are required"));
        }

        for (String uri : request.getRedirectUris()) {
            if (uri == null || uri.isBlank()) {
                return Uni.createFrom().item(AdminServiceResult.badRequest(
                        "invalid_request", "redirect_uris must not contain blank values"));
            }
        }

        String clientId = request.getClientId();
        if (clientId == null || clientId.isBlank()) {
            clientId = "client-" + UUID.randomUUID();
        }

        final String resolvedClientId = clientId;
        return clientRepository.findByClientId(resolvedClientId).flatMap(existing -> {
            if (existing.isPresent()) {
                return Uni.createFrom().item(AdminServiceResult.conflict(
                        "conflict", "client_id already exists"));
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
                    OffsetDateTime.now(), request.getClientDescription());

            return clientRepository.persistClient(client)
                    .map(saved -> AdminServiceResult.ok(toResponse(saved, plaintextSecret)));
        });
    }

    public Uni<List<ClientRegistrationResponse>> list() {
        return clientRepository.findAllClients()
                .map(clients -> clients.stream().map(c -> toResponse(c, null)).toList());
    }

    public Uni<AdminServiceResult<ClientRegistrationResponse>> get(String clientId) {
        return clientRepository.findByClientId(clientId)
                .map(opt -> opt.map(c -> AdminServiceResult.ok(toResponse(c, null)))
                        .orElseGet(() -> AdminServiceResult.notFound("not_found", "Client not found")));
    }

    public Uni<AdminServiceResult<ClientRegistrationResponse>> update(String clientId, ClientUpdateRequest request) {
        if (request == null) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "request body is required"));
        }
        if (request.getClientName() == null
                && request.getRedirectUris() == null
                && request.getScopes() == null
                && request.getGrantTypes() == null
                && request.getEnabled() == null
                && request.getClientDescription() == null) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "at least one field must be provided"));
        }
        if (request.getClientName() != null && request.getClientName().isBlank()) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "client_name must not be blank"));
        }
        if (request.getRedirectUris() != null) {
            if (request.getRedirectUris().isEmpty()) {
                return Uni.createFrom().item(AdminServiceResult.badRequest(
                        "invalid_request", "redirect_uris must not be empty"));
            }
            for (String uri : request.getRedirectUris()) {
                if (uri == null || uri.isBlank()) {
                    return Uni.createFrom().item(AdminServiceResult.badRequest(
                            "invalid_request", "redirect_uris must not contain blank values"));
                }
            }
        }
        if (request.getScopes() != null && request.getScopes().isEmpty()) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "scopes must not be empty"));
        }
        if (request.getGrantTypes() != null && request.getGrantTypes().isEmpty()) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "grant_types must not be empty"));
        }

        return clientRepository.findByClientId(clientId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(AdminServiceResult.notFound("not_found", "Client not found"));
            }
            Client client = opt.get();
            if (request.getClientName() != null) {
                client.clientName = request.getClientName().trim();
            }
            if (request.getRedirectUris() != null) {
                client.redirectUris = List.copyOf(request.getRedirectUris());
            }
            if (request.getScopes() != null) {
                client.scopes = List.copyOf(request.getScopes());
            }
            if (request.getGrantTypes() != null) {
                client.grantTypes = List.copyOf(request.getGrantTypes());
            }
            if (request.getEnabled() != null) {
                client.enabled = request.getEnabled();
            }
            if (request.getClientDescription() != null) {
                client.clientDescription = request.getClientDescription().isBlank()
                        ? null : request.getClientDescription().trim();
            }
            return clientRepository.updateClient(client)
                    .flatMap(ignored -> clientRepository.findByClientId(clientId))
                    .map(updated -> AdminServiceResult.ok(toResponse(updated.orElseThrow(), null)));
        });
    }

    public Uni<AdminServiceResult<ClientRegistrationResponse>> updateOAuthSettings(
            String clientId, Integer accessTokenLifetimeSeconds,
            Integer refreshTokenLifetimeSeconds, Integer sessionLifetimeSeconds,
            String sessionCookieName, Boolean sessionCookieSecure) {
        if (accessTokenLifetimeSeconds != null && accessTokenLifetimeSeconds <= 0) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "access_token_lifetime_seconds must be positive"));
        }
        if (refreshTokenLifetimeSeconds != null && refreshTokenLifetimeSeconds <= 0) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "refresh_token_lifetime_seconds must be positive"));
        }
        if (sessionLifetimeSeconds != null && sessionLifetimeSeconds <= 0) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "session_lifetime_seconds must be positive"));
        }
        String cookieNameError = TokenPolicyService.validateCookieName(sessionCookieName);
        if (cookieNameError != null) {
            return Uni.createFrom().item(AdminServiceResult.badRequest("invalid_request", cookieNameError));
        }

        return clientRepository.findByClientId(clientId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(AdminServiceResult.notFound("not_found", "Client not found"));
            }
            Client client = opt.get();
            client.accessTokenLifetimeSeconds = accessTokenLifetimeSeconds;
            client.refreshTokenLifetimeSeconds = refreshTokenLifetimeSeconds;
            client.sessionLifetimeSeconds = sessionLifetimeSeconds;
            client.sessionCookieName = TokenPolicyService.normalizeOptionalCookieName(sessionCookieName);
            client.sessionCookieSecure = sessionCookieSecure;
            return clientRepository.updateClient(client)
                    .flatMap(ignored -> clientRepository.findByClientId(clientId))
                    .map(updated -> AdminServiceResult.ok(toResponse(updated.orElseThrow(), null)));
        });
    }

    /** @deprecated Use {@link #updateOAuthSettings} */
    public Uni<AdminServiceResult<ClientRegistrationResponse>> updateTokenLifetimes(
            String clientId, Integer accessTokenLifetimeSeconds,
            Integer refreshTokenLifetimeSeconds, Integer sessionLifetimeSeconds) {
        return updateOAuthSettings(clientId, accessTokenLifetimeSeconds, refreshTokenLifetimeSeconds,
                sessionLifetimeSeconds, null, null);
    }

    public Uni<AdminServiceResult<ClientRegistrationResponse>> regenerateSecret(String clientId) {
        return clientRepository.findByClientId(clientId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(AdminServiceResult.notFound("not_found", "Client not found"));
            }
            Client client = opt.get();
            String plaintextSecret = UUID.randomUUID().toString() + UUID.randomUUID();
            client.clientSecretHash = passwordService.hashPassword(plaintextSecret);
            return clientRepository.updateClient(client)
                    .map(ignored -> AdminServiceResult.ok(toResponse(client, plaintextSecret)));
        });
    }

    public static ClientRegistrationResponse toResponse(Client client, String plaintextSecret) {
        return new ClientRegistrationResponse(
                client.clientId, plaintextSecret, client.clientName, client.redirectUris,
                client.scopes, client.grantTypes, client.enabled, client.clientDescription,
                client.accessTokenLifetimeSeconds, client.refreshTokenLifetimeSeconds,
                client.sessionLifetimeSeconds, client.sessionCookieName, client.sessionCookieSecure);
    }
}
