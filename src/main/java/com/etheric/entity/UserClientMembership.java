package com.etheric.entity;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_clients")
@IdClass(UserClientMembershipId.class)
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserClientMembership implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    @EqualsAndHashCode.Include
    public UUID userId;

    @Id
    @Column(name = "client_id", nullable = false)
    @EqualsAndHashCode.Include
    public String clientId;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;
}
