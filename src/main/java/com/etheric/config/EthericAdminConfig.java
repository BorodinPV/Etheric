package com.etheric.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "etheric.admin")
public interface EthericAdminConfig {

    String apiKey();

    @WithDefault("28800")
    long sessionLifetime();
}
