package com.etheric.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    private String email;
    private List<String> roles;
    private Boolean enabled;
}
