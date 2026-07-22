package com.etheric.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    private UUID id;
    private String clientId;
    private String clientSecretHash;
    private String clientName;
    private List<String> redirectUris;
    private List<String> scopes;
    private List<String> grantTypes;
    private boolean enabled;
    private LocalDateTime createdAt;
    private String clientLogo;
    private String clientDescription;
}
