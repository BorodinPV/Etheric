package com.etheric.service;

import com.etheric.config.EthericTtlConfig;
import com.etheric.model.ClientOAuthPolicy;
import com.etheric.model.SessionData;
import com.etheric.exception.OAuthError;
import com.etheric.exception.OAuthException;
import com.etheric.util.OAuthRedirectBuilder;
import com.etheric.util.ScopeUtil;
import com.etheric.util.SessionCookieFactory;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class AuthSessionService {

    @Inject
    CacheService cacheService;

    @Inject
    AuthorizationCodeService authorizationCodeService;

    @Inject
    SessionCookieFactory sessionCookieFactory;

    @Inject
    EthericTtlConfig ttlConfig;

    @Inject
    UserClientMembershipService membershipService;

    @Inject
    ConsentService consentService;

    @Inject
    TokenPolicyService tokenPolicyService;

    public Uni<ClientOAuthPolicy> resolveOAuthPolicy(String state) {
        if (state == null) {
            return Uni.createFrom().item(tokenPolicyService.defaultOAuthPolicy());
        }
        return cacheService.getAuthorizationRequestState(state)
                .flatMap(requestState -> {
                    if (requestState == null) {
                        return Uni.createFrom().item(tokenPolicyService.defaultOAuthPolicy());
                    }
                    return tokenPolicyService.resolveOAuthPolicyForClient(requestState.getClientId());
                });
    }

    public Uni<Response> completeLogin(String userId, String state, ClientOAuthPolicy policy) {
        String newSessionId = UUID.randomUUID().toString();
        return resolveClientId(state).flatMap(clientId -> {
            SessionData sessionData = new SessionData(userId, null, System.currentTimeMillis(), clientId);
            return cacheService.saveSession(newSessionId, sessionData, policy.getSessionLifetimeSeconds())
                .flatMap(v -> {
                    if (state == null) {
                        return Uni.createFrom().voidItem();
                    }
                    return cacheService.getAuthorizationRequestState(state).flatMap(requestState -> {
                        if (requestState == null) {
                            return Uni.createFrom().voidItem();
                        }
                        requestState.setUserId(userId);
                        return cacheService.saveAuthorizationRequestState(state, requestState,
                                ttlConfig.requestStateLifetime()).replaceWithVoid();
                    });
                })
                .flatMap(v -> resolvePostLoginRedirect(newSessionId, userId, state, policy));
        });
    }

    /**
     * Ends OAuth browser sessions for the current request: clears all known session cookies
     * and deletes Redis sessions for every authenticated user found in those cookies.
     * When {@code clientId} is set, only sessions created for that OAuth client are removed.
     */
    public Uni<Void> logout(HttpHeaders headers, String clientId) {
        return sessionCookieFactory.extractAllSessionIds(headers).flatMap(cookieSessionIds ->
                collectUserIds(cookieSessionIds).flatMap(userIds -> {
                    if (userIds.isEmpty()) {
                        return deleteSessionIds(cookieSessionIds);
                    }
                    List<Uni<Void>> deletes = userIds.stream()
                            .map(userId -> clientId != null && !clientId.isBlank()
                                    ? cacheService.deleteUserSessionsForClient(userId, clientId)
                                    : cacheService.deleteAllUserSessions(userId))
                            .toList();
                    return Uni.join().all(deletes).andCollectFailures().replaceWithVoid();
                }));
    }

    private Uni<String> resolveClientId(String state) {
        if (state == null) {
            return Uni.createFrom().nullItem();
        }
        return cacheService.getAuthorizationRequestState(state)
                .map(requestState -> requestState != null ? requestState.getClientId() : null);
    }

    private Uni<Set<String>> collectUserIds(List<String> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Uni.createFrom().item(Set.of());
        }
        List<Uni<String>> lookups = sessionIds.stream()
                .map(id -> cacheService.getSession(id).map(session ->
                        session != null ? session.getUserId() : null))
                .toList();
        return Uni.join().all(lookups).andCollectFailures().map(results -> {
            Set<String> userIds = new LinkedHashSet<>();
            for (String userId : results) {
                if (userId != null) {
                    userIds.add(userId);
                }
            }
            return userIds;
        });
    }

    private Uni<Void> deleteSessionIds(List<String> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        List<Uni<Void>> deletes = sessionIds.stream().map(cacheService::deleteSession).toList();
        return Uni.join().all(deletes).andCollectFailures().replaceWithVoid();
    }

    private Uni<Response> resolvePostLoginRedirect(String newSessionId, String userId, String state,
                                                   ClientOAuthPolicy policy) {
        if (state == null) {
            return Uni.createFrom().item(buildLoginRedirect(newSessionId, null, "/", policy));
        }
        return cacheService.getAuthorizationRequestState(state).flatMap(requestState -> {
            if (requestState == null) {
                return Uni.createFrom().item(buildLoginRedirect(newSessionId, state, "/consent", policy));
            }
            return membershipService.isMember(userId, requestState.getClientId()).flatMap(member -> {
                if (!member) {
                    return Uni.createFrom().failure(new OAuthException(
                            OAuthError.ACCESS_DENIED, requestState.getRedirectUri(), state));
                }
                return consentService.getConsent(userId, requestState.getClientId()).flatMap(consent -> {
                    if (consent != null && ScopeUtil.coversScopes(consent.getScopes(), requestState.getScope())) {
                        return authorizationCodeService.issueCodeAndRedirect(userId, requestState, state)
                                .map(response -> Response.seeOther(response.getLocation())
                                        .header("Set-Cookie", sessionCookieFactory.create(newSessionId, policy))
                                        .build());
                    }
                    return Uni.createFrom().item(buildLoginRedirect(newSessionId, state, "/consent", policy));
                });
            });
        });
    }

    private Response buildLoginRedirect(String newSessionId, String state, String targetPath,
                                        ClientOAuthPolicy policy) {
        URI redirectUri = state != null
                ? OAuthRedirectBuilder.build(targetPath, Map.of("state", state))
                : URI.create(targetPath);
        return Response.seeOther(redirectUri)
                .header("Set-Cookie", sessionCookieFactory.create(newSessionId, policy))
                .build();
    }
}
