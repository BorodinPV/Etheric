package com.etheric.service;

import com.etheric.model.IntrospectionResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * RFC 7662 token introspection logic used by {@code POST /introspect}.
 */
@ApplicationScoped
public class TokenIntrospectionService {

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    public Uni<IntrospectionResponse> introspect(String token, String tokenTypeHint) {
        if (token == null || token.isBlank()) {
            return Uni.createFrom().item(inactive());
        }
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
            if ("access_token".equals(tokenTypeHint)) {
                return Uni.createFrom().item(inactive());
            }
            return lookupRefreshToken(token);
        }).flatMap(response -> {
            if (response.isActive()) {
                return Uni.createFrom().item(response);
            }
            if ("access_token".equals(tokenTypeHint)) {
                return Uni.createFrom().item(inactive());
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
