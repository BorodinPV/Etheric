package com.etheric.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "etheric.registration")
public interface EthericRegistrationConfig {

    @WithDefault("true")
    boolean enabled();
}
