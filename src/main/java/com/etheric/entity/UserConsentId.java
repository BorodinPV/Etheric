package com.etheric.entity;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@RegisterForReflection
public class UserConsentId implements Serializable {

    public UUID userId;
    public String clientId;

    public UserConsentId() {
    }

    public UserConsentId(UUID userId, String clientId) {
        this.userId = userId;
        this.clientId = clientId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserConsentId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, clientId);
    }
}
