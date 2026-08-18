package com.etheric.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "server_settings")
@RegisterForReflection
public class ServerSettings extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public Integer id;

    @Column(name = "oauth_session_cookie_name", nullable = false)
    public String oauthSessionCookieName;

    @Column(name = "oauth_session_lifetime_seconds", nullable = false)
    public int oauthSessionLifetimeSeconds;

    @Column(name = "default_access_token_lifetime_seconds", nullable = false)
    public int defaultAccessTokenLifetimeSeconds;

    @Column(name = "default_refresh_token_lifetime_seconds", nullable = false)
    public int defaultRefreshTokenLifetimeSeconds;

    @Column(name = "session_cookie_secure", nullable = false)
    public boolean sessionCookieSecure;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    public ServerSettings() {
    }
}
