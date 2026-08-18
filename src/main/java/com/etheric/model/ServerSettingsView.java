package com.etheric.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerSettingsView {

    @JsonProperty("oauth_session_cookie_name")
    private String oauthSessionCookieName;

    @JsonProperty("oauth_session_lifetime_seconds")
    private int oauthSessionLifetimeSeconds;

    @JsonProperty("default_access_token_lifetime_seconds")
    private int defaultAccessTokenLifetimeSeconds;

    @JsonProperty("default_refresh_token_lifetime_seconds")
    private int defaultRefreshTokenLifetimeSeconds;

    @JsonProperty("session_cookie_secure")
    private boolean sessionCookieSecure;
}
