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
@Table(name = "clients")
@RegisterForReflection
public class Client extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "client_id", nullable = false, unique = true)
    public String clientId;

    @Column(name = "client_secret_hash", nullable = false)
    public String clientSecretHash;

    @Column(name = "client_name", nullable = false)
    public String clientName;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "redirect_uris", nullable = false, columnDefinition = "jsonb")
    public List<String> redirectUris;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "scopes", nullable = false, columnDefinition = "jsonb")
    public List<String> scopes;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "grant_types", nullable = false, columnDefinition = "jsonb")
    public List<String> grantTypes;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "client_description")
    public String clientDescription;

    public Client() {
    }

    public Client(UUID id, String clientId, String clientSecretHash, String clientName,
                  List<String> redirectUris, List<String> scopes, List<String> grantTypes,
                  boolean enabled, OffsetDateTime createdAt, String clientDescription) {
        this.id = id;
        this.clientId = clientId;
        this.clientSecretHash = clientSecretHash;
        this.clientName = clientName;
        this.redirectUris = redirectUris;
        this.scopes = scopes;
        this.grantTypes = grantTypes;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.clientDescription = clientDescription;
    }
}
