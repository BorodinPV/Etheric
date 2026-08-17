package com.etheric.entity;

import com.etheric.persistence.StringListJsonConverter;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "authorization_codes")
@RegisterForReflection
public class AuthorizationCodeEntity extends PanacheEntityBase {

    @Id
    @Column(name = "code")
    public String code;

    @Column(name = "client_id", nullable = false)
    public String clientId;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "redirect_uri", nullable = false)
    public String redirectUri;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "scopes", nullable = false, columnDefinition = "jsonb")
    public List<String> scopes;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    public OffsetDateTime expiresAt;

    @Column(name = "used_at")
    public OffsetDateTime usedAt;

    public AuthorizationCodeEntity() {
    }

    public AuthorizationCodeEntity(String code, String clientId, String userId, String redirectUri,
                                   List<String> scopes, OffsetDateTime createdAt,
                                   OffsetDateTime expiresAt) {
        this.code = code;
        this.clientId = clientId;
        this.userId = userId;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
