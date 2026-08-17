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
import java.util.UUID;

@Entity
@Table(name = "users")
@RegisterForReflection
public class User extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "username", nullable = false, unique = true)
    public String username;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "email")
    public String email;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "roles", nullable = false, columnDefinition = "jsonb")
    public List<String> roles;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    public User() {
    }

    public User(UUID id, String username, String passwordHash, String email,
                List<String> roles, boolean enabled, OffsetDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.roles = roles;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }
}
