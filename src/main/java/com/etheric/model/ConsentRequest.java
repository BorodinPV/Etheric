package com.etheric.model;

import jakarta.ws.rs.FormParam;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ConsentRequest {

    @FormParam("action")
    private String action;

    @FormParam("state")
    private String state;

    @FormParam("csrf_token")
    private String csrfToken;
}
