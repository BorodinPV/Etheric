package com.etheric.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntrospectionResponse {

    private boolean active;

    private String scope;

    @JsonProperty("client_id")
    private String clientId;

    private String username;

    @JsonProperty("token_type")
    private String tokenType;

    private Long exp;

    private Long iat;

    private String sub;

    private String aud;

    private String iss;
}
