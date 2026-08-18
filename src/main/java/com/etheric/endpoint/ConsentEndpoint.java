package com.etheric.endpoint;

import com.etheric.exception.OAuthError;
import com.etheric.exception.OAuthException;
import com.etheric.service.TokenPolicyService;
import com.etheric.service.UserClientMembershipService;
import com.etheric.model.ConsentData;
import com.etheric.model.SessionData;
import com.etheric.repository.UserRepository;
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
 * Consent screen for the authorization flow ({@code GET|POST /consent}).
 */
@Path("/consent")
public class ConsentEndpoint {

    @Inject
    Template consent;

    @Inject
    com.etheric.repository.ClientRepository clientRepository;

    @Inject
    CacheService cacheService;

    @Inject
    com.etheric.service.AuthorizationCodeService authorizationCodeService;

    @Inject
    com.etheric.config.EthericCacheConfig cacheConfig;

    @Inject
    SessionCookieFactory sessionCookieFactory;

    @Inject
    TokenPolicyService tokenPolicyService;

    @Inject
    UserClientMembershipService membershipService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getConsent(@QueryParam("state") String state, @Context HttpHeaders headers) {
        if (state == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }

        String sessionId = sessionCookieFactory.extractSessionId(headers);
        if (sessionId == null) {
            return Uni.createFrom().item(Response.seeOther(
                    OAuthRedirectBuilder.build("/login", Map.of("state", state))).build());
        }

        return cacheService.getSession(sessionId).flatMap(session -> {
            if (session == null) {
                return Uni.createFrom().item(Response.seeOther(
                    OAuthRedirectBuilder.build("/login", Map.of("state", state))).build());
            }
            return cacheService.getAuthorizationRequestState(state).flatMap(requestState -> {
                if (requestState == null) {
                    return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                            .entity("Invalid or expired authorization request").build());
                }
                return clientRepository.findByClientId(requestState.getClientId()).flatMap(clientOpt -> {
                    if (clientOpt.isEmpty()) {
                        return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                                .entity("Client not found").build());
                    }
                    return membershipService.isMember(session.getUserId(), requestState.getClientId())
                            .flatMap(member -> {
                                if (!member) {
                                    return Uni.createFrom().failure(new OAuthException(
                                            OAuthError.ACCESS_DENIED, requestState.getRedirectUri(), state));
                                }
                                String csrfToken = UUID.randomUUID().toString();
                                session.setCsrfToken(csrfToken);
                                return tokenPolicyService.resolveSessionLifetimeForClient(requestState.getClientId())
                                        .flatMap(sessionLifetime -> cacheService.saveSession(sessionId, session, sessionLifetime)
                                                .replaceWith(renderConsent(clientOpt.get(), requestState, state, csrfToken)));
                            });
                });
            });
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> postConsent(
            @FormParam("action") String action,
            @FormParam("state") String state,
            @FormParam("csrf_token") String csrfToken,
            @Context HttpHeaders headers) {

        if (state == null || action == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }

        String sessionId = sessionCookieFactory.extractSessionId(headers);
        if (sessionId == null) {
            return Uni.createFrom().item(Response.seeOther(
                    OAuthRedirectBuilder.build("/login", Map.of("state", state))).build());
        }

        return cacheService.getSession(sessionId).flatMap(session -> {
            if (session == null) {
                return Uni.createFrom().item(Response.seeOther(
                    OAuthRedirectBuilder.build("/login", Map.of("state", state))).build());
            }
            if (csrfToken == null || !csrfToken.equals(session.getCsrfToken())) {
                return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                        .entity("Invalid CSRF token").build());
            }
            return cacheService.getAuthorizationRequestState(state).flatMap(requestState -> {
                if (requestState == null) {
                    return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                            .entity("Invalid or expired authorization request").build());
                }
                if ("approve".equals(action)) {
                    return handleApprove(session, requestState, state);
                }
                return handleDeny(requestState, state);
            });
        });
    }

    private Uni<Response> handleApprove(SessionData session,
                                        com.etheric.model.AuthorizationRequestState requestState, String state) {
        return membershipService.isMember(session.getUserId(), requestState.getClientId()).flatMap(member -> {
            if (!member) {
                return Uni.createFrom().failure(new OAuthException(
                        OAuthError.ACCESS_DENIED, requestState.getRedirectUri(), state));
            }
            ConsentData consent = new ConsentData(requestState.getScope(), System.currentTimeMillis());
            return cacheService.saveConsent(session.getUserId(), requestState.getClientId(), consent,
                            cacheConfig.consentTtlSeconds())
                    .flatMap(v -> authorizationCodeService.issueCodeAndRedirect(session.getUserId(), requestState, state));
        });
    }

    private Uni<Response> handleDeny(com.etheric.model.AuthorizationRequestState requestState, String state) {
        return cacheService.deleteAuthorizationRequestState(state)
                .replaceWith(Response.seeOther(OAuthRedirectBuilder.accessDenied(
                        requestState.getRedirectUri(), requestState.getState())).build());
    }

    private Response renderConsent(com.etheric.entity.Client client,
                                   com.etheric.model.AuthorizationRequestState requestState,
                                   String state, String csrfToken) {
        TemplateInstance template = consent.instance();
        template.data("client", client);
        template.data("scopes", requestState.getScope());
        template.data("state", state);
        template.data("csrfToken", csrfToken);
        return Response.ok(template.render()).type(MediaType.TEXT_HTML).build();
    }
}
