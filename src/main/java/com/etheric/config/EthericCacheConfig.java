package com.etheric.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "etheric.cache")
public interface EthericCacheConfig {

    @WithDefault("60")
    long clientTtlSeconds();

    @WithDefault("2592000")
    long consentTtlSeconds();
}
