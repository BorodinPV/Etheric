package com.etheric.endpoint;

import com.etheric.config.EthericTtlConfig;
import com.etheric.service.TokenPolicyService;
import com.etheric.model.SessionData;
import com.etheric.repository.UserRepository;
import com.etheric.exception.OAuthError;
import com.etheric.exception.OAuthException;
import com.etheric.service.AuthorizationCodeService;
import com.etheric.service.UserClientMembershipService;
import com.etheric.service.CacheService;
import com.etheric.util.OAuthRedirectBuilder;
import com.etheric.util.ScopeUtil;
import com.etheric.util.SessionCookieFactory;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Login page for the authorization flow ({@code GET|POST /login}).
 */
@Path("/login")
public class LoginEndpoint {

    @Inject
    Template login;

    @Inject
    UserRepository userRepository;

    @Inject
    CacheService cacheService;

    @Inject
    AuthorizationCodeService authorizationCodeService;

    @Inject
    SessionCookieFactory sessionCookieFactory;

    @Inject
    TokenPolicyService tokenPolicyService;

    @Inject
    EthericTtlConfig ttlConfig;

    @Inject
    UserClientMembershipService membershipService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getLogin(@QueryParam("state") String state, @Context HttpHeaders headers) {
        String sessionId = sessionCookieFactory.extractSessionId(headers);
        if (sessionId != null) {
            return cacheService.getSession(sessionId).flatMap(session -> {
                if (session != null) {
                    return renderLoginPage(sessionId, session, state, false);
                }
                return createAnonymousSession(state);
            }).onFailure().recoverWithUni(e -> createAnonymousSession(state));
        }
        return createAnonymousSession(state);
    }

    private Uni<Response> createAnonymousSession(String state) {
        String sessionId = UUID.randomUUID().toString();
        SessionData session = new SessionData(null, null, System.currentTimeMillis());
        return renderLoginPage(sessionId, session, state, true);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> postLogin(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("state") String state,
            @FormParam("csrf_token") String csrfToken,
            @Context HttpHeaders headers) {

        String sessionId = sessionCookieFactory.extractSessionId(headers);
        if (sessionId == null) {
            return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                    .entity("Invalid CSRF token").build());
        }

        return cacheService.getSession(sessionId).flatMap(session -> {
            if (session == null || csrfToken == null || !csrfToken.equals(session.getCsrfToken())) {
                return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                        .entity("Invalid CSRF token").build());
            }
            return userRepository.authenticate(username, password).flatMap(userOpt -> {
                if (userOpt.isEmpty()) {
                    return renderLoginError(sessionId, session, state);
                }
                return handleSuccessfulLogin(userOpt.get().id.toString(), state);
            });
        });
    }

    private Uni<Response> renderLoginError(String sessionId, SessionData session, String state) {
        String newCsrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(newCsrfToken);
        return resolveSessionLifetime(state).flatMap(sessionLifetime ->
                cacheService.saveSession(sessionId, session, sessionLifetime)
                        .replaceWith(buildLoginResponse(sessionId, state, newCsrfToken,
                                "Неверное имя пользователя или пароль", false)));
    }

    private Uni<Response> handleSuccessfulLogin(String userId, String state) {
        String newSessionId = UUID.randomUUID().toString();
        return resolveSessionLifetime(state).flatMap(sessionLifetime ->
                cacheService.saveSession(newSessionId,
                                new SessionData(userId, null, System.currentTimeMillis()), sessionLifetime)
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
                        .flatMap(v -> resolvePostLoginRedirect(newSessionId, userId, state)));
    }

    private Uni<Long> resolveSessionLifetime(String state) {
        if (state == null) {
            return Uni.createFrom().item(tokenPolicyService.oauthSessionLifetimeSeconds());
        }
        return cacheService.getAuthorizationRequestState(state)
                .flatMap(requestState -> {
                    if (requestState == null) {
                        return Uni.createFrom().item(tokenPolicyService.oauthSessionLifetimeSeconds());
                    }
                    return tokenPolicyService.resolveSessionLifetimeForClient(requestState.getClientId());
                });
    }

    private Uni<Response> resolvePostLoginRedirect(String newSessionId, String userId, String state) {
        if (state == null) {
            return Uni.createFrom().item(buildLoginRedirect(newSessionId, null, "/"));
        }
        return cacheService.getAuthorizationRequestState(state).flatMap(requestState -> {
            if (requestState == null) {
                return Uni.createFrom().item(buildLoginRedirect(newSessionId, state, "/consent"));
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
                                        .header("Set-Cookie", sessionCookieFactory.create(newSessionId))
                                        .build());
                    }
                    return Uni.createFrom().item(buildLoginRedirect(newSessionId, state, "/consent"));
                });
            });
        });
    }

    private Response buildLoginRedirect(String newSessionId, String state, String targetPath) {
        URI redirectUri = state != null
                ? OAuthRedirectBuilder.build(targetPath, Map.of("state", state))
                : URI.create(targetPath);
        return Response.seeOther(redirectUri)
                .header("Set-Cookie", sessionCookieFactory.create(newSessionId))
                .build();
    }

    private Uni<Response> renderLoginPage(String sessionId, SessionData session, String state, boolean issueCookie) {
        String csrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(csrfToken);
        return resolveSessionLifetime(state).flatMap(sessionLifetime ->
                cacheService.saveSession(sessionId, session, sessionLifetime)
                        .replaceWith(buildLoginResponse(sessionId, state, csrfToken, null, issueCookie)));
    }

    private Response buildLoginResponse(String sessionId, String state, String csrfToken,
                                        String error, boolean issueCookie) {
        TemplateInstance template = login.instance();
        template.data("error", error);
        template.data("state", state);
        template.data("csrfToken", csrfToken);
        Response.ResponseBuilder response = Response.ok(template.render()).type(MediaType.TEXT_HTML);
        if (issueCookie) {
            response.header("Set-Cookie", sessionCookieFactory.create(sessionId));
        }
        return response.build();
    }
}
