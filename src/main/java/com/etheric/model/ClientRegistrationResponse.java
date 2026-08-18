package com.etheric.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientRegistrationResponse {

    @JsonProperty("client_id")
    private String clientId;

    /** Returned only once at registration time. */
    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("redirect_uris")
    private List<String> redirectUris;

    private List<String> scopes;

    @JsonProperty("grant_types")
    private List<String> grantTypes;

    private boolean enabled;

    @JsonProperty("client_description")
    private String clientDescription;

    @JsonProperty("access_token_lifetime_seconds")
    private Integer accessTokenLifetimeSeconds;

    @JsonProperty("refresh_token_lifetime_seconds")
    private Integer refreshTokenLifetimeSeconds;

    @JsonProperty("session_lifetime_seconds")
    private Integer sessionLifetimeSeconds;
}
