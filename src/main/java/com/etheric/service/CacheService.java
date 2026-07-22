package com.etheric.service;

import com.etheric.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class CacheService {

    private static final Logger LOG = Logger.getLogger(CacheService.class);

    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public CacheService() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredEntries, 1, 1, TimeUnit.MINUTES);
    }

    // Authorization Code
    public void saveAuthorizationCode(String code, AuthorizationCodeData data, long ttlSeconds) {
        put("auth:code:" + code, data, ttlSeconds);
    }

    public AuthorizationCodeData getAuthorizationCode(String code) {
        return get("auth:code:" + code, AuthorizationCodeData.class);
    }

    public void deleteAuthorizationCode(String code) {
        delete("auth:code:" + code);
    }

    // Access Token
    public void saveAccessToken(String token, AccessTokenData data, long ttlSeconds) {
        put("auth:token:access:" + token, data, ttlSeconds);
    }

    public AccessTokenData getAccessToken(String token) {
        return get("auth:token:access:" + token, AccessTokenData.class);
    }

    public void deleteAccessToken(String token) {
        delete("auth:token:access:" + token);
    }

    // Refresh Token
    public void saveRefreshToken(String token, RefreshTokenData data, long ttlSeconds) {
        put("auth:token:refresh:" + token, data, ttlSeconds);
    }

    public RefreshTokenData getRefreshToken(String token) {
        return get("auth:token:refresh:" + token, RefreshTokenData.class);
    }

    public void deleteRefreshToken(String token) {
        delete("auth:token:refresh:" + token);
    }

    // Session
    public void saveSession(String sessionId, SessionData data, long ttlSeconds) {
        put("auth:session:" + sessionId, data, ttlSeconds);
    }

    public SessionData getSession(String sessionId) {
        return get("auth:session:" + sessionId, SessionData.class);
    }

    public void deleteSession(String sessionId) {
        delete("auth:session:" + sessionId);
    }

    // CSRF Token (stored within session)
    public void saveCsrfToken(String sessionId, String csrfToken) {
        var session = getSession(sessionId);
        if (session != null) {
            session.setCsrfToken(csrfToken);
            saveSession(sessionId, session, 1800);
        }
    }

    // Authorization Request State
    public void saveAuthorizationRequestState(String state, AuthorizationRequestState data, long ttlSeconds) {
        put("auth:request:" + state, data, ttlSeconds);
    }

    public AuthorizationRequestState getAuthorizationRequestState(String state) {
        return get("auth:request:" + state, AuthorizationRequestState.class);
    }

    public void deleteAuthorizationRequestState(String state) {
        delete("auth:request:" + state);
    }

    // Generic cache operations
    private <T> void put(String key, T value, long ttlSeconds) {
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        cache.put(key, new CacheEntry<>(value, expiresAt));
        LOG.debugf("Cached key: %s, expires at: %s", key, expiresAt);
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String key, Class<T> type) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt)) {
            cache.remove(key);
            LOG.debugf("Cache entry expired: %s", key);
            return null;
        }
        return (T) entry.value;
    }

    private void delete(String key) {
        cache.remove(key);
        LOG.debugf("Deleted cache key: %s", key);
    }

    public boolean exists(String key) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) {
            return false;
        }
        if (Instant.now().isAfter(entry.expiresAt)) {
            cache.remove(key);
            return false;
        }
        return true;
    }

    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        int removed = 0;
        for (String key : cache.keySet()) {
            CacheEntry<?> entry = cache.get(key);
            if (entry != null && now.isAfter(entry.expiresAt)) {
                cache.remove(key);
                removed++;
            }
        }
        if (removed > 0) {
            LOG.debugf("Cleaned up %d expired cache entries", removed);
        }
    }

    private static class CacheEntry<T> {
        final T value;
        final Instant expiresAt;

        CacheEntry(T value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
