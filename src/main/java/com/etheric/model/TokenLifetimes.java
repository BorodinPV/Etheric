package com.etheric.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenLifetimes {

    private long accessTokenLifetimeSeconds;
    private long refreshTokenLifetimeSeconds;
    private long sessionLifetimeSeconds;
}
