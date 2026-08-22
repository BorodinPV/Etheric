package com.etheric.service;

import com.etheric.entity.Client;
import com.etheric.model.ClientOAuthPolicy;
import com.etheric.model.ClientRegistrationRequest;
import com.etheric.model.ClientUpdateRequest;
import com.etheric.model.ClientRegistrationResponse;
import com.etheric.repository.ClientRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AdminClientService {

    private static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");
    private static final List<String> DEFAULT_GRANT_TYPES = List.of("authorization_code", "refresh_token");
    private static final String ERROR_INVALID_REQUEST = "invalid_request";
    private static final String ERROR_NOT_FOUND = "not_found";
    private static final String MSG_CLIENT_NOT_FOUND = "Client not found";

    private final ClientRepository clientRepository;
    private final PasswordService passwordService;
    private final TokenPolicyService tokenPolicyService;

    public AdminClientService(ClientRepository clientRepository,
                              PasswordService passwordService,
                              TokenPolicyService tokenPolicyService) {
        this.clientRepository = clientRepository;
        this.passwordService = passwordService;
        this.tokenPolicyService = tokenPolicyService;
    }

    public Uni<AdminServiceResult<ClientRegistrationResponse>> register(ClientRegistrationRequest request) {
        AdminServiceResult<ClientRegistrationResponse> invalid = validateRegisterRequest(request);
        if (invalid != null) {
            return Uni.createFrom().item(invalid);
        }

        OAuthSettings oauthSettings = resolveOAuthSettingsForClient(
                request.getAccessTokenLifetimeSeconds(),
                request.getRefreshTokenLifetimeSeconds(),
                request.getSessionLifetimeSeconds(),
                request.getSessionCookieName(),
                request.getSessionCookieSecure());
        if (oauthSettings.error() != null) {
            return Uni.createFrom().item(invalidRequest(oauthSettings.error()));
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

            Client client = new Client(
                    UUID.randomUUID(), resolvedClientId, secretHash, request.getClientName().trim(),
                    List.copyOf(request.getRedirectUris()),
                    copyOrDefault(request.getScopes(), DEFAULT_SCOPES),
                    copyOrDefault(request.getGrantTypes(), DEFAULT_GRANT_TYPES),
                    true,
                    OffsetDateTime.now(ZoneOffset.UTC), request.getClientDescription(),
                    oauthSettings.accessTokenLifetimeSeconds(),
                    oauthSettings.refreshTokenLifetimeSeconds(),
                    oauthSettings.sessionLifetimeSeconds(),
                    oauthSettings.sessionCookieName(),
                    oauthSettings.sessionCookieSecure());

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
                        .orElseGet(() -> clientNotFound()));
    }

    public Uni<AdminServiceResult<ClientRegistrationResponse>> update(String clientId, ClientUpdateRequest request) {
        AdminServiceResult<ClientRegistrationResponse> invalid = validateUpdateRequest(request);
        if (invalid != null) {
            return Uni.createFrom().item(invalid);
        }

        return clientRepository.findByClientId(clientId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(clientNotFound());
            }
            Client client = opt.get();
            OAuthSettings oauthSettings = resolveOAuthSettingsForUpdate(client, request);
            if (oauthSettings.error() != null) {
                return Uni.createFrom().item(invalidRequest(oauthSettings.error()));
            }
            applyUpdate(client, request, oauthSettings);
            return clientRepository.updateClient(client)
                    .flatMap(ignored -> clientRepository.findByClientId(clientId))
                    .map(updated -> AdminServiceResult.ok(toResponse(updated.orElseThrow(), null)));
        });
    }

    public Uni<AdminServiceResult<ClientRegistrationResponse>> updateOAuthSettings(
            String clientId, Integer accessTokenLifetimeSeconds,
            Integer refreshTokenLifetimeSeconds, Integer sessionLifetimeSeconds,
            String sessionCookieName, Boolean sessionCookieSecure) {
        ClientUpdateRequest request = new ClientUpdateRequest();
        request.setAccessTokenLifetimeSeconds(accessTokenLifetimeSeconds);
        request.setRefreshTokenLifetimeSeconds(refreshTokenLifetimeSeconds);
        request.setSessionLifetimeSeconds(sessionLifetimeSeconds);
        request.setSessionCookieName(sessionCookieName);
        request.setSessionCookieSecure(sessionCookieSecure);
        return update(clientId, request);
    }

    public Uni<AdminServiceResult<ClientRegistrationResponse>> regenerateSecret(String clientId) {
        return clientRepository.findByClientId(clientId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(clientNotFound());
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

    private static AdminServiceResult<ClientRegistrationResponse> validateRegisterRequest(
            ClientRegistrationRequest request) {
        if (request == null
                || request.getClientName() == null || request.getClientName().isBlank()
                || request.getRedirectUris() == null || request.getRedirectUris().isEmpty()) {
            return invalidRequest("client_name and redirect_uris are required");
        }
        if (containsBlankUri(request.getRedirectUris())) {
            return invalidRequest("redirect_uris must not contain blank values");
        }
        return null;
    }

    private static AdminServiceResult<ClientRegistrationResponse> validateUpdateRequest(ClientUpdateRequest request) {
        if (request == null) {
            return invalidRequest("request body is required");
        }
        if (hasNoUpdateFields(request)) {
            return invalidRequest("at least one field must be provided");
        }
        if (request.getClientName() != null && request.getClientName().isBlank()) {
            return invalidRequest("client_name must not be blank");
        }
        AdminServiceResult<ClientRegistrationResponse> redirectUriError = validateUpdateRedirectUris(request);
        if (redirectUriError != null) {
            return redirectUriError;
        }
        if (request.getScopes() != null && request.getScopes().isEmpty()) {
            return invalidRequest("scopes must not be empty");
        }
        if (request.getGrantTypes() != null && request.getGrantTypes().isEmpty()) {
            return invalidRequest("grant_types must not be empty");
        }
        return null;
    }

    private static boolean hasNoUpdateFields(ClientUpdateRequest request) {
        return request.getClientName() == null
                && request.getRedirectUris() == null
                && request.getScopes() == null
                && request.getGrantTypes() == null
                && request.getEnabled() == null
                && request.getClientDescription() == null
                && request.getAccessTokenLifetimeSeconds() == null
                && request.getRefreshTokenLifetimeSeconds() == null
                && request.getSessionLifetimeSeconds() == null
                && request.getSessionCookieName() == null
                && request.getSessionCookieSecure() == null;
    }

    private static AdminServiceResult<ClientRegistrationResponse> validateUpdateRedirectUris(
            ClientUpdateRequest request) {
        if (request.getRedirectUris() == null) {
            return null;
        }
        if (request.getRedirectUris().isEmpty()) {
            return invalidRequest("redirect_uris must not be empty");
        }
        if (containsBlankUri(request.getRedirectUris())) {
            return invalidRequest("redirect_uris must not contain blank values");
        }
        return null;
    }

    private static void applyUpdate(Client client, ClientUpdateRequest request, OAuthSettings oauthSettings) {
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
        if (oauthSettings.apply()) {
            client.accessTokenLifetimeSeconds = oauthSettings.accessTokenLifetimeSeconds();
            client.refreshTokenLifetimeSeconds = oauthSettings.refreshTokenLifetimeSeconds();
            client.sessionLifetimeSeconds = oauthSettings.sessionLifetimeSeconds();
            client.sessionCookieName = oauthSettings.sessionCookieName();
            client.sessionCookieSecure = oauthSettings.sessionCookieSecure();
        }
    }

    private static boolean containsBlankUri(List<String> uris) {
        for (String uri : uris) {
            if (uri == null || uri.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> copyOrDefault(List<String> values, List<String> defaults) {
        return (values == null || values.isEmpty()) ? defaults : List.copyOf(values);
    }

    private static <T> AdminServiceResult<T> invalidRequest(String description) {
        return AdminServiceResult.badRequest(ERROR_INVALID_REQUEST, description);
    }

    private static <T> AdminServiceResult<T> clientNotFound() {
        return AdminServiceResult.notFound(ERROR_NOT_FOUND, MSG_CLIENT_NOT_FOUND);
    }

    private OAuthSettings resolveOAuthSettingsForUpdate(Client client, ClientUpdateRequest request) {
        boolean hasOAuthUpdate = request.getAccessTokenLifetimeSeconds() != null
                || request.getRefreshTokenLifetimeSeconds() != null
                || request.getSessionLifetimeSeconds() != null
                || request.getSessionCookieName() != null
                || request.getSessionCookieSecure() != null;
        if (!hasOAuthUpdate) {
            return OAuthSettings.skip();
        }
        return resolveOAuthSettings(
                request.getAccessTokenLifetimeSeconds() != null
                        ? request.getAccessTokenLifetimeSeconds() : client.accessTokenLifetimeSeconds,
                request.getRefreshTokenLifetimeSeconds() != null
                        ? request.getRefreshTokenLifetimeSeconds() : client.refreshTokenLifetimeSeconds,
                request.getSessionLifetimeSeconds() != null
                        ? request.getSessionLifetimeSeconds() : client.sessionLifetimeSeconds,
                request.getSessionCookieName() != null
                        ? request.getSessionCookieName() : client.sessionCookieName,
                request.getSessionCookieSecure() != null
                        ? request.getSessionCookieSecure() : client.sessionCookieSecure);
    }

    private OAuthSettings resolveOAuthSettingsForClient(Integer accessTokenLifetimeSeconds,
                                                          Integer refreshTokenLifetimeSeconds,
                                                          Integer sessionLifetimeSeconds,
                                                          String sessionCookieName,
                                                          Boolean sessionCookieSecure) {
        ClientOAuthPolicy defaults = tokenPolicyService.defaultOAuthPolicy();
        return resolveOAuthSettings(
                accessTokenLifetimeSeconds != null
                        ? accessTokenLifetimeSeconds : (int) defaults.getAccessTokenLifetimeSeconds(),
                refreshTokenLifetimeSeconds != null
                        ? refreshTokenLifetimeSeconds : (int) defaults.getRefreshTokenLifetimeSeconds(),
                sessionLifetimeSeconds != null
                        ? sessionLifetimeSeconds : (int) defaults.getSessionLifetimeSeconds(),
                sessionCookieName != null ? sessionCookieName : defaults.getSessionCookieName(),
                sessionCookieSecure != null ? sessionCookieSecure : defaults.isSessionCookieSecure());
    }

    private OAuthSettings resolveOAuthSettings(int accessTokenLifetimeSeconds,
                                                 int refreshTokenLifetimeSeconds,
                                                 int sessionLifetimeSeconds,
                                                 String sessionCookieName,
                                                 boolean sessionCookieSecure) {
        if (accessTokenLifetimeSeconds <= 0 || refreshTokenLifetimeSeconds <= 0 || sessionLifetimeSeconds <= 0) {
            return OAuthSettings.error("token and session lifetimes must be positive");
        }
        String cookieName = TokenPolicyService.normalizeCookieName(sessionCookieName);
        String cookieNameError = TokenPolicyService.validateCookieName(cookieName);
        if (cookieNameError != null) {
            return OAuthSettings.error(cookieNameError);
        }

        return OAuthSettings.of(accessTokenLifetimeSeconds, refreshTokenLifetimeSeconds,
                sessionLifetimeSeconds, cookieName, sessionCookieSecure);
    }

    private record OAuthSettings(
            boolean apply,
            String error,
            int accessTokenLifetimeSeconds,
            int refreshTokenLifetimeSeconds,
            int sessionLifetimeSeconds,
            String sessionCookieName,
            boolean sessionCookieSecure) {

        static OAuthSettings skip() {
            return new OAuthSettings(false, null, 0, 0, 0, null, false);
        }

        static OAuthSettings error(String message) {
            return new OAuthSettings(false, message, 0, 0, 0, null, false);
        }

        static OAuthSettings of(int accessTokenLifetimeSeconds, int refreshTokenLifetimeSeconds,
                                int sessionLifetimeSeconds, String sessionCookieName,
                                boolean sessionCookieSecure) {
            return new OAuthSettings(true, null, accessTokenLifetimeSeconds, refreshTokenLifetimeSeconds,
                    sessionLifetimeSeconds, sessionCookieName, sessionCookieSecure);
        }
    }
}
