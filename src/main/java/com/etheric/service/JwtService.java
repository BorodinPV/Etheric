package com.etheric.service;

import com.etheric.model.JwkKey;
import com.etheric.model.JwksResponse;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

@ApplicationScoped
public class JwtService {

    private static final Logger LOG = Logger.getLogger(JwtService.class);

    @Inject
    JWTParser jwtParser;

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private String keyId;

    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            publicKey = (RSAPublicKey) keyPair.getPublic();
            privateKey = (RSAPrivateKey) keyPair.getPrivate();
            keyId = UUID.randomUUID().toString();
            LOG.info("RSA key pair generated for JWT signing");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    public String generateAccessToken(String userId, List<String> roles, List<String> scopes) {
        long now = System.currentTimeMillis() / 1000;
        long expiry = 3600;

        return Jwt.claim(Claims.sub, userId)
                .claim(Claims.groups, roles)
                .claim("scopes", scopes)
                .issuedAt(now)
                .expiresAt(now + expiry)
                .jws().algorithm(SignatureAlgorithm.RS256)
                .keyId(keyId)
                .sign(privateKey);
    }

    public String generateRefreshToken(String userId, List<String> roles, List<String> scopes) {
        long now = System.currentTimeMillis() / 1000;
        long expiry = 604800;

        return Jwt.claim(Claims.sub, userId)
                .claim(Claims.groups, roles)
                .claim("scopes", scopes)
                .claim("token_type", "refresh")
                .issuedAt(now)
                .expiresAt(now + expiry)
                .jws().algorithm(SignatureAlgorithm.RS256)
                .keyId(keyId)
                .sign(privateKey);
    }

    public String generateAuthorizationCode() {
        return UUID.randomUUID().toString();
    }

    public boolean verifyToken(String token) {
        try {
            JsonWebToken jwt = jwtParser.verify(token, publicKey);
            return jwt != null && !isTokenExpired(jwt);
        } catch (Exception e) {
            LOG.warnf("Token verification failed: %s", e.getMessage());
            return false;
        }
    }

    public JsonWebToken parseToken(String token) {
        try {
            return jwtParser.verify(token, publicKey);
        } catch (Exception e) {
            LOG.warnf("Token parsing failed: %s", e.getMessage());
            return null;
        }
    }

    private boolean isTokenExpired(JsonWebToken jwt) {
        Long exp = jwt.getClaim("exp");
        if (exp == null) {
            return false;
        }
        return exp * 1000 < System.currentTimeMillis();
    }

    public JwksResponse getJwks() {
        JwkKey jwkKey = new JwkKey(
                "RSA",
                keyId,
                "sig",
                "RS256",
                Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray())
        );
        return new JwksResponse(List.of(jwkKey));
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyId() {
        return keyId;
    }
}
