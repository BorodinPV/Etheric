package com.etheric.service;

import com.etheric.config.EthericTtlConfig;
import com.etheric.entity.Client;
import com.etheric.entity.ServerSettings;
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

    public String oauthSessionCookieName() {
        return resolveSnapshot().oauthSessionCookieName();
    }

    public boolean sessionCookieSecure() {
        return resolveSnapshot().sessionCookieSecure();
    }

    public long oauthSessionLifetimeSeconds() {
        return resolveSnapshot().oauthSessionLifetimeSeconds();
    }

    public Uni<TokenLifetimes> resolveForClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Uni.createFrom().item(defaultLifetimes());
        }
        return clientRepository.findByClientId(clientId)
                .map(opt -> opt.map(this::lifetimesForClient).orElseGet(this::defaultLifetimes));
    }

    public Uni<Long> resolveSessionLifetimeForClient(String clientId) {
        return resolveForClient(clientId).map(TokenLifetimes::getSessionLifetimeSeconds);
    }

    public TokenLifetimes defaultLifetimes() {
        ServerSettingsSnapshot active = resolveSnapshot();
        return new TokenLifetimes(
                active.defaultAccessTokenLifetimeSeconds(),
                active.defaultRefreshTokenLifetimeSeconds(),
                active.oauthSessionLifetimeSeconds());
    }

    private TokenLifetimes lifetimesForClient(Client client) {
        ServerSettingsSnapshot active = resolveSnapshot();
        return new TokenLifetimes(
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

    public ServerSettingsSnapshot currentSnapshot() {
        return resolveSnapshot();
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
}
