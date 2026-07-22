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
public class User {

    private UUID id;
    private String username;
    private String passwordHash;
    private String email;
    private List<String> roles;
    private boolean enabled;
    private LocalDateTime createdAt;
}
