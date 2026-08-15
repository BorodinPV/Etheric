package com.etheric.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String username;
    private String email;
    private List<String> roles;
    private boolean enabled;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;
}
