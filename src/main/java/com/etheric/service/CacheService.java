package com.etheric.service;

import com.etheric.model.*;
import com.etheric.util.SecretMasker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.set.ReactiveSetCommands;
import io.quarkus.redis.datasource.transactions.TransactionResult;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;

import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import java.util.ArrayList;

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
    private ReactiveSetCommands<String, String> sets;

    @PostConstruct
    void init() {
        values = redis.value(String.class);
        keys = redis.key(String.class);
        sets = redis.set(String.class);
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
        return set(accessTokenKey(token), data, ttlSeconds);
    }

    public Uni<AccessTokenData> getAccessToken(String token) {
        return get(accessTokenKey(token), AccessTokenData.class);
    }

    public Uni<Void> deleteAccessToken(String token) {
        return delete(accessTokenKey(token));
    }

    public Uni<Void> saveRefreshToken(String token, RefreshTokenData data, long ttlSeconds) {
        return set(refreshTokenKey(token), data, ttlSeconds);
    }

    public Uni<RefreshTokenData> getRefreshToken(String token) {
        return get(refreshTokenKey(token), RefreshTokenData.class);
    }

    public Uni<Void> deleteRefreshToken(String token) {
        return delete(refreshTokenKey(token));
    }

    /**
     * Atomically stores a new access/refresh token pair (authorization_code grant).
     */
    public Uni<Void> saveTokenPairAtomically(String accessToken, AccessTokenData accessData, long accessTtlSeconds,
                                             String refreshToken, RefreshTokenData refreshData,
                                             long refreshTtlSeconds) {
        return serializePair(accessToken, accessData, accessTtlSeconds, refreshToken, refreshData, refreshTtlSeconds)
                .flatMap(pair -> withRetry(
                        redis.withTransaction(tx -> {
                            var txValues = tx.value(String.class);
                            return txValues.setex(pair.accessKey(), pair.accessTtlSeconds(), pair.accessJson())
                                    .chain(() -> txValues.setex(
                                            pair.refreshKey(), pair.refreshTtlSeconds(), pair.refreshJson()));
                        }),
                        "saveTokenPairAtomically"))
                .invoke(this::ensureTransactionExecuted)
                .replaceWithVoid();
    }

    /**
     * Atomically rotates a refresh token: deletes the old refresh entry and stores the new
     * access/refresh pair in a single Redis transaction guarded by {@code WATCH}.
     *
     * @return {@code true} when rotation succeeded; {@code false} when the old refresh token
     *         was already consumed or modified concurrently
     */
    public Uni<Boolean> rotateRefreshTokenAtomically(String oldRefreshToken,
                                                     String newAccessToken, AccessTokenData accessData,
                                                     long accessTtlSeconds,
                                                     String newRefreshToken, RefreshTokenData refreshData,
                                                     long refreshTtlSeconds) {
        String oldRefreshKey = refreshTokenKey(oldRefreshToken);
        return serializePair(newAccessToken, accessData, accessTtlSeconds, newRefreshToken, refreshData,
                refreshTtlSeconds)
                .flatMap(pair -> withRetry(
                        redis.withTransaction(
                                ds -> ds.value(String.class).get(oldRefreshKey),
                                (existingJson, tx) -> {
                                    if (existingJson == null) {
                                        return tx.discard();
                                    }
                                    var txValues = tx.value(String.class);
                                    return txValues.getdel(oldRefreshKey)
                                            .chain(() -> txValues.setex(
                                                    pair.accessKey(), pair.accessTtlSeconds(), pair.accessJson()))
                                            .chain(() -> txValues.setex(
                                                    pair.refreshKey(), pair.refreshTtlSeconds(), pair.refreshJson()));
                                },
                                oldRefreshKey),
                        "rotateRefreshTokenAtomically"))
                .map(this::transactionSucceeded);
    }

    public Uni<Void> saveSession(String sessionId, SessionData data, long ttlSeconds) {
        return set(sessionKey(sessionId), data, ttlSeconds)
                .flatMap(v -> data.getUserId() != null
                        ? registerUserSession(data.getUserId(), sessionId, ttlSeconds)
                        : Uni.createFrom().voidItem());
    }

    public Uni<SessionData> getSession(String sessionId) {
        return get(sessionKey(sessionId), SessionData.class);
    }

    public Uni<Void> deleteSession(String sessionId) {
        return getSession(sessionId).flatMap(data ->
                deleteSessionKey(sessionId).flatMap(v -> {
                    if (data != null && data.getUserId() != null) {
                        return unregisterUserSession(data.getUserId(), sessionId);
                    }
                    return Uni.createFrom().voidItem();
                }));
    }

    public Uni<Void> deleteAllUserSessions(String userId) {
        return listUserSessionIds(userId).flatMap(sessionIds -> {
            if (sessionIds.isEmpty()) {
                return Uni.createFrom().voidItem();
            }
            List<Uni<Void>> deletes = sessionIds.stream().map(this::deleteSessionKey).toList();
            return Uni.join().all(deletes).andCollectFailures()
                    .replaceWithVoid()
                    .flatMap(v -> deleteRedisKey(userSessionsKey(userId)));
        });
    }

    public Uni<Void> deleteUserSessionsForClient(String userId, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return deleteAllUserSessions(userId);
        }
        return listUserSessionIds(userId).flatMap(sessionIds -> {
            if (sessionIds.isEmpty()) {
                return Uni.createFrom().voidItem();
            }
            List<Uni<Void>> deletes = new ArrayList<>();
            for (String sessionId : sessionIds) {
                deletes.add(getSession(sessionId).flatMap(data -> {
                    if (data != null && clientId.equals(data.getClientId())) {
                        return deleteSessionKey(sessionId)
                                .flatMap(v -> unregisterUserSession(userId, sessionId));
                    }
                    return Uni.createFrom().voidItem();
                }));
            }
            return Uni.join().all(deletes).andCollectFailures().replaceWithVoid();
        });
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

    private Uni<Void> deleteSessionKey(String sessionId) {
        return delete(sessionKey(sessionId));
    }

    private static String accessTokenKey(String token) {
        return "auth:token:access:" + token;
    }

    private static String refreshTokenKey(String token) {
        return "auth:token:refresh:" + token;
    }

    private static String sessionKey(String sessionId) {
        return "auth:session:" + sessionId;
    }

    private record SerializedTokenPair(
            String accessKey, String accessJson, long accessTtlSeconds,
            String refreshKey, String refreshJson, long refreshTtlSeconds) {
    }

    private Uni<SerializedTokenPair> serializePair(String accessToken, AccessTokenData accessData,
                                                   long accessTtlSeconds,
                                                   String refreshToken, RefreshTokenData refreshData,
                                                   long refreshTtlSeconds) {
        try {
            return Uni.createFrom().item(new SerializedTokenPair(
                    accessTokenKey(accessToken),
                    objectMapper.writeValueAsString(accessData),
                    accessTtlSeconds,
                    refreshTokenKey(refreshToken),
                    objectMapper.writeValueAsString(refreshData),
                    refreshTtlSeconds));
        } catch (JsonProcessingException e) {
            return Uni.createFrom().failure(new IllegalStateException("Failed to serialize cache value", e));
        }
    }

    private boolean transactionSucceeded(TransactionResult result) {
        return result != null && !result.discarded() && !result.isEmpty();
    }

    private void ensureTransactionExecuted(TransactionResult result) {
        if (result == null || result.discarded() || result.isEmpty()) {
            throw new IllegalStateException("Redis transaction did not execute");
        }
    }

    private Uni<Void> registerUserSession(String userId, String sessionId, long ttlSeconds) {
        String key = userSessionsKey(userId);
        return withRetry(sets.sadd(key, sessionId), "userSession.sadd")
                .flatMap(v -> withRetry(keys.expire(key, Duration.ofSeconds(ttlSeconds)), "userSession.expire"))
                .replaceWithVoid();
    }

    private Uni<Void> unregisterUserSession(String userId, String sessionId) {
        return withRetry(sets.srem(userSessionsKey(userId), sessionId), "userSession.srem").replaceWithVoid();
    }

    private Uni<List<String>> listUserSessionIds(String userId) {
        return withRetry(sets.smembers(userSessionsKey(userId)), "userSession.smembers")
                .map(members -> members == null ? List.of() : List.copyOf(members));
    }

    private static String userSessionsKey(String userId) {
        return "auth:user:" + userId + ":sessions";
    }

    private Uni<Void> deleteRedisKey(String key) {
        LOG.debugf("Deleted cache key: %s", SecretMasker.maskCacheKey(key));
        return withRetry(keys.del(key), "deleteKey:" + key).replaceWithVoid();
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
