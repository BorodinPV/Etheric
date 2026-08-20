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
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.Map;
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
        return cacheService.saveSession(newSessionId,
                        new SessionData(userId, null, System.currentTimeMillis()), policy.getSessionLifetimeSeconds())
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
                return cacheService.getConsent(userId, requestState.getClientId()).flatMap(consent -> {
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
