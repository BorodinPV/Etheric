package com.etheric.service;

import com.etheric.entity.ServerSettings;
import com.etheric.model.ServerSettingsView;
import com.etheric.repository.ServerSettingsRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AdminServerSettingsService {

    @Inject
    ServerSettingsRepository serverSettingsRepository;

    @Inject
    TokenPolicyService tokenPolicyService;

    public Uni<AdminServiceResult<ServerSettingsView>> get() {
        return serverSettingsRepository.getSettings()
                .map(settings -> {
                    if (settings == null) {
                        return AdminServiceResult.ok(toView(tokenPolicyService.currentSnapshot()));
                    }
                    return AdminServiceResult.ok(toView(settings));
                });
    }

    public Uni<AdminServiceResult<ServerSettingsView>> update(ServerSettingsView view) {
        if (view == null) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "settings are required"));
        }
        if (view.getOauthSessionCookieName() == null || view.getOauthSessionCookieName().isBlank()) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "oauth_session_cookie_name is required"));
        }
        if (!view.getOauthSessionCookieName().matches("[A-Za-z0-9_-]+")) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "oauth_session_cookie_name must be alphanumeric, dash, or underscore"));
        }
        if (view.getOauthSessionLifetimeSeconds() <= 0
                || view.getDefaultAccessTokenLifetimeSeconds() <= 0
                || view.getDefaultRefreshTokenLifetimeSeconds() <= 0) {
            return Uni.createFrom().item(AdminServiceResult.badRequest(
                    "invalid_request", "lifetimes must be positive"));
        }

        return serverSettingsRepository.getSettings().flatMap(existing -> {
            ServerSettings settings = existing != null ? existing : new ServerSettings();
            TokenPolicyService.applyView(settings, view);
            return serverSettingsRepository.updateSettings(settings)
                    .flatMap(v -> tokenPolicyService.refreshCache())
                    .map(v -> AdminServiceResult.ok(toView(settings)));
        });
    }

    private static ServerSettingsView toView(ServerSettings settings) {
        return new ServerSettingsView(
                settings.oauthSessionCookieName,
                settings.oauthSessionLifetimeSeconds,
                settings.defaultAccessTokenLifetimeSeconds,
                settings.defaultRefreshTokenLifetimeSeconds,
                settings.sessionCookieSecure);
    }

    private static ServerSettingsView toView(TokenPolicyService.ServerSettingsSnapshot snapshot) {
        return new ServerSettingsView(
                snapshot.oauthSessionCookieName(),
                (int) snapshot.oauthSessionLifetimeSeconds(),
                (int) snapshot.defaultAccessTokenLifetimeSeconds(),
                (int) snapshot.defaultRefreshTokenLifetimeSeconds(),
                snapshot.sessionCookieSecure());
    }
}
