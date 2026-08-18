package com.etheric.service;

import com.etheric.config.EthericTtlConfig;
import com.etheric.entity.Client;
import com.etheric.entity.ServerSettings;
import com.etheric.model.ClientOAuthPolicy;
import com.etheric.model.TokenLifetimes;
import com.etheric.repository.ClientRepository;
import com.etheric.repository.ServerSettingsRepository;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves OAuth cookie and token lifetime settings from DB with application.properties fallback.
 */
@ApplicationScoped
public class TokenPolicyService {

    private static final Logger LOG = Logger.getLogger(TokenPolicyService.class);

    @Inject
    ServerSettingsRepository serverSettingsRepository;

    @Inject
    ClientRepository clientRepository;

    @Inject
    EthericTtlConfig ttlConfig;

    @ConfigProperty(name = "etheric.session.cookie.secure", defaultValue = "true")
    boolean configSessionCookieSecure;

    private volatile ServerSettingsSnapshot snapshot;

    void onStartup(@Observes StartupEvent event) {
        snapshot = ServerSettingsSnapshot.fromConfig(ttlConfig, configSessionCookieSecure);
        try {
            VertxContextSupport.subscribeAndAwait(this::refreshCache);
            LOG.info("Token policy loaded from database");
        } catch (Throwable err) {
            LOG.warnf("Using application.properties token policy (DB unavailable): %s", err.getMessage());
        }
    }

    public Uni<Void> refreshCache() {
        return serverSettingsRepository.getSettings()
                .map(settings -> {
                    if (settings == null) {
                        snapshot = ServerSettingsSnapshot.fromConfig(ttlConfig, configSessionCookieSecure);
                    } else {
                        snapshot = ServerSettingsSnapshot.fromEntity(settings);
                    }
                    return null;
                });
    }

    /** @deprecated Prefer {@link #defaultOAuthPolicy()} */
    public String oauthSessionCookieName() {
        return defaultOAuthPolicy().getSessionCookieName();
    }

    /** @deprecated Prefer {@link #defaultOAuthPolicy()} */
    public boolean sessionCookieSecure() {
        return defaultOAuthPolicy().isSessionCookieSecure();
    }

    /** @deprecated Prefer {@link #defaultOAuthPolicy()} */
    public long oauthSessionLifetimeSeconds() {
        return defaultOAuthPolicy().getSessionLifetimeSeconds();
    }

    public ClientOAuthPolicy defaultOAuthPolicy() {
        ServerSettingsSnapshot active = resolveSnapshot();
        return new ClientOAuthPolicy(
                active.oauthSessionCookieName(),
                active.sessionCookieSecure(),
                active.defaultAccessTokenLifetimeSeconds(),
                active.defaultRefreshTokenLifetimeSeconds(),
                active.oauthSessionLifetimeSeconds());
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
            ClientOAuthPolicy serverDefault = defaultOAuthPolicy();
            byCookieName.put(serverDefault.getSessionCookieName(), serverDefault);
            for (Client client : clients) {
                ClientOAuthPolicy policy = oauthPolicyForClient(client);
                byCookieName.putIfAbsent(policy.getSessionCookieName(), policy);
            }
            return List.copyOf(byCookieName.values());
        });
    }

    public TokenLifetimes defaultLifetimes() {
        return toLifetimes(defaultOAuthPolicy());
    }

    public ServerSettingsSnapshot currentSnapshot() {
        return resolveSnapshot();
    }

    private ClientOAuthPolicy oauthPolicyForClient(Client client) {
        ServerSettingsSnapshot active = resolveSnapshot();
        return new ClientOAuthPolicy(
                resolveCookieName(client.sessionCookieName, active.oauthSessionCookieName()),
                resolveCookieSecure(client.sessionCookieSecure, active.sessionCookieSecure()),
                client.accessTokenLifetimeSeconds != null
                        ? client.accessTokenLifetimeSeconds
                        : active.defaultAccessTokenLifetimeSeconds(),
                client.refreshTokenLifetimeSeconds != null
                        ? client.refreshTokenLifetimeSeconds
                        : active.defaultRefreshTokenLifetimeSeconds(),
                client.sessionLifetimeSeconds != null
                        ? client.sessionLifetimeSeconds
                        : active.oauthSessionLifetimeSeconds());
    }

    private static String resolveCookieName(String clientValue, String serverDefault) {
        if (clientValue == null || clientValue.isBlank()) {
            return serverDefault;
        }
        return clientValue.trim();
    }

    private static boolean resolveCookieSecure(Boolean clientValue, boolean serverDefault) {
        return clientValue != null ? clientValue : serverDefault;
    }

    private TokenLifetimes toLifetimes(ClientOAuthPolicy policy) {
        return new TokenLifetimes(
                policy.getAccessTokenLifetimeSeconds(),
                policy.getRefreshTokenLifetimeSeconds(),
                policy.getSessionLifetimeSeconds());
    }

    private ServerSettingsSnapshot resolveSnapshot() {
        ServerSettingsSnapshot active = snapshot;
        if (active == null) {
            active = ServerSettingsSnapshot.fromConfig(ttlConfig, configSessionCookieSecure);
            snapshot = active;
        }
        return active;
    }

    public record ServerSettingsSnapshot(
            String oauthSessionCookieName,
            long oauthSessionLifetimeSeconds,
            long defaultAccessTokenLifetimeSeconds,
            long defaultRefreshTokenLifetimeSeconds,
            boolean sessionCookieSecure) {

        static ServerSettingsSnapshot fromEntity(ServerSettings settings) {
            return new ServerSettingsSnapshot(
                    settings.oauthSessionCookieName,
                    settings.oauthSessionLifetimeSeconds,
                    settings.defaultAccessTokenLifetimeSeconds,
                    settings.defaultRefreshTokenLifetimeSeconds,
                    settings.sessionCookieSecure);
        }

        static ServerSettingsSnapshot fromConfig(EthericTtlConfig config, boolean secure) {
            return new ServerSettingsSnapshot(
                    "SESSIONID",
                    config.sessionLifetime(),
                    config.accessTokenLifetime(),
                    config.refreshTokenLifetime(),
                    secure);
        }
    }

    public static ServerSettings applyView(ServerSettings settings, com.etheric.model.ServerSettingsView view) {
        settings.oauthSessionCookieName = view.getOauthSessionCookieName().trim();
        settings.oauthSessionLifetimeSeconds = view.getOauthSessionLifetimeSeconds();
        settings.defaultAccessTokenLifetimeSeconds = view.getDefaultAccessTokenLifetimeSeconds();
        settings.defaultRefreshTokenLifetimeSeconds = view.getDefaultRefreshTokenLifetimeSeconds();
        settings.sessionCookieSecure = view.isSessionCookieSecure();
        settings.updatedAt = OffsetDateTime.now();
        return settings;
    }

    public static String validateCookieName(String cookieName) {
        if (cookieName == null || cookieName.isBlank()) {
            return null;
        }
        String trimmed = cookieName.trim();
        if (!trimmed.matches("[A-Za-z0-9_-]+")) {
            return "session_cookie_name must be alphanumeric, dash, or underscore";
        }
        return null;
    }

    public static String normalizeOptionalCookieName(String cookieName) {
        if (cookieName == null || cookieName.isBlank()) {
            return null;
        }
        return cookieName.trim();
    }
}
