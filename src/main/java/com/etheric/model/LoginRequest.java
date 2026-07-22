package com.etheric.model;

import jakarta.ws.rs.FormParam;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginRequest {

    @FormParam("username")
    private String username;

    @FormParam("password")
    private String password;

    @FormParam("state")
    private String state;

    @FormParam("csrf_token")
    private String csrfToken;
}
