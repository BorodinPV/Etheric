package com.etheric.endpoint;

import com.etheric.config.EthericRegistrationConfig;
import com.etheric.model.ClientOAuthPolicy;
import com.etheric.model.SessionData;
import com.etheric.service.AuthSessionService;
import com.etheric.service.CacheService;
import com.etheric.service.RegistrationService;
import com.etheric.util.OAuthRedirectBuilder;
import com.etheric.util.SessionCookieFactory;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
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

    private final Template register;
    private final RegistrationService registrationService;
    private final AuthSessionService authSessionService;
    private final CacheService cacheService;
    private final SessionCookieFactory sessionCookieFactory;
    private final EthericRegistrationConfig registrationConfig;

    public RegistrationEndpoint(@Location("register") Template register,
                                RegistrationService registrationService,
                                AuthSessionService authSessionService,
                                CacheService cacheService,
                                SessionCookieFactory sessionCookieFactory,
                                EthericRegistrationConfig registrationConfig) {
        this.register = register;
        this.registrationService = registrationService;
        this.authSessionService = authSessionService;
        this.cacheService = cacheService;
        this.sessionCookieFactory = sessionCookieFactory;
        this.registrationConfig = registrationConfig;
    }

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
    public Uni<Response> postRegister(@BeanParam RegisterFormParams params, @Context HttpHeaders headers) {
        return handleRegisterPost(params.toForm(), headers);
    }

    private Uni<Response> handleRegisterPost(RegisterForm form, HttpHeaders headers) {
        if (!registrationConfig.enabled()) {
            return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND).build());
        }

        return authSessionService.resolveOAuthPolicy(form.state()).flatMap(policy -> {
            String sessionId = sessionCookieFactory.extractSessionId(headers, policy);
            if (sessionId == null) {
                return forbiddenCsrf();
            }
            return cacheService.getSession(sessionId)
                    .flatMap(session -> completeRegister(form, sessionId, session, policy));
        });
    }

    private Uni<Response> completeRegister(RegisterForm form, String sessionId, SessionData session,
                                           ClientOAuthPolicy policy) {
        if (isInvalidCsrf(session, form.csrfToken())) {
            return forbiddenCsrf();
        }
        return registrationService.register(form.username(), form.password(), form.email(),
                        form.state(), form.clientId())
                .flatMap(result -> finishRegistration(form, sessionId, session, policy, result));
    }

    private Uni<Response> finishRegistration(RegisterForm form, String sessionId, SessionData session,
                                             ClientOAuthPolicy policy,
                                             RegistrationService.RegistrationResult result) {
        if (!result.success()) {
            return renderRegisterError(sessionId, session, form, policy, result.errorMessage());
        }
        if (hasText(form.state())) {
            return authSessionService.completeLogin(result.userId(), form.state(), policy);
        }
        return redirectAfterStandaloneRegistration(form.clientId(), form.returnUri());
    }

    private Uni<Response> redirectAfterStandaloneRegistration(String clientId, String returnUri) {
        if (hasText(returnUri) && hasText(clientId)) {
            return registrationService.isReturnUriAllowed(clientId, returnUri).flatMap(allowed -> {
                if (Boolean.TRUE.equals(allowed)) {
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

    private Uni<Response> renderRegisterError(String sessionId, SessionData session, RegisterForm form,
                                              ClientOAuthPolicy policy, String error) {
        String newCsrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(newCsrfToken);
        return cacheService.saveSession(sessionId, session, policy.getSessionLifetimeSeconds())
                .replaceWith(buildRegisterResponse(new RegisterResponseParams(
                        sessionId, form.state(), form.clientId(), form.returnUri(),
                        newCsrfToken, error, false, policy)));
    }

    private Uni<Response> renderRegisterPage(String sessionId, SessionData session, String state,
                                             String clientId, String returnUri, ClientOAuthPolicy policy,
                                             boolean issueCookie) {
        String csrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(csrfToken);
        return cacheService.saveSession(sessionId, session, policy.getSessionLifetimeSeconds())
                .replaceWith(buildRegisterResponse(new RegisterResponseParams(
                        sessionId, state, clientId, returnUri, csrfToken, null, issueCookie, policy)));
    }

    private Response buildRegisterResponse(RegisterResponseParams params) {
        TemplateInstance template = register.instance();
        template.data("error", params.error());
        template.data("state", params.state());
        template.data("clientId", params.clientId());
        template.data("returnUri", params.returnUri());
        template.data("csrfToken", params.csrfToken());
        Response.ResponseBuilder response = Response.ok(template.render()).type(MediaType.TEXT_HTML);
        if (params.issueCookie()) {
            response.header("Set-Cookie", sessionCookieFactory.create(params.sessionId(), params.policy()));
        }
        return response.build();
    }

    private static Uni<Response> forbiddenCsrf() {
        return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                .entity("Invalid CSRF token").build());
    }

    private static boolean isInvalidCsrf(SessionData session, String csrfToken) {
        return session == null || csrfToken == null || !csrfToken.equals(session.getCsrfToken());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class RegisterFormParams {
        @FormParam("username")
        String username;
        @FormParam("password")
        String password;
        @FormParam("email")
        String email;
        @FormParam("state")
        String state;
        @FormParam("client_id")
        String clientId;
        @FormParam("return_uri")
        String returnUri;
        @FormParam("csrf_token")
        String csrfToken;

        private RegisterForm toForm() {
            return new RegisterForm(username, password, email, state, clientId, returnUri, csrfToken);
        }
    }

    private record RegisterForm(String username, String password, String email, String state,
                                String clientId, String returnUri, String csrfToken) {
    }

    private record RegisterResponseParams(String sessionId, String state, String clientId, String returnUri,
                                          String csrfToken, String error, boolean issueCookie,
                                          ClientOAuthPolicy policy) {
    }
}
