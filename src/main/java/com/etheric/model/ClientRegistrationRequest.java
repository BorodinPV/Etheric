package com.etheric.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientRegistrationRequest {

    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("redirect_uris")
    private List<String> redirectUris;

    private List<String> scopes;

    @JsonProperty("grant_types")
    private List<String> grantTypes;

    @JsonProperty("client_description")
    private String clientDescription;

    /** Optional custom client_id; generated if omitted. */
    @JsonProperty("client_id")
    private String clientId;
}
