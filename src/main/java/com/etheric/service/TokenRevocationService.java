package com.etheric.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

/**
 * RFC 7009 token revocation logic used by {@code POST /revoke}.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class TokenRevocationService {

    private final CacheService cacheService;

    public Uni<Void> revoke(String token, String tokenTypeHint) {
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
