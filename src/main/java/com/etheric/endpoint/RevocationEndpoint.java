package com.etheric.endpoint;

import com.etheric.service.CacheService;
import com.etheric.service.ClientAuthService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * OAuth 2.0 Token Revocation Endpoint (RFC 7009) — {@code POST /revoke}.
 */
@Path("/revoke")
public class RevocationEndpoint {

    @Inject
    CacheService cacheService;

    @Inject
    ClientAuthService clientAuthService;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> revoke(
            @FormParam("token") String token,
            @FormParam("token_type_hint") String tokenTypeHint,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @Context HttpHeaders headers) {

        return clientAuthService.authenticateRequired(clientId, clientSecret, headers)
                .flatMap(client -> revokeToken(token, tokenTypeHint))
                .replaceWith(Response.ok().build());
    }

    private Uni<Void> revokeToken(String token, String tokenTypeHint) {
        if (token == null || token.isBlank()) {
            return Uni.createFrom().voidItem();
        }
        if ("refresh_token".equals(tokenTypeHint)) {
            return cacheService.deleteRefreshToken(token)
                    .flatMap(v -> cacheService.deleteAccessToken(token))
                    .replaceWithVoid();
        }
        if ("access_token".equals(tokenTypeHint)) {
            return cacheService.deleteAccessToken(token)
                    .flatMap(v -> cacheService.deleteRefreshToken(token))
                    .replaceWithVoid();
        }
        return cacheService.deleteAccessToken(token)
                .flatMap(v -> cacheService.deleteRefreshToken(token))
                .replaceWithVoid();
    }
}
