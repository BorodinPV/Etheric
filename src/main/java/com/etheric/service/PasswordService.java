package com.etheric.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@ApplicationScoped
public class PasswordService {

    private static final Logger LOG = Logger.getLogger(PasswordService.class);

    public String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    public boolean verifyPassword(String password, String passwordHash) {
        try {
            String hashedInput = hashPassword(password);
            return MessageDigest.isEqual(
                    hashedInput.getBytes(StandardCharsets.UTF_8),
                    passwordHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            LOG.warnf("Password verification failed: %s", e.getMessage());
            return false;
        }
    }
}
