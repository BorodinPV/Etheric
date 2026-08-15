package com.etheric.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "etheric.rate-limit")
public interface EthericRateLimitConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("60")
    long windowSeconds();

    @WithDefault("60")
    int authorizeMax();

    @WithDefault("20")
    int loginMax();

    @WithDefault("30")
    int tokenMax();

    @WithDefault("20")
    int consentMax();
}
