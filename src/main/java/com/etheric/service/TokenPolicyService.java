package com.etheric.service;

import com.etheric.config.EthericTtlConfig;
import com.etheric.entity.Client;
import com.etheric.model.ClientOAuthPolicy;
import com.etheric.model.TokenLifetimes;
import com.etheric.repository.ClientRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves OAuth cookie and token lifetime settings per client.
 * Application.properties provides bootstrap defaults for new clients and anonymous fallbacks.
 */
@ApplicationScoped
public class TokenPolicyService {

    private final ClientRepository clientRepository;
    private final EthericTtlConfig ttlConfig;
    private final boolean configSessionCookieSecure;

    @Inject
    public TokenPolicyService(ClientRepository clientRepository,
                              EthericTtlConfig ttlConfig,
                              @ConfigProperty(name = "etheric.session.cookie.secure", defaultValue = "true")
                              boolean configSessionCookieSecure) {
        this.clientRepository = clientRepository;
        this.ttlConfig = ttlConfig;
        this.configSessionCookieSecure = configSessionCookieSecure;
    }

    public ClientOAuthPolicy defaultOAuthPolicy() {
        return fromConfig(ttlConfig, configSessionCookieSecure);
    }

    public Uni<ClientOAuthPolicy> resolveOAuthPolicyForClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Uni.createFrom().item(defaultOAuthPolicy());
        }
        return clientRepository.findByClientId(clientId)
                .map(opt -> opt.map(this::oauthPolicyForClient).orElseGet(this::defaultOAuthPolicy));
    }

    public Uni<TokenLifetimes> resolveForClient(String clientId) {
        return resolveOAuthPolicyForClient(clientId).map(this::toLifetimes);
    }

    public Uni<Long> resolveSessionLifetimeForClient(String clientId) {
        return resolveOAuthPolicyForClient(clientId).map(ClientOAuthPolicy::getSessionLifetimeSeconds);
    }

    public Uni<List<ClientOAuthPolicy>> knownOAuthPolicies() {
        return clientRepository.findAllClients().map(clients -> {
            Map<String, ClientOAuthPolicy> byCookieName = new LinkedHashMap<>();
            for (Client client : clients) {
                ClientOAuthPolicy policy = oauthPolicyForClient(client);
                byCookieName.putIfAbsent(policy.getSessionCookieName(), policy);
            }
            if (byCookieName.isEmpty()) {
                ClientOAuthPolicy fallback = defaultOAuthPolicy();
                byCookieName.put(fallback.getSessionCookieName(), fallback);
            }
            return List.copyOf(byCookieName.values());
        });
    }

    public TokenLifetimes defaultLifetimes() {
        return toLifetimes(defaultOAuthPolicy());
    }

    public static ClientOAuthPolicy fromConfig(EthericTtlConfig config, boolean secure) {
        return new ClientOAuthPolicy(
                "SESSIONID",
                secure,
                config.accessTokenLifetime(),
                config.refreshTokenLifetime(),
                config.sessionLifetime());
    }

    private ClientOAuthPolicy oauthPolicyForClient(Client client) {
        return new ClientOAuthPolicy(
                client.sessionCookieName,
                client.sessionCookieSecure,
                client.accessTokenLifetimeSeconds,
                client.refreshTokenLifetimeSeconds,
                client.sessionLifetimeSeconds);
    }

    private TokenLifetimes toLifetimes(ClientOAuthPolicy policy) {
        return new TokenLifetimes(
                policy.getAccessTokenLifetimeSeconds(),
                policy.getRefreshTokenLifetimeSeconds(),
                policy.getSessionLifetimeSeconds());
    }

    public static String validateCookieName(String cookieName) {
        if (cookieName == null || cookieName.isBlank()) {
            return "session_cookie_name is required";
        }
        String trimmed = cookieName.trim();
        if (!trimmed.matches("[A-Za-z0-9_-]+")) {
            return "session_cookie_name must be alphanumeric, dash, or underscore";
        }
        return null;
    }

    public static String normalizeCookieName(String cookieName) {
        if (cookieName == null || cookieName.isBlank()) {
            return null;
        }
        return cookieName.trim();
    }
}
