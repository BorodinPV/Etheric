package com.etheric.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "etheric.admin")
public interface EthericAdminConfig {

    String apiKey();

    @WithDefault("28800")
    long sessionLifetime();

    /**
     * Public path of the admin console. Redirects, breadcrumbs and template links
     * are built from this value. JAX-RS {@code @Path} still needs a compile-time
     * constant and must stay in sync with this default.
     */
    @WithDefault("/admin/console")
    String consolePath();

    default String loginPath() {
        return consolePath() + "/login";
    }

    default String localePath() {
        return consolePath() + "/locale";
    }

    default String logoutPath() {
        return consolePath() + "/logout";
    }

    default String clientsPath() {
        return consolePath() + "/clients";
    }

    default String usersPath() {
        return consolePath() + "/users";
    }

    default String clientTabPath(String clientId, String tab) {
        return clientsPath() + "/" + clientId + "/" + tab;
    }

    default String userTabPath(String userId, String tab) {
        return usersPath() + "/" + userId + "/" + tab;
    }

    default String relativeConsolePath() {
        String path = consolePath();
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
