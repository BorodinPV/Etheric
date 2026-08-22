package com.etheric.entity;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserConsentId implements Serializable {

    public UUID userId;
    public String clientId;
}
