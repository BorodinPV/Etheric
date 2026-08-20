package com.etheric.endpoint;

import com.etheric.config.EthericRegistrationConfig;
import com.etheric.model.ClientOAuthPolicy;
import com.etheric.model.SessionData;
import com.etheric.service.AuthSessionService;
import com.etheric.service.CacheService;
import com.etheric.service.RegistrationService;
import com.etheric.util.OAuthRedirectBuilder;
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
 * Self-service user registration ({@code GET|POST /register}).
 */
@Path("/register")
public class RegistrationEndpoint {

    @Inject
    Template register;

    @Inject
    RegistrationService registrationService;

    @Inject
    AuthSessionService authSessionService;

    @Inject
    CacheService cacheService;

    @Inject
    SessionCookieFactory sessionCookieFactory;

    @Inject
    EthericRegistrationConfig registrationConfig;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getRegister(
            @QueryParam("state") String state,
            @QueryParam("client_id") String clientId,
            @QueryParam("return_uri") String returnUri,
            @Context HttpHeaders headers) {
        if (!registrationConfig.enabled()) {
            return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND).build());
        }
        return authSessionService.resolveOAuthPolicy(state).flatMap(policy -> {
            String sessionId = sessionCookieFactory.extractSessionId(headers, policy);
            if (sessionId != null) {
                return cacheService.getSession(sessionId).flatMap(session -> {
                    if (session != null) {
                        return renderRegisterPage(sessionId, session, state, clientId, returnUri, policy, false);
                    }
                    return createAnonymousSession(state, clientId, returnUri, policy);
                }).onFailure().recoverWithUni(e -> createAnonymousSession(state, clientId, returnUri, policy));
            }
            return createAnonymousSession(state, clientId, returnUri, policy);
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> postRegister(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("email") String email,
            @FormParam("state") String state,
            @FormParam("client_id") String clientId,
            @FormParam("return_uri") String returnUri,
            @FormParam("csrf_token") String csrfToken,
            @Context HttpHeaders headers) {
        if (!registrationConfig.enabled()) {
            return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND).build());
        }

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
                return registrationService.register(username, password, email, state, clientId)
                        .flatMap(result -> {
                            if (!result.success()) {
                                return renderRegisterError(sessionId, session, state, clientId, returnUri,
                                        policy, result.errorMessage());
                            }
                            if (state != null && !state.isBlank()) {
                                return authSessionService.completeLogin(result.userId(), state, policy);
                            }
                            return redirectAfterStandaloneRegistration(result.userId(), clientId, returnUri, policy);
                        });
            });
        });
    }

    private Uni<Response> redirectAfterStandaloneRegistration(String userId, String clientId, String returnUri,
                                                              ClientOAuthPolicy policy) {
        if (returnUri != null && !returnUri.isBlank() && clientId != null && !clientId.isBlank()) {
            return registrationService.isReturnUriAllowed(clientId, returnUri).flatMap(allowed -> {
                if (allowed) {
                    URI target = OAuthRedirectBuilder.build(returnUri.trim(), Map.of("registered", "1"));
                    return Uni.createFrom().item(Response.seeOther(target).build());
                }
                return redirectToLoginAfterRegistration();
            });
        }
        return redirectToLoginAfterRegistration();
    }

    private Uni<Response> redirectToLoginAfterRegistration() {
        URI target = OAuthRedirectBuilder.build("/login", Map.of("registered", "1"));
        return Uni.createFrom().item(Response.seeOther(target).build());
    }

    private Uni<Response> createAnonymousSession(String state, String clientId, String returnUri,
                                                 ClientOAuthPolicy policy) {
        String sessionId = UUID.randomUUID().toString();
        SessionData session = new SessionData(null, null, System.currentTimeMillis());
        return renderRegisterPage(sessionId, session, state, clientId, returnUri, policy, true);
    }

    private Uni<Response> renderRegisterError(String sessionId, SessionData session, String state,
                                              String clientId, String returnUri, ClientOAuthPolicy policy,
                                              String error) {
        String newCsrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(newCsrfToken);
        return cacheService.saveSession(sessionId, session, policy.getSessionLifetimeSeconds())
                .replaceWith(buildRegisterResponse(sessionId, state, clientId, returnUri, newCsrfToken,
                        error, false, policy));
    }

    private Uni<Response> renderRegisterPage(String sessionId, SessionData session, String state,
                                             String clientId, String returnUri, ClientOAuthPolicy policy,
                                             boolean issueCookie) {
        String csrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(csrfToken);
        return cacheService.saveSession(sessionId, session, policy.getSessionLifetimeSeconds())
                .replaceWith(buildRegisterResponse(sessionId, state, clientId, returnUri, csrfToken,
                        null, issueCookie, policy));
    }

    private Response buildRegisterResponse(String sessionId, String state, String clientId, String returnUri,
                                           String csrfToken, String error, boolean issueCookie,
                                           ClientOAuthPolicy policy) {
        TemplateInstance template = register.instance();
        template.data("error", error);
        template.data("state", state);
        template.data("clientId", clientId);
        template.data("returnUri", returnUri);
        template.data("csrfToken", csrfToken);
        Response.ResponseBuilder response = Response.ok(template.render()).type(MediaType.TEXT_HTML);
        if (issueCookie) {
            response.header("Set-Cookie", sessionCookieFactory.create(sessionId, policy));
        }
        return response.build();
    }
}
