package com.etheric.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    private String username;
    private String password;
    private String email;
    private List<String> roles;
    private Boolean enabled;
}
