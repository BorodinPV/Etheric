package com.etheric.endpoint;

import com.etheric.exception.OAuthError;
import com.etheric.exception.OAuthException;
import com.etheric.model.AuthorizationRequestState;
import com.etheric.repository.ClientRepository;
import com.etheric.service.CacheService;
import com.etheric.util.PkceUtil;
import com.etheric.util.SessionCookieFactory;
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

@Path("/authorize")
public class AuthorizationEndpoint {

    private static final Logger LOG = Logger.getLogger(AuthorizationEndpoint.class);
    private static final long REQUEST_STATE_TTL = 600;

    @Inject
    ClientRepository clientRepository;

    @Inject
    CacheService cacheService;

    @GET
    public Response authorize(
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

        var clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty() || !clientOpt.get().isEnabled()) {
            throw new OAuthException(OAuthError.UNAUTHORIZED_CLIENT, redirectUri, state);
        }

        if (!clientRepository.isRedirectUriValid(clientId, redirectUri)) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, redirectUri, state);
        }

        if (scope != null && !scope.isEmpty() && !clientRepository.isScopeValid(clientId, scope)) {
            throw new OAuthException(OAuthError.INVALID_SCOPE, redirectUri, state);
        }

        String resolvedMethod = null;
        if (codeChallenge != null && !codeChallenge.isBlank()) {
            resolvedMethod = (codeChallengeMethod == null || codeChallengeMethod.isBlank())
                    ? PkceUtil.METHOD_S256
                    : codeChallengeMethod;
            if (!PkceUtil.isSupportedMethod(resolvedMethod)) {
                throw new OAuthException(OAuthError.INVALID_REQUEST, redirectUri, state);
            }
        } else if (codeChallengeMethod != null && !codeChallengeMethod.isBlank()) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, redirectUri, state);
        }

        AuthorizationRequestState requestState = new AuthorizationRequestState(
                clientId,
                redirectUri,
                scope,
                state,
                null,
                codeChallenge,
                resolvedMethod,
                nonce
        );
        cacheService.saveAuthorizationRequestState(state, requestState, REQUEST_STATE_TTL);

        String sessionId = extractSessionId(headers);
        if (sessionId != null) {
            var session = cacheService.getSession(sessionId);
            if (session != null && session.getUserId() != null) {
                requestState.setUserId(session.getUserId());
                cacheService.saveAuthorizationRequestState(state, requestState, REQUEST_STATE_TTL);
            }
        }

        if (requestState.getUserId() == null) {
            return Response.seeOther(URI.create("/login?state=" + state)).build();
        }

        return Response.seeOther(URI.create("/consent?state=" + state)).build();
    }

    private String extractSessionId(HttpHeaders headers) {
        String cookie = headers.getHeaderString("Cookie");
        if (cookie != null && cookie.contains(SessionCookieFactory.COOKIE_NAME + "=")) {
            String[] parts = cookie.split(SessionCookieFactory.COOKIE_NAME + "=");
            if (parts.length > 1) {
                String value = parts[1].split(";")[0].trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
