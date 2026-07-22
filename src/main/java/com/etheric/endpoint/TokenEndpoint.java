package com.etheric.endpoint;

import com.etheric.exception.OAuthError;
import com.etheric.exception.OAuthException;
import com.etheric.model.AccessTokenData;
import com.etheric.model.AuthorizationCodeData;
import com.etheric.model.RefreshTokenData;
import com.etheric.model.TokenResponse;
import com.etheric.service.CacheService;
import com.etheric.service.JwtService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;

@Path("/token")
public class TokenEndpoint {

    private static final Logger LOG = Logger.getLogger(TokenEndpoint.class);

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(
            @FormParam("grant_type") String grantType,
            @FormParam("code") String code,
            @FormParam("redirect_uri") String redirectUri,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("refresh_token") String refreshToken,
            @FormParam("scope") List<String> scope) {

        if (grantType == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }

        switch (grantType) {
            case "authorization_code":
                return handleAuthorizationCode(code, redirectUri, clientId, clientSecret, scope);
            case "refresh_token":
                return handleRefreshToken(refreshToken, clientId, clientSecret, scope);
            default:
                throw new OAuthException(OAuthError.UNSUPPORTED_GRANT_TYPE, null, null);
        }
    }

    private Response handleAuthorizationCode(String code, String redirectUri, String clientId,
                                            String clientSecret, List<String> scope) {
        if (code == null || redirectUri == null || clientId == null || clientSecret == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }

        AuthorizationCodeData codeData = cacheService.getAuthorizationCode(code);
        if (codeData == null) {
            throw new OAuthException(OAuthError.INVALID_GRANT, null, null);
        }

        cacheService.deleteAuthorizationCode(code);

        if (!codeData.getRedirectUri().equals(redirectUri)) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }

        List<String> grantedScopes = scope != null && !scope.isEmpty() ? scope : codeData.getScopes();
        List<String> roles = List.of("user");
        String accessToken = jwtService.generateAccessToken(codeData.getUserId(), roles, grantedScopes);
        String newRefreshToken = jwtService.generateRefreshToken(codeData.getUserId(), roles, grantedScopes);

        cacheService.saveAccessToken(accessToken, new AccessTokenData(
                codeData.getUserId(),
                clientId,
                grantedScopes,
                System.currentTimeMillis() / 1000 + 3600
        ), 3600);
        cacheService.saveRefreshToken(newRefreshToken, new RefreshTokenData(
                codeData.getUserId(),
                clientId,
                grantedScopes
        ), 604800);

        return Response.ok(new TokenResponse(
                accessToken,
                "Bearer",
                3600L,
                newRefreshToken,
                String.join(" ", grantedScopes)
        )).build();
    }

    private Response handleRefreshToken(String refreshToken, String clientId,
                                        String clientSecret, List<String> scope) {
        if (refreshToken == null || clientId == null) {
            throw new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        }

        RefreshTokenData refreshTokenData = cacheService.getRefreshToken(refreshToken);
        if (refreshTokenData == null) {
            throw new OAuthException(OAuthError.INVALID_GRANT, null, null);
        }

        cacheService.deleteRefreshToken(refreshToken);

        List<String> grantedScopes = scope != null && !scope.isEmpty() ? scope : refreshTokenData.getScopes();
        List<String> roles = List.of("user");
        String newAccessToken = jwtService.generateAccessToken(refreshTokenData.getUserId(), roles, grantedScopes);
        String newRefreshToken = jwtService.generateRefreshToken(refreshTokenData.getUserId(), roles, grantedScopes);

        cacheService.saveAccessToken(newAccessToken, new AccessTokenData(
                refreshTokenData.getUserId(),
                clientId,
                grantedScopes,
                System.currentTimeMillis() / 1000 + 3600
        ), 3600);
        cacheService.saveRefreshToken(newRefreshToken, new RefreshTokenData(
                refreshTokenData.getUserId(),
                clientId,
                grantedScopes
        ), 604800);

        return Response.ok(new TokenResponse(
                newAccessToken,
                "Bearer",
                3600L,
                newRefreshToken,
                String.join(" ", grantedScopes)
        )).build();
    }
}
