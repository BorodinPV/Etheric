package com.etheric.endpoint;

import com.etheric.config.EthericTtlConfig;
import com.etheric.model.AuthorizationCodeData;
import com.etheric.repository.ClientRepository;
import com.etheric.service.CacheService;
import com.etheric.service.JwtService;
import com.etheric.util.OAuthRedirectBuilder;
import com.etheric.util.SessionCookieFactory;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

/**
 * Consent screen for the authorization flow ({@code GET|POST /consent}).
 * <p>
 * GET: {@code state} query param; returns HTML consent page (requires session).
 * POST form: {@code action=approve|deny}, {@code state}, {@code csrf_token}.
 * Approve: {@code 302} redirect to client {@code redirect_uri} with authorization {@code code}.
 * Deny: {@code 302} with {@code error=access_denied}. Invalid session/state: {@code 400}/{@code 403}.
 */
@Path("/consent")
public class ConsentEndpoint {

    @Inject
    Template consent;

    @Inject
    ClientRepository clientRepository;

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    @Inject
    EthericTtlConfig ttlConfig;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getConsent(@QueryParam("state") String state, @Context HttpHeaders headers) {
        if (state == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }

        String sessionId = SessionCookieFactory.extractSessionId(headers);
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
                    String csrfToken = UUID.randomUUID().toString();
                    session.setCsrfToken(csrfToken);
                    return cacheService.saveSession(sessionId, session, ttlConfig.sessionLifetime())
                            .replaceWith(renderConsent(clientOpt.get(), requestState, state, csrfToken));
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

        String sessionId = SessionCookieFactory.extractSessionId(headers);
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

    private Uni<Response> handleApprove(com.etheric.model.SessionData session,
                                        com.etheric.model.AuthorizationRequestState requestState, String state) {
        String code = jwtService.generateAuthorizationCode();
        return cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
                        requestState.getClientId(), session.getUserId(), requestState.getRedirectUri(),
                        requestState.getScope(), requestState.getCodeChallenge(),
                        requestState.getCodeChallengeMethod(), requestState.getNonce()),
                ttlConfig.authorizationCodeLifetime())
                .flatMap(v -> cacheService.deleteAuthorizationRequestState(state))
                .replaceWith(Response.seeOther(OAuthRedirectBuilder.authorizationSuccess(
                        requestState.getRedirectUri(), code, requestState.getState())).build());
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
