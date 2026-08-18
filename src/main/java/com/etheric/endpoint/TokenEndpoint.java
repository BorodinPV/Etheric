package com.etheric.endpoint;

import com.etheric.config.EthericTtlConfig;
import com.etheric.entity.Client;
import com.etheric.exception.OAuthError;
import com.etheric.exception.OAuthException;
import com.etheric.model.AccessTokenData;
import com.etheric.model.AuthorizationCodeData;
import com.etheric.model.RefreshTokenData;
import com.etheric.model.TokenResponse;
import com.etheric.repository.ClientRepository;
import com.etheric.repository.UserRepository;
import com.etheric.service.AuthorizationCodeService;
import com.etheric.service.CacheService;
import com.etheric.service.ClientAuthService;
import com.etheric.service.JwtService;
import com.etheric.util.PkceUtil;
import com.etheric.util.ScopeUtil;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * OAuth 2.0 Token Endpoint ({@code POST /token}).
 */
@Path("/token")
public class TokenEndpoint {

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    @Inject
    ClientRepository clientRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    ClientAuthService clientAuthService;

    @Inject
    AuthorizationCodeService authorizationCodeService;

    @Inject
    EthericTtlConfig ttlConfig;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> token(
            @FormParam("grant_type") String grantType,
            @FormParam("code") String code,
            @FormParam("redirect_uri") String redirectUri,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("code_verifier") String codeVerifier,
            @FormParam("refresh_token") String refreshToken,
            @FormParam("scope") List<String> scope,
            @Context HttpHeaders headers) {

        if (grantType == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }

        return switch (grantType) {
            case "authorization_code" ->
                    handleAuthorizationCode(code, redirectUri, clientId, clientSecret, codeVerifier, scope, headers);
            case "refresh_token" ->
                    handleRefreshToken(refreshToken, clientId, clientSecret, scope, headers);
            default -> Uni.createFrom().failure(new OAuthException(OAuthError.UNSUPPORTED_GRANT_TYPE, null, null));
        };
    }

    private Uni<Response> handleAuthorizationCode(String code, String redirectUri, String clientId,
                                                   String clientSecret, String codeVerifier,
                                                   List<String> scope, HttpHeaders headers) {
        ClientAuthService.ClientCredentials creds =
                clientAuthService.resolveCredentials(clientId, clientSecret, headers);
        if (code == null || redirectUri == null || creds.clientId() == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }
        String resolvedClientId = creds.clientId();

        return cacheService.getAuthorizationCode(code)
                .flatMap(codeData -> {
                    Uni<Client> authUni = hasPkceChallenge(codeData)
                            ? clientAuthService.authenticateOptionalSecret(clientId, clientSecret, headers)
                            : clientAuthService.authenticateRequired(clientId, clientSecret, headers);
                    return authUni
                            .flatMap(client -> clientRepository.isGrantTypeSupported(resolvedClientId, "authorization_code"))
                            .flatMap(supported -> {
                                if (!supported) {
                                    return Uni.createFrom().failure(new OAuthException(OAuthError.UNAUTHORIZED_CLIENT, null, null));
                                }
                                return Uni.createFrom().item(codeData);
                            });
                })
                .flatMap(codeData -> validateAuthorizationCode(codeData, redirectUri, resolvedClientId, codeVerifier))
                .flatMap(codeData -> {
                    List<String> grantedScopes = ScopeUtil.resolveGrantedScopes(scope, codeData.getScopes());
                    return issueTokenResponse(codeData.getUserId(), resolvedClientId, grantedScopes, codeData.getNonce())
                            .flatMap(response -> authorizationCodeService.markCodeUsed(code).replaceWith(response));
                });
    }

    private static boolean hasPkceChallenge(AuthorizationCodeData codeData) {
        return codeData != null
                && codeData.getCodeChallenge() != null
                && !codeData.getCodeChallenge().isBlank();
    }

    private Uni<AuthorizationCodeData> validateAuthorizationCode(AuthorizationCodeData codeData,
                                                                   String redirectUri, String clientId,
                                                                   String codeVerifier) {
        if (codeData == null) {
            return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_GRANT, null, null));
        }
        if (!clientId.equals(codeData.getClientId())) {
            return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_GRANT, null, null));
        }
        if (!PkceUtil.verify(codeVerifier, codeData.getCodeChallenge(), codeData.getCodeChallengeMethod())) {
            return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_GRANT, null, null));
        }
        if (!codeData.getRedirectUri().equals(redirectUri)) {
            return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_GRANT, null, null));
        }
        return Uni.createFrom().item(codeData);
    }

    private Uni<Response> handleRefreshToken(String refreshToken, String clientId,
                                             String clientSecret, List<String> scope,
                                             HttpHeaders headers) {
        ClientAuthService.ClientCredentials creds =
                clientAuthService.resolveCredentials(clientId, clientSecret, headers);
        if (refreshToken == null || creds.clientId() == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }
        String resolvedClientId = creds.clientId();

        return clientAuthService.authenticateOptionalSecret(clientId, clientSecret, headers)
                .flatMap(client -> clientRepository.isGrantTypeSupported(resolvedClientId, "refresh_token"))
                .flatMap(supported -> {
                    if (!supported) {
                        return Uni.createFrom().failure(new OAuthException(OAuthError.UNAUTHORIZED_CLIENT, null, null));
                    }
                    return cacheService.getRefreshToken(refreshToken);
                })
                .flatMap(refreshTokenData -> {
                    if (refreshTokenData == null) {
                        return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_GRANT, null, null));
                    }
                    if (!resolvedClientId.equals(refreshTokenData.getClientId())) {
                        return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_GRANT, null, null));
                    }
                    List<String> grantedScopes = ScopeUtil.resolveGrantedScopes(scope, refreshTokenData.getScopes());
                    return cacheService.deleteRefreshToken(refreshToken)
                            .flatMap(v -> issueTokenResponse(refreshTokenData.getUserId(), resolvedClientId, grantedScopes, null));
                });
    }

    private Uni<Response> issueTokenResponse(String userId, String clientId,
                                             List<String> grantedScopes, String nonce) {
        long accessTtl = ttlConfig.accessTokenLifetime();
        long refreshTtl = ttlConfig.refreshTokenLifetime();
        boolean includeIdToken = grantedScopes.contains("openid");

        return resolveRoles(userId).flatMap(roles -> {
            String accessToken = jwtService.generateAccessToken(userId, roles, grantedScopes);
            String refreshToken = jwtService.generateRefreshToken(userId, roles, grantedScopes);

            Uni<String> idTokenUni = includeIdToken
                    ? resolveIdToken(userId, clientId, nonce, grantedScopes)
                    : Uni.createFrom().nullItem();

            return idTokenUni.flatMap(idToken ->
                    cacheService.saveAccessToken(accessToken, new AccessTokenData(
                                    userId, clientId, grantedScopes, System.currentTimeMillis() / 1000 + accessTtl), accessTtl)
                            .flatMap(v -> cacheService.saveRefreshToken(refreshToken,
                                    new RefreshTokenData(userId, clientId, grantedScopes), refreshTtl))
                            .replaceWith(Response.ok(new TokenResponse(
                                    accessToken, "Bearer", accessTtl, refreshToken,
                                    String.join(" ", grantedScopes), idToken)).build()));
        });
    }

    private Uni<String> resolveIdToken(String userId, String clientId, String nonce, List<String> scopes) {
        try {
            UUID userUuid = UUID.fromString(userId);
            return userRepository.findUserById(userUuid)
                    .map(opt -> jwtService.generateIdToken(
                            userId, clientId, nonce, scopes,
                            opt.map(u -> u.email).orElse(null),
                            opt.map(u -> u.username).orElse(null)));
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(jwtService.generateIdToken(userId, clientId, nonce, scopes, null, null));
        }
    }

    private Uni<List<String>> resolveRoles(String userId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            return userRepository.findUserById(userUuid)
                    .map(opt -> opt.map(u -> u.roles).orElse(List.of("user")));
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(List.of("user"));
        }
    }
}
