package com.etheric.endpoint;

import com.etheric.entity.Client;
import com.etheric.logging.SecurityAuditLogger;
import com.etheric.exception.OAuthError;
import com.etheric.exception.OAuthException;
import com.etheric.model.AuthorizationRequestState;
import com.etheric.model.ClientOAuthPolicy;
import com.etheric.model.SessionData;
import com.etheric.repository.ClientRepository;
import com.etheric.service.AuthorizationCodeService;
import com.etheric.service.CacheService;
import com.etheric.service.ConsentService;
import com.etheric.service.TokenPolicyService;
import com.etheric.service.UserClientMembershipService;
import com.etheric.util.OAuthRedirectBuilder;
import com.etheric.util.SessionCookieFactory;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Consent screen for the authorization flow ({@code GET|POST /consent}).
 */
@Path("/consent")
public class ConsentEndpoint {

    private static final String STATE_PARAM = "state";
    private static final String LOGIN_PATH = "/login";

    private final Template consent;
    private final ClientRepository clientRepository;
    private final CacheService cacheService;
    private final ConsentService consentService;
    private final AuthorizationCodeService authorizationCodeService;
    private final SessionCookieFactory sessionCookieFactory;
    private final TokenPolicyService tokenPolicyService;
    private final UserClientMembershipService membershipService;
    private final SecurityAuditLogger securityAuditLogger;

    public ConsentEndpoint(@Location("consent") Template consent, OAuthFlowSupport flow) {
        this.consent = consent;
        this.clientRepository = flow.clients();
        this.cacheService = flow.cache();
        this.consentService = flow.consent();
        this.authorizationCodeService = flow.codes();
        this.sessionCookieFactory = flow.cookies();
        this.tokenPolicyService = flow.tokenPolicy();
        this.membershipService = flow.membership();
        this.securityAuditLogger = flow.audit();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getConsent(@QueryParam(STATE_PARAM) String state, @Context HttpHeaders headers) {
        if (state == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }

        return cacheService.getAuthorizationRequestState(state).flatMap(requestState -> {
            if (requestState == null) {
                return invalidOrExpiredRequest();
            }
            return tokenPolicyService.resolveOAuthPolicyForClient(requestState.getClientId())
                    .flatMap(policy -> {
                        String sessionId = sessionCookieFactory.extractSessionId(headers, policy);
                        return requireSession(sessionId, state,
                                session -> loadConsentPage(session, sessionId, requestState, state, policy));
                    });
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> postConsent(
            @FormParam("action") String action,
            @FormParam(STATE_PARAM) String state,
            @FormParam("csrf_token") String csrfToken,
            @Context HttpHeaders headers) {

        if (state == null || action == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }

        return cacheService.getAuthorizationRequestState(state).flatMap(requestState -> {
            if (requestState == null) {
                return invalidOrExpiredRequest();
            }
            return tokenPolicyService.resolveOAuthPolicyForClient(requestState.getClientId())
                    .flatMap(policy -> requireSession(
                            sessionCookieFactory.extractSessionId(headers, policy),
                            state,
                            session -> processConsentAction(action, csrfToken, session, requestState, state)));
        });
    }

    private Uni<Response> loadConsentPage(SessionData session, String sessionId,
                                          AuthorizationRequestState requestState,
                                          String state, ClientOAuthPolicy policy) {
        return clientRepository.findByClientId(requestState.getClientId()).flatMap(clientOpt -> {
            if (clientOpt.isEmpty()) {
                return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                        .entity("Client not found").build());
            }
            return membershipService.isMember(session.getUserId(), requestState.getClientId())
                    .flatMap(member -> {
                        if (!Boolean.TRUE.equals(member)) {
                            return accessDenied(requestState, state);
                        }
                        String csrfToken = UUID.randomUUID().toString();
                        session.setCsrfToken(csrfToken);
                        return cacheService.saveSession(sessionId, session, policy.getSessionLifetimeSeconds())
                                .replaceWith(renderConsent(clientOpt.get(), requestState, state, csrfToken));
                    });
        });
    }

    private Uni<Response> processConsentAction(String action, String csrfToken, SessionData session,
                                               AuthorizationRequestState requestState, String state) {
        if (csrfToken == null || !csrfToken.equals(session.getCsrfToken())) {
            return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                    .entity("Invalid CSRF token").build());
        }
        if ("approve".equals(action)) {
            return handleApprove(session, requestState, state);
        }
        return handleDeny(session, requestState, state);
    }

    private Uni<Response> handleApprove(SessionData session, AuthorizationRequestState requestState, String state) {
        return membershipService.isMember(session.getUserId(), requestState.getClientId()).flatMap(member -> {
            if (!Boolean.TRUE.equals(member)) {
                return accessDenied(requestState, state);
            }
            return consentService.saveConsent(session.getUserId(), requestState.getClientId(), requestState.getScope())
                    .flatMap(v -> authorizationCodeService.issueCodeAndRedirect(session.getUserId(), requestState, state));
        });
    }

    private Uni<Response> handleDeny(SessionData session, AuthorizationRequestState requestState, String state) {
        securityAuditLogger.consentDenied(session.getUserId(), requestState.getClientId());
        return cacheService.deleteAuthorizationRequestState(state)
                .replaceWith(Response.seeOther(OAuthRedirectBuilder.accessDenied(
                        requestState.getRedirectUri(), requestState.getState())).build());
    }

    private Uni<Response> requireSession(String sessionId, String state,
                                         Function<SessionData, Uni<Response>> onSession) {
        if (sessionId == null) {
            return Uni.createFrom().item(redirectToLogin(state));
        }
        return cacheService.getSession(sessionId).flatMap(session -> {
            if (session == null) {
                return Uni.createFrom().item(redirectToLogin(state));
            }
            return onSession.apply(session);
        });
    }

    private Uni<Response> invalidOrExpiredRequest() {
        return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity("Invalid or expired authorization request").build());
    }

    private Uni<Response> accessDenied(AuthorizationRequestState requestState, String state) {
        return Uni.createFrom().failure(new OAuthException(
                OAuthError.ACCESS_DENIED, requestState.getRedirectUri(), state));
    }

    private Response redirectToLogin(String state) {
        return Response.seeOther(OAuthRedirectBuilder.build(LOGIN_PATH, Map.of(STATE_PARAM, state))).build();
    }

    private Response renderConsent(Client client, AuthorizationRequestState requestState,
                                   String state, String csrfToken) {
        TemplateInstance template = consent.instance();
        template.data("client", client);
        template.data("scopes", requestState.getScope());
        template.data(STATE_PARAM, state);
        template.data("csrfToken", csrfToken);
        return Response.ok(template.render()).type(MediaType.TEXT_HTML).build();
    }
}
