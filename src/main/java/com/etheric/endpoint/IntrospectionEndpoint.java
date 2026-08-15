package com.etheric.endpoint;

import com.etheric.model.IntrospectionResponse;
import com.etheric.service.CacheService;
import com.etheric.service.ClientAuthService;
import com.etheric.service.JwtService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * OAuth 2.0 Token Introspection Endpoint (RFC 7662) — {@code POST /introspect}.
 */
@Path("/introspect")
public class IntrospectionEndpoint {

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    @Inject
    ClientAuthService clientAuthService;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> introspect(
            @FormParam("token") String token,
            @FormParam("token_type_hint") String tokenTypeHint,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @Context HttpHeaders headers) {

        if (token == null || token.isBlank()) {
            return Uni.createFrom().item(Response.ok(inactive()).build());
        }

        return clientAuthService.authenticateRequired(clientId, clientSecret, headers)
                .flatMap(client -> introspectToken(token, tokenTypeHint))
                .map(body -> Response.ok(body).build());
    }

    private Uni<IntrospectionResponse> introspectToken(String token, String tokenTypeHint) {
        if ("refresh_token".equals(tokenTypeHint)) {
            return lookupRefreshToken(token).flatMap(response -> {
                if (response.isActive()) {
                    return Uni.createFrom().item(response);
                }
                return lookupAccessToken(token);
            });
        }
        return lookupAccessToken(token).flatMap(response -> {
            if (response.isActive()) {
                return Uni.createFrom().item(response);
            }
            return lookupRefreshToken(token);
        }).flatMap(response -> {
            if (response.isActive()) {
                return Uni.createFrom().item(response);
            }
            return introspectJwt(token);
        });
    }

    private Uni<IntrospectionResponse> lookupAccessToken(String token) {
        return cacheService.getAccessToken(token).map(data -> {
            if (data == null) {
                return inactive();
            }
            if (data.getExpiresAt() * 1000 < System.currentTimeMillis()) {
                return inactive();
            }
            return IntrospectionResponse.builder()
                    .active(true)
                    .scope(String.join(" ", data.getScopes()))
                    .clientId(data.getClientId())
                    .sub(data.getUserId())
                    .tokenType("Bearer")
                    .exp(data.getExpiresAt())
                    .iss(jwtService.getIssuer())
                    .aud(data.getClientId())
                    .build();
        });
    }

    private Uni<IntrospectionResponse> lookupRefreshToken(String token) {
        return cacheService.getRefreshToken(token).map(data -> {
            if (data == null) {
                return inactive();
            }
            var jwtOpt = jwtService.parseToken(token);
            if (jwtOpt.isEmpty()) {
                return inactive();
            }
            JsonWebToken jwt = jwtOpt.get();
            Long exp = jwt.getClaim("exp");
            if (exp != null && exp * 1000 < System.currentTimeMillis()) {
                return inactive();
            }
            return IntrospectionResponse.builder()
                    .active(true)
                    .scope(String.join(" ", data.getScopes()))
                    .clientId(data.getClientId())
                    .sub(data.getUserId())
                    .tokenType("refresh_token")
                    .exp(exp)
                    .iat(jwt.getClaim("iat"))
                    .iss(jwtService.getIssuer())
                    .aud(data.getClientId())
                    .build();
        });
    }

    private Uni<IntrospectionResponse> introspectJwt(String token) {
        return Uni.createFrom().item(jwtService.parseToken(token).map(jwt -> {
            Long exp = jwt.getClaim("exp");
            if (exp != null && exp * 1000 < System.currentTimeMillis()) {
                return inactive();
            }
            Object scopesClaim = jwt.getClaim("scopes");
            String scopeStr = scopesClaim instanceof java.util.List<?> list
                    ? String.join(" ", list.stream().map(Object::toString).toList())
                    : null;
            return IntrospectionResponse.builder()
                    .active(true)
                    .scope(scopeStr)
                    .sub(jwt.getSubject())
                    .tokenType("Bearer")
                    .exp(exp)
                    .iat(jwt.getClaim("iat"))
                    .iss(jwt.getIssuer())
                    .aud(jwt.getAudience() != null && !jwt.getAudience().isEmpty()
                            ? jwt.getAudience().iterator().next() : null)
                    .build();
        }).orElse(inactive()));
    }

    private IntrospectionResponse inactive() {
        return IntrospectionResponse.builder().active(false).build();
    }
}
