package com.etheric.entity;

import com.etheric.persistence.StringListJsonConverter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_consents")
@IdClass(UserConsentId.class)
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserConsent implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    @EqualsAndHashCode.Include
    public UUID userId;

    @Id
    @Column(name = "client_id", nullable = false)
    @EqualsAndHashCode.Include
    public String clientId;

    @Getter
    @Setter
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "scopes", nullable = false, columnDefinition = "jsonb")
    private List<String> scopes;

    @Column(name = "granted_at", nullable = false)
    public OffsetDateTime grantedAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
