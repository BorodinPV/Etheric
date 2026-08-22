package com.etheric.service;

import com.etheric.config.EthericAdminConfig;
import com.etheric.entity.User;
import com.etheric.model.AdminFlashData;
import com.etheric.model.AdminSessionData;
import com.etheric.repository.UserRepository;
import com.etheric.util.AdminSessionCookieFactory;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class AdminConsoleAuthService {

    public static final String ADMIN_ROLE = "admin";
    public static final String SESSION_PROPERTY = "admin.session";
    public static final String SESSION_ID_PROPERTY = "admin.session.id";

    private final UserRepository userRepository;
    private final CacheService cacheService;
    private final EthericAdminConfig adminConfig;

    public Uni<AnonymousSession> createAnonymousSession() {
        String sessionId = UUID.randomUUID().toString();
        AdminSessionData session = new AdminSessionData(null, null, UUID.randomUUID().toString(),
                System.currentTimeMillis());
        return cacheService.saveAdminSession(sessionId, session, adminConfig.sessionLifetime())
                .replaceWith(new AnonymousSession(sessionId, session));
    }

    public Uni<LoginAttemptResult> login(String sessionId, AdminSessionData session,
                                         String username, String password, String csrfToken) {
        if (sessionId == null || session == null
                || csrfToken == null || !csrfToken.equals(session.getCsrfToken())) {
            return Uni.createFrom().item(LoginAttemptResult.csrfError());
        }

        return userRepository.authenticate(username != null ? username.trim() : null, password).flatMap(userOpt -> {
            if (userOpt.isEmpty()) {
                return rotateCsrf(sessionId, session)
                        .map(updated -> LoginAttemptResult.invalidCredentials(updated.session(), updated.sessionId()));
            }
            User user = userOpt.get();
            if (!hasAdminRole(user.roles)) {
                return rotateCsrf(sessionId, session)
                        .map(updated -> LoginAttemptResult.accessDenied(updated.session(), updated.sessionId()));
            }
            return createAuthenticatedSession(user)
                    .flatMap(result -> cacheService.deleteAdminSession(sessionId)
                            .replaceWith(LoginAttemptResult.success(result.sessionId(), result.session())));
        });
    }

    public Uni<LogoutResult> logout(String sessionId) {
        if (sessionId == null) {
            return Uni.createFrom().item(new LogoutResult(null));
        }
        return cacheService.deleteAdminSession(sessionId)
                .replaceWith(new LogoutResult(sessionId));
    }

    public Uni<AdminSessionData> getSession(String sessionId) {
        return cacheService.getAdminSession(sessionId);
    }

    public Uni<SessionRotation> rotateCsrf(String sessionId, AdminSessionData session) {
        session.setCsrfToken(UUID.randomUUID().toString());
        return cacheService.saveAdminSession(sessionId, session, adminConfig.sessionLifetime())
                .replaceWith(new SessionRotation(sessionId, session));
    }

    public Uni<AuthenticatedSessionResult> createAuthenticatedSession(User user) {
        String sessionId = UUID.randomUUID().toString();
        AdminSessionData session = new AdminSessionData(
                user.id, user.username, UUID.randomUUID().toString(), System.currentTimeMillis());
        return cacheService.saveAdminSession(sessionId, session, adminConfig.sessionLifetime())
                .replaceWith(new AuthenticatedSessionResult(sessionId, session));
    }

    public boolean validateCsrf(AdminSessionData session, String csrfToken) {
        return session != null && csrfToken != null && csrfToken.equals(session.getCsrfToken());
    }

    public static boolean hasAdminRole(List<String> roles) {
        return roles != null && roles.contains(ADMIN_ROLE);
    }

    public static String extractSessionId(HttpHeaders headers) {
        return AdminSessionCookieFactory.extractSessionId(headers);
    }

    public Response redirectToLogin(String redirectUri) {
        String target = adminConfig.loginPath();
        if (redirectUri != null && !redirectUri.isBlank() && redirectUri.startsWith(adminConfig.consolePath())) {
            target += "?redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8);
        }
        return Response.seeOther(URI.create(target)).build();
    }

    public Uni<Void> setFlash(String sessionId, AdminFlashData flash) {
        return cacheService.saveAdminFlash(sessionId, flash, 120);
    }

    public Uni<AdminFlashData> consumeFlash(String sessionId) {
        return cacheService.getAdminFlash(sessionId)
                .flatMap(flash -> {
                    if (flash == null) {
                        return Uni.createFrom().nullItem();
                    }
                    return cacheService.deleteAdminFlash(sessionId).replaceWith(flash);
                });
    }

    public record LoginAttemptResult(Outcome outcome, String sessionId, AdminSessionData session, String errorMessage) {

        public enum Outcome { SUCCESS, INVALID_CREDENTIALS, ACCESS_DENIED, CSRF_ERROR }

        public static LoginAttemptResult success(String sessionId, AdminSessionData session) {
            return new LoginAttemptResult(Outcome.SUCCESS, sessionId, session, null);
        }

        public static LoginAttemptResult invalidCredentials(AdminSessionData session, String sessionId) {
            return new LoginAttemptResult(Outcome.INVALID_CREDENTIALS, sessionId, session,
                    "invalid_credentials");
        }

        public static LoginAttemptResult accessDenied(AdminSessionData session, String sessionId) {
            return new LoginAttemptResult(Outcome.ACCESS_DENIED, sessionId, session,
                    "access_denied");
        }

        public static LoginAttemptResult csrfError() {
            return new LoginAttemptResult(Outcome.CSRF_ERROR, null, null, "invalid_csrf");
        }
    }

    public record AuthenticatedSessionResult(String sessionId, AdminSessionData session) {
    }

    public record SessionRotation(String sessionId, AdminSessionData session) {
    }

    public record LogoutResult(String sessionId) {
    }

    public record AnonymousSession(String sessionId, AdminSessionData session) {
    }
}
