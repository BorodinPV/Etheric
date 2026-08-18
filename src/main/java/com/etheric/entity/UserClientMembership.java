package com.etheric.entity;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_clients")
@IdClass(UserClientMembershipId.class)
@RegisterForReflection
public class UserClientMembership implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Id
    @Column(name = "client_id", nullable = false)
    public String clientId;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    public UserClientMembership() {
    }

    public UserClientMembership(UUID userId, String clientId, OffsetDateTime createdAt) {
        this.userId = userId;
        this.clientId = clientId;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserClientMembership that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, clientId);
    }
}
