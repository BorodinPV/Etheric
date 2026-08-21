package com.etheric.entity;

import com.etheric.persistence.StringListJsonConverter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_consents")
@IdClass(UserConsentId.class)
@RegisterForReflection
public class UserConsent implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Id
    @Column(name = "client_id", nullable = false)
    public String clientId;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "scopes", nullable = false, columnDefinition = "jsonb")
    public List<String> scopes;

    @Column(name = "granted_at", nullable = false)
    public OffsetDateTime grantedAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    public UserConsent() {
    }

    public UserConsent(UUID userId, String clientId, List<String> scopes,
                       OffsetDateTime grantedAt, OffsetDateTime updatedAt) {
        this.userId = userId;
        this.clientId = clientId;
        this.scopes = scopes;
        this.grantedAt = grantedAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserConsent that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, clientId);
    }
}
