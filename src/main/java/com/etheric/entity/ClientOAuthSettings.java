package com.etheric.entity;

/**
 * Per-client OAuth token and session cookie settings.
 */
public record ClientOAuthSettings(
        int accessTokenLifetimeSeconds,
        int refreshTokenLifetimeSeconds,
        int sessionLifetimeSeconds,
        String sessionCookieName,
        boolean sessionCookieSecure) {

    public static final int DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS = 3600;
    public static final int DEFAULT_REFRESH_TOKEN_LIFETIME_SECONDS = 604800;
    public static final int DEFAULT_SESSION_LIFETIME_SECONDS = 28800;
    public static final String DEFAULT_SESSION_COOKIE_NAME = "SESSIONID";
    public static final boolean DEFAULT_SESSION_COOKIE_SECURE = true;

    public static ClientOAuthSettings defaults() {
        return new ClientOAuthSettings(
                DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS,
                DEFAULT_REFRESH_TOKEN_LIFETIME_SECONDS,
                DEFAULT_SESSION_LIFETIME_SECONDS,
                DEFAULT_SESSION_COOKIE_NAME,
                DEFAULT_SESSION_COOKIE_SECURE);
    }

    public static ClientOAuthSettings withLifetimes(int accessTokenLifetimeSeconds,
                                                    int refreshTokenLifetimeSeconds,
                                                    int sessionLifetimeSeconds) {
        return new ClientOAuthSettings(
                accessTokenLifetimeSeconds,
                refreshTokenLifetimeSeconds,
                sessionLifetimeSeconds,
                DEFAULT_SESSION_COOKIE_NAME,
                DEFAULT_SESSION_COOKIE_SECURE);
    }
}
