package com.etheric.endpoint;

import com.etheric.config.EthericTtlConfig;
import com.etheric.exception.OAuthError;
import com.etheric.exception.OAuthException;
import com.etheric.model.AuthorizationRequestState;
import com.etheric.repository.ClientRepository;
import com.etheric.service.CacheService;
import com.etheric.util.OAuthRedirectBuilder;
import com.etheric.util.PkceUtil;
import com.etheric.util.SessionCookieFactory;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Path("/authorize")
public class AuthorizationEndpoint {

    private static final Logger LOG = Logger.getLogger(AuthorizationEndpoint.class);

    @Inject
    ClientRepository clientRepository;

    @Inject
    CacheService cacheService;

    @Inject
    EthericTtlConfig ttlConfig;

    @GET
    public Uni<Response> authorize(
            @QueryParam("response_type") String responseType,
            @QueryParam("client_id") String clientId,
            @QueryParam("redirect_uri") String redirectUri,
            @QueryParam("state") String state,
            @QueryParam("scope") List<String> scope,
            @QueryParam("code_challenge") String codeChallenge,
            @QueryParam("code_challenge_method") String codeChallengeMethod,
            @QueryParam("nonce") String nonce,
            @Context HttpHeaders headers) {

        LOG.debugf("Authorization request: responseType=%s, clientId=%s, state=%s", responseType, clientId, state);

        if (responseType == null || clientId == null || redirectUri == null || state == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, redirectUri, state);
        }
        if (!"code".equals(responseType)) {
            throw new OAuthException(OAuthError.UNSUPPORTED_RESPONSE_TYPE, redirectUri, state);
        }

        String resolvedMethod = resolvePkceMethod(codeChallenge, codeChallengeMethod, redirectUri, state);

        AuthorizationRequestState requestState = new AuthorizationRequestState(
                clientId, redirectUri, scope, state, null, codeChallenge, resolvedMethod, nonce);

        return clientRepository.findByClientId(clientId).flatMap(clientOpt -> {
            if (clientOpt.isEmpty() || !clientOpt.get().enabled) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.UNAUTHORIZED_CLIENT, redirectUri, state));
            }
            return clientRepository.isRedirectUriValid(clientId, redirectUri);
        }).flatMap(valid -> {
            if (!valid) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_REQUEST, redirectUri, state));
            }
            if (scope != null && !scope.isEmpty()) {
                return clientRepository.isScopeValid(clientId, scope);
            }
            return Uni.createFrom().item(true);
        }).flatMap(scopeValid -> {
            if (!scopeValid) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_SCOPE, redirectUri, state));
            }
            return cacheService.saveAuthorizationRequestState(state, requestState, ttlConfig.requestStateLifetime())
                    .replaceWith(requestState);
        }).flatMap(savedState -> enrichWithSession(savedState, state, headers))
                .map(enriched -> redirectForUser(enriched, state));
    }

    private String resolvePkceMethod(String codeChallenge, String codeChallengeMethod,
                                     String redirectUri, String state) {
        if (codeChallenge != null && !codeChallenge.isBlank()) {
            String method = (codeChallengeMethod == null || codeChallengeMethod.isBlank())
                    ? PkceUtil.METHOD_S256 : codeChallengeMethod;
            if (!PkceUtil.isSupportedMethod(method)) {
                throw new OAuthException(OAuthError.INVALID_REQUEST, redirectUri, state);
            }
            return method;
        }
        if (codeChallengeMethod != null && !codeChallengeMethod.isBlank()) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, redirectUri, state);
        }
        return null;
    }

    private Uni<AuthorizationRequestState> enrichWithSession(AuthorizationRequestState requestState,
                                                             String state, HttpHeaders headers) {
        String sessionId = SessionCookieFactory.extractSessionId(headers);
        if (sessionId == null) {
            return Uni.createFrom().item(requestState);
        }
        return cacheService.getSession(sessionId).flatMap(session -> {
            if (session == null || session.getUserId() == null) {
                return Uni.createFrom().item(requestState);
            }
            requestState.setUserId(session.getUserId());
            return cacheService.saveAuthorizationRequestState(state, requestState, ttlConfig.requestStateLifetime())
                    .replaceWith(requestState);
        });
    }

    private Response redirectForUser(AuthorizationRequestState requestState, String state) {
        if (requestState.getUserId() == null) {
            return Response.seeOther(OAuthRedirectBuilder.build("/login", Map.of("state", state))).build();
        }
        return Response.seeOther(OAuthRedirectBuilder.build("/consent", Map.of("state", state))).build();
    }
}
