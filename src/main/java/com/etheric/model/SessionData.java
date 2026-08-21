package com.etheric.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class SessionData {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("csrf_token")
    private String csrfToken;

    @JsonProperty("created_at")
    private long createdAt;

    @JsonProperty("client_id")
    private String clientId;

    public SessionData(String userId, String csrfToken, long createdAt) {
        this(userId, csrfToken, createdAt, null);
    }
}
