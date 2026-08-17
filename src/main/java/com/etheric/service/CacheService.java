package com.etheric.service;

import com.etheric.model.*;
import com.etheric.util.SecretMasker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Redis-backed cache for OAuth artifacts (codes, tokens, sessions).
 */
@ApplicationScoped
public class CacheService {

    private static final Logger LOG = Logger.getLogger(CacheService.class);
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(50);
    private static final Duration MAX_BACKOFF = Duration.ofMillis(200);

    @Inject
    ReactiveRedisDataSource redis;

    @Inject
    ObjectMapper objectMapper;

    private ReactiveValueCommands<String, String> values;
    private ReactiveKeyCommands<String> keys;

    @PostConstruct
    void init() {
        values = redis.value(String.class);
        keys = redis.key(String.class);
    }

    public Uni<Void> saveAuthorizationCode(String code, AuthorizationCodeData data, long ttlSeconds) {
        return set("auth:code:" + code, data, ttlSeconds);
    }

    public Uni<AuthorizationCodeData> getAuthorizationCode(String code) {
        return get("auth:code:" + code, AuthorizationCodeData.class);
    }

    public Uni<Void> deleteAuthorizationCode(String code) {
        return delete("auth:code:" + code);
    }

    public Uni<Void> saveAccessToken(String token, AccessTokenData data, long ttlSeconds) {
        return set("auth:token:access:" + token, data, ttlSeconds);
    }

    public Uni<AccessTokenData> getAccessToken(String token) {
        return get("auth:token:access:" + token, AccessTokenData.class);
    }

    public Uni<Void> deleteAccessToken(String token) {
        return delete("auth:token:access:" + token);
    }

    public Uni<Void> saveRefreshToken(String token, RefreshTokenData data, long ttlSeconds) {
        return set("auth:token:refresh:" + token, data, ttlSeconds);
    }

    public Uni<RefreshTokenData> getRefreshToken(String token) {
        return get("auth:token:refresh:" + token, RefreshTokenData.class);
    }

    public Uni<Void> deleteRefreshToken(String token) {
        return delete("auth:token:refresh:" + token);
    }

    public Uni<Void> saveSession(String sessionId, SessionData data, long ttlSeconds) {
        return set("auth:session:" + sessionId, data, ttlSeconds);
    }

    public Uni<SessionData> getSession(String sessionId) {
        return get("auth:session:" + sessionId, SessionData.class);
    }

    public Uni<Void> deleteSession(String sessionId) {
        return delete("auth:session:" + sessionId);
    }

    public Uni<Void> saveAdminSession(String sessionId, AdminSessionData data, long ttlSeconds) {
        return set("admin:session:" + sessionId, data, ttlSeconds);
    }

    public Uni<AdminSessionData> getAdminSession(String sessionId) {
        return get("admin:session:" + sessionId, AdminSessionData.class);
    }

    public Uni<Void> deleteAdminSession(String sessionId) {
        return delete("admin:session:" + sessionId);
    }

    public Uni<Void> saveAdminFlash(String sessionId, AdminFlashData data, long ttlSeconds) {
        return set("admin:flash:" + sessionId, data, ttlSeconds);
    }

    public Uni<AdminFlashData> getAdminFlash(String sessionId) {
        return get("admin:flash:" + sessionId, AdminFlashData.class);
    }

    public Uni<Void> deleteAdminFlash(String sessionId) {
        return delete("admin:flash:" + sessionId);
    }

    public Uni<Void> saveAuthorizationRequestState(String state, AuthorizationRequestState data, long ttlSeconds) {
        return set("auth:request:" + state, data, ttlSeconds);
    }

    public Uni<AuthorizationRequestState> getAuthorizationRequestState(String state) {
        return get("auth:request:" + state, AuthorizationRequestState.class);
    }

    public Uni<Void> deleteAuthorizationRequestState(String state) {
        return delete("auth:request:" + state);
    }

    public Uni<Void> saveConsent(String userId, String clientId, ConsentData data, long ttlSeconds) {
        return set("auth:consent:" + userId + ":" + clientId, data, ttlSeconds);
    }

    public Uni<ConsentData> getConsent(String userId, String clientId) {
        return get("auth:consent:" + userId + ":" + clientId, ConsentData.class);
    }

    public Uni<Void> deleteConsent(String userId, String clientId) {
        return delete("auth:consent:" + userId + ":" + clientId);
    }

    public Uni<Boolean> exists(String key) {
        return withRetry(values.get(key).map(value -> value != null), "exists");
    }

    public Uni<Boolean> checkRateLimit(String bucket, int maxRequests, long windowSeconds) {
        String key = "rate:" + bucket;
        return withRetry(values.incr(key), "rateLimit.incr")
                .flatMap(count -> {
                    if (count == 1) {
                        return withRetry(keys.expire(key, Duration.ofSeconds(windowSeconds)), "rateLimit.expire")
                                .replaceWith(count);
                    }
                    return Uni.createFrom().item(count);
                })
                .map(count -> count <= maxRequests)
                .onFailure().invoke(e -> LOG.errorf("Rate limit check failed for bucket %s: %s", bucket, e.getMessage()))
                .onFailure().recoverWithItem(true);
    }

    private Uni<Void> set(String key, Object value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            LOG.debugf("Caching key: %s, ttl: %ds", SecretMasker.maskCacheKey(key), ttlSeconds);
            return withRetry(values.setex(key, ttlSeconds, json), "set:" + key).replaceWithVoid();
        } catch (JsonProcessingException e) {
            return Uni.createFrom().failure(new IllegalStateException("Failed to serialize cache value", e));
        }
    }

    private <T> Uni<T> get(String key, Class<T> type) {
        return withRetry(values.get(key), "get:" + key).flatMap(json -> {
            if (json == null) {
                return Uni.createFrom().nullItem();
            }
            try {
                return Uni.createFrom().item(objectMapper.readValue(json, type));
            } catch (JsonProcessingException e) {
                return Uni.createFrom().failure(new IllegalStateException("Failed to deserialize cache value", e));
            }
        });
    }

    private Uni<Void> delete(String key) {
        LOG.debugf("Deleted cache key: %s", SecretMasker.maskCacheKey(key));
        return withRetry(values.getdel(key), "delete:" + key).replaceWithVoid();
    }

    private <T> Uni<T> withRetry(Uni<T> operation, String operationName) {
        return operation.onFailure().retry()
                .withBackOff(INITIAL_BACKOFF, MAX_BACKOFF)
                .atMost(MAX_RETRIES)
                .onFailure().invoke(e -> LOG.errorf(
                        "Redis operation '%s' failed after %d attempts: %s",
                        operationName, MAX_RETRIES, e.getMessage()));
    }
}
