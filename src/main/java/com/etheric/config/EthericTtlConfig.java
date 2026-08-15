package com.etheric.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "etheric.jwt")
public interface EthericTtlConfig {

    long accessTokenLifetime();

    long refreshTokenLifetime();

    long authorizationCodeLifetime();

    long sessionLifetime();

    long requestStateLifetime();

    @WithDefault("etheric")
    String issuer();

    @WithDefault("RS256")
    String algorithm();

    Optional<String> privateKeyLocation();

    Optional<String> publicKeyLocation();
}
