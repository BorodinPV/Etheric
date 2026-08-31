package com.etheric.service;

import com.etheric.entity.Client;
import com.etheric.exception.OAuthError;
import com.etheric.exception.OAuthException;
import com.etheric.repository.ClientRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Shared OAuth client authentication (form params or HTTP Basic).
 */
@ApplicationScoped
@RequiredArgsConstructor
public class ClientAuthService {

    private final ClientRepository clientRepository;
    private final PasswordService passwordService;

    public record ClientCredentials(String clientId, String clientSecret) {
    }

    public ClientCredentials resolveCredentials(String formClientId, String formClientSecret,
                                                HttpHeaders headers) {
        String clientId = formClientId;
        String clientSecret = formClientSecret;

        if ((clientId == null || clientSecret == null) && headers != null) {
            String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
                ClientCredentials basic = parseBasicAuth(authHeader.substring(6).trim());
                if (clientId == null) {
                    clientId = basic.clientId();
                }
                if (clientSecret == null) {
                    clientSecret = basic.clientSecret();
                }
            }
        }
        return new ClientCredentials(clientId, clientSecret);
    }

    public Uni<Client> authenticateRequired(String formClientId, String formClientSecret,
                                            HttpHeaders headers) {
        ClientCredentials creds = resolveCredentials(formClientId, formClientSecret, headers);
        if (creds.clientId() == null || creds.clientSecret() == null || creds.clientSecret().isBlank()) {
            return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
        }
        return authenticate(creds.clientId(), creds.clientSecret());
    }

    public static final String AUTH_METHOD_CLIENT_SECRET_BASIC = "client_secret_basic";
    public static final String AUTH_METHOD_NONE = "none";

    public Uni<Client> authenticate(String clientId, String clientSecret) {
        return clientRepository.findByClientId(clientId).flatMap(opt -> {
            if (opt.isEmpty() || !opt.get().enabled) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
            }
            Client client = opt.get();
            if (!passwordService.verifyPassword(clientSecret, client.clientSecretHash)) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
            }
            return Uni.createFrom().item(client);
        });
    }

    public boolean isPublicClient(Client client) {
        return client != null && AUTH_METHOD_NONE.equals(client.tokenEndpointAuthMethod);
    }

    /**
     * Authenticates a client to the token endpoint based on its configured token endpoint
     * authentication method.
     * <p>
     * Confidential clients ({@code client_secret_basic}) must always present and verify their
     * secret. Public clients ({@code none}) authenticate without a secret — they must satisfy
     * PKCE for the authorization_code grant, otherwise they fail like an unauthenticated client.
     *
     * @param pkceSatisfied whether the request carries a valid PKCE proof (only meaningful for public
     *                      clients; confidential clients authenticate via secret). Pass {@code true}
     *                      for the refresh_token grant, where a public client is bound via its token.
     */
    public Uni<Client> authenticateForTokenEndpoint(String formClientId, String formClientSecret,
                                                    HttpHeaders headers, boolean pkceSatisfied) {
        ClientCredentials creds = resolveCredentials(formClientId, formClientSecret, headers);
        if (creds.clientId() == null) {
            return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
        }
        return clientRepository.findByClientId(creds.clientId()).flatMap(opt -> {
            if (opt.isEmpty() || !opt.get().enabled) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
            }
            Client client = opt.get();
            if (isPublicClient(client)) {
                if (pkceSatisfied) {
                    return Uni.createFrom().item(client);
                }
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
            }
            if (creds.clientSecret() == null || creds.clientSecret().isBlank()) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
            }
            if (!passwordService.verifyPassword(creds.clientSecret(), client.clientSecretHash)) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
            }
            return Uni.createFrom().item(client);
        });
    }

    private ClientCredentials parseBasicAuth(String encoded) {
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) {
                return new ClientCredentials(decoded, "");
            }
            return new ClientCredentials(decoded.substring(0, colon), decoded.substring(colon + 1));
        } catch (IllegalArgumentException e) {
            return new ClientCredentials(null, null);
        }
    }
}
