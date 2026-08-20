package com.etheric.endpoint;

import com.etheric.model.ClientOAuthPolicy;
import com.etheric.model.SessionData;
import com.etheric.repository.UserRepository;
import com.etheric.service.AuthSessionService;
import com.etheric.service.CacheService;
import com.etheric.util.SessionCookieFactory;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

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
    SessionCookieFactory sessionCookieFactory;

    @Inject
    AuthSessionService authSessionService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getLogin(
            @QueryParam("state") String state,
            @QueryParam("registered") String registered,
            @Context HttpHeaders headers) {
        return authSessionService.resolveOAuthPolicy(state).flatMap(policy -> {
            String sessionId = sessionCookieFactory.extractSessionId(headers, policy);
            if (sessionId != null) {
                return cacheService.getSession(sessionId).flatMap(session -> {
                    if (session != null) {
                        return renderLoginPage(sessionId, session, state, registered, policy, false);
                    }
                    return createAnonymousSession(state, registered, policy);
                }).onFailure().recoverWithUni(e -> createAnonymousSession(state, registered, policy));
            }
            return createAnonymousSession(state, registered, policy);
        });
    }

    private Uni<Response> createAnonymousSession(String state, String registered, ClientOAuthPolicy policy) {
        String sessionId = UUID.randomUUID().toString();
        SessionData session = new SessionData(null, null, System.currentTimeMillis());
        return renderLoginPage(sessionId, session, state, registered, policy, true);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> postLogin(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("state") String state,
            @FormParam("csrf_token") String csrfToken,
            @Context HttpHeaders headers) {

        return authSessionService.resolveOAuthPolicy(state).flatMap(policy -> {
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
                    return authSessionService.completeLogin(userOpt.get().id.toString(), state, policy);
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
                        "Неверное имя пользователя или пароль", null, false, policy));
    }

    private Uni<Response> renderLoginPage(String sessionId, SessionData session, String state,
                                          String registered, ClientOAuthPolicy policy, boolean issueCookie) {
        String csrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(csrfToken);
        String success = "1".equals(registered) ? "Аккаунт создан. Войдите." : null;
        return cacheService.saveSession(sessionId, session, policy.getSessionLifetimeSeconds())
                .replaceWith(buildLoginResponse(sessionId, state, csrfToken, null, success, issueCookie, policy));
    }

    private Response buildLoginResponse(String sessionId, String state, String csrfToken,
                                        String error, String success, boolean issueCookie,
                                        ClientOAuthPolicy policy) {
        TemplateInstance template = login.instance();
        template.data("error", error);
        template.data("success", success);
        template.data("state", state);
        template.data("csrfToken", csrfToken);
        Response.ResponseBuilder response = Response.ok(template.render()).type(MediaType.TEXT_HTML);
        if (issueCookie) {
            response.header("Set-Cookie", sessionCookieFactory.create(sessionId, policy));
        }
        return response.build();
    }
}
