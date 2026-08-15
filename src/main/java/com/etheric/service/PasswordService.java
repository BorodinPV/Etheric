package com.etheric.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Bcrypt password hashing for user passwords and client secrets.
 */
@ApplicationScoped
public class PasswordService {

    private static final Logger LOG = Logger.getLogger(PasswordService.class);

    public String hashPassword(String password) {
        return BcryptUtil.bcryptHash(password);
    }

    public boolean verifyPassword(String password, String passwordHash) {
        try {
            return BcryptUtil.matches(password, passwordHash);
        } catch (Exception e) {
            LOG.warnf("Password verification failed: %s", e.getMessage());
            return false;
        }
    }
}
