package com.etheric.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class RefreshTokenData {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("client_id")
    private String clientId;

    private List<String> scopes;
}
