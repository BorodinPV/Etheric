package com.etheric.endpoint;

import com.etheric.config.EthericTtlConfig;
import com.etheric.service.TokenPolicyService;
import com.etheric.model.ClientOAuthPolicy;
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
        return resolveOAuthPolicy(state).flatMap(policy -> {
            String sessionId = sessionCookieFactory.extractSessionId(headers, policy);
            if (sessionId != null) {
                return cacheService.getSession(sessionId).flatMap(session -> {
                    if (session != null) {
                        return renderLoginPage(sessionId, session, state, policy, false);
                    }
                    return createAnonymousSession(state, policy);
                }).onFailure().recoverWithUni(e -> createAnonymousSession(state, policy));
            }
            return createAnonymousSession(state, policy);
        });
    }

    private Uni<Response> createAnonymousSession(String state, ClientOAuthPolicy policy) {
        String sessionId = UUID.randomUUID().toString();
        SessionData session = new SessionData(null, null, System.currentTimeMillis());
        return renderLoginPage(sessionId, session, state, policy, true);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> postLogin(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("state") String state,
            @FormParam("csrf_token") String csrfToken,
            @Context HttpHeaders headers) {

        return resolveOAuthPolicy(state).flatMap(policy -> {
            String sessionId = sessionCookieFactory.extractSessionId(headers, policy);
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
                        return renderLoginError(sessionId, session, state, policy);
                    }
                    return handleSuccessfulLogin(userOpt.get().id.toString(), state, policy);
                });
            });
        });
    }

    private Uni<Response> renderLoginError(String sessionId, SessionData session, String state,
                                           ClientOAuthPolicy policy) {
        String newCsrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(newCsrfToken);
        return cacheService.saveSession(sessionId, session, policy.getSessionLifetimeSeconds())
                .replaceWith(buildLoginResponse(sessionId, state, newCsrfToken,
                        "Неверное имя пользователя или пароль", false, policy));
    }

    private Uni<Response> handleSuccessfulLogin(String userId, String state, ClientOAuthPolicy policy) {
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

    private Uni<Response> renderLoginPage(String sessionId, SessionData session, String state,
                                          ClientOAuthPolicy policy, boolean issueCookie) {
        String csrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(csrfToken);
        return cacheService.saveSession(sessionId, session, policy.getSessionLifetimeSeconds())
                .replaceWith(buildLoginResponse(sessionId, state, csrfToken, null, issueCookie, policy));
    }

    private Response buildLoginResponse(String sessionId, String state, String csrfToken,
                                        String error, boolean issueCookie, ClientOAuthPolicy policy) {
        TemplateInstance template = login.instance();
        template.data("error", error);
        template.data("state", state);
        template.data("csrfToken", csrfToken);
        Response.ResponseBuilder response = Response.ok(template.render()).type(MediaType.TEXT_HTML);
        if (issueCookie) {
            response.header("Set-Cookie", sessionCookieFactory.create(sessionId, policy));
        }
        return response.build();
    }

    private Uni<ClientOAuthPolicy> resolveOAuthPolicy(String state) {
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
}
