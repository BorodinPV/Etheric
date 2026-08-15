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
import com.etheric.service.CacheService;
import com.etheric.service.JwtService;
import com.etheric.service.PasswordService;
import com.etheric.util.PkceUtil;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

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
    PasswordService passwordService;

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
            @FormParam("scope") List<String> scope) {

        if (grantType == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }

        return switch (grantType) {
            case "authorization_code" ->
                    handleAuthorizationCode(code, redirectUri, clientId, clientSecret, codeVerifier, scope);
            case "refresh_token" ->
                    handleRefreshToken(refreshToken, clientId, clientSecret, scope);
            default -> Uni.createFrom().failure(new OAuthException(OAuthError.UNSUPPORTED_GRANT_TYPE, null, null));
        };
    }

    private Uni<Response> handleAuthorizationCode(String code, String redirectUri, String clientId,
                                                   String clientSecret, String codeVerifier, List<String> scope) {
        if (code == null || redirectUri == null || clientId == null || clientSecret == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }

        return authenticateClient(clientId, clientSecret)
                .flatMap(client -> clientRepository.isGrantTypeSupported(clientId, "authorization_code"))
                .flatMap(supported -> {
                    if (!supported) {
                        return Uni.createFrom().failure(new OAuthException(OAuthError.UNAUTHORIZED_CLIENT, null, null));
                    }
                    return cacheService.getAuthorizationCode(code);
                })
                .flatMap(codeData -> validateAuthorizationCode(codeData, code, redirectUri, clientId, codeVerifier))
                .flatMap(codeData -> cacheService.deleteAuthorizationCode(code).replaceWith(codeData))
                .flatMap(codeData -> {
                    List<String> grantedScopes = scope != null && !scope.isEmpty() ? scope : codeData.getScopes();
                    return issueTokenResponse(codeData.getUserId(), clientId, grantedScopes);
                });
    }

    private Uni<AuthorizationCodeData> validateAuthorizationCode(AuthorizationCodeData codeData, String code,
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
                                             String clientSecret, List<String> scope) {
        if (refreshToken == null || clientId == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }

        Uni<Client> authUni = clientSecret != null && !clientSecret.isBlank()
                ? authenticateClient(clientId, clientSecret)
                : clientRepository.findByClientId(clientId).flatMap(opt -> {
                    if (opt.isEmpty() || !opt.get().enabled) {
                        return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
                    }
                    return Uni.createFrom().item(opt.get());
                });

        return authUni
                .flatMap(client -> clientRepository.isGrantTypeSupported(clientId, "refresh_token"))
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
                    if (!clientId.equals(refreshTokenData.getClientId())) {
                        return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_GRANT, null, null));
                    }
                    List<String> grantedScopes = scope != null && !scope.isEmpty() ? scope : refreshTokenData.getScopes();
                    return cacheService.deleteRefreshToken(refreshToken)
                            .flatMap(v -> issueTokenResponse(refreshTokenData.getUserId(), clientId, grantedScopes));
                });
    }

    private Uni<Response> issueTokenResponse(String userId, String clientId, List<String> grantedScopes) {
        long accessTtl = ttlConfig.accessTokenLifetime();
        long refreshTtl = ttlConfig.refreshTokenLifetime();

        return resolveRoles(userId).flatMap(roles -> {
            String accessToken = jwtService.generateAccessToken(userId, roles, grantedScopes);
            String refreshToken = jwtService.generateRefreshToken(userId, roles, grantedScopes);

            return cacheService.saveAccessToken(accessToken, new AccessTokenData(
                            userId, clientId, grantedScopes, System.currentTimeMillis() / 1000 + accessTtl), accessTtl)
                    .flatMap(v -> cacheService.saveRefreshToken(refreshToken,
                            new RefreshTokenData(userId, clientId, grantedScopes), refreshTtl))
                    .replaceWith(Response.ok(new TokenResponse(
                            accessToken, "Bearer", accessTtl, refreshToken,
                            String.join(" ", grantedScopes), null)).build());
        });
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

    private Uni<Client> authenticateClient(String clientId, String clientSecret) {
        return clientRepository.findByClientId(clientId).flatMap(opt -> {
            if (opt.isEmpty() || !opt.get().enabled) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
            }
            Client client = opt.get();
            if (!passwordService.verifyPassword(clientSecret, client.clientSecretHash)) {
                return Uni.createFrom().failure(new OAuthException(OAuthError.INVALID_CLIENT, 401));
            }
            return Uni.createFrom().item(client);
        });
    }
}
