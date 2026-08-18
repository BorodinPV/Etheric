package com.etheric.service;

import com.etheric.config.EthericTtlConfig;
import com.etheric.model.JwkKey;
import com.etheric.model.JwksResponse;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * JWT signing, verification, and JWKS exposure.
 */
@ApplicationScoped
public class JwtService {

    private static final Logger LOG = Logger.getLogger(JwtService.class);

    @Inject
    JWTParser jwtParser;

    @Inject
    EthericTtlConfig ttlConfig;

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private String keyId;
    private SignatureAlgorithm signatureAlgorithm;

    @PostConstruct
    public void init() {
        signatureAlgorithm = resolveSignatureAlgorithm(ttlConfig.algorithm());

        Optional<String> privateLoc = ttlConfig.privateKeyLocation();
        Optional<String> publicLoc = ttlConfig.publicKeyLocation();

        if (privateLoc.isPresent() && publicLoc.isPresent()) {
            try {
                RSAPrivateKey loadedPrivate = loadPrivateKey(privateLoc.get());
                RSAPublicKey loadedPublic = loadPublicKey(publicLoc.get());
                if (loadedPrivate != null && loadedPublic != null) {
                    privateKey = loadedPrivate;
                    publicKey = loadedPublic;
                    keyId = deriveKeyId(publicKey);
                    LOG.infof("RSA key pair loaded from PEM (kid=%s)", keyId);
                    return;
                }
            } catch (Exception e) {
                LOG.warnf("Failed to load RSA keys from PEM: %s", e.getMessage());
            }
        }
        generateEphemeralKeys();
    }

    private SignatureAlgorithm resolveSignatureAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalStateException("etheric.jwt.algorithm must not be blank");
        }
        try {
            return SignatureAlgorithm.valueOf(algorithm.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Unsupported JWT algorithm: " + algorithm + ". Supported values: "
                            + java.util.Arrays.toString(SignatureAlgorithm.values()),
                    e);
        }
    }

    private void generateEphemeralKeys() {
        LOG.warn("Using ephemeral RSA key pair for JWT signing (dev only)");
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            publicKey = (RSAPublicKey) keyPair.getPublic();
            privateKey = (RSAPrivateKey) keyPair.getPrivate();
            keyId = deriveKeyId(publicKey);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    private String deriveKeyId(RSAPublicKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getModulus().toByteArray());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(hash, 16));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private RSAPrivateKey loadPrivateKey(String location) throws Exception {
        try (InputStream is = openKeyStream(location)) {
            if (is == null) {
                return null;
            }
            byte[] der = readPemBytes(is);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        }
    }

    private RSAPublicKey loadPublicKey(String location) throws Exception {
        try (InputStream is = openKeyStream(location)) {
            if (is == null) {
                return null;
            }
            byte[] der = readPemBytes(is);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(der));
        }
    }

    private InputStream openKeyStream(String location) throws IOException {
        String path = location.startsWith("classpath:") ? location.substring("classpath:".length()) : location;
        InputStream classpathStream = getClass().getClassLoader().getResourceAsStream(path);
        if (classpathStream != null) {
            return classpathStream;
        }
        Path filePath = Path.of(path);
        if (Files.exists(filePath)) {
            return Files.newInputStream(filePath);
        }
        return null;
    }

    private byte[] readPemBytes(InputStream is) throws IOException {
        String pem = new String(is.readAllBytes(), StandardCharsets.US_ASCII);
        String base64 = pem
                .replaceAll("-----BEGIN[^-]+-----", "")
                .replaceAll("-----END[^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    public String generateAccessToken(String userId, List<String> roles, List<String> scopes) {
        return generateAccessToken(userId, roles, scopes, ttlConfig.accessTokenLifetime());
    }

    public String generateAccessToken(String userId, List<String> roles, List<String> scopes, long lifetimeSeconds) {
        long now = System.currentTimeMillis() / 1000;

        return Jwt.issuer(ttlConfig.issuer())
                .claim(Claims.sub, userId)
                .claim(Claims.groups, roles)
                .claim("scopes", scopes)
                .issuedAt(now)
                .expiresAt(now + lifetimeSeconds)
                .jws().algorithm(signatureAlgorithm)
                .keyId(keyId)
                .sign(privateKey);
    }

    public String generateRefreshToken(String userId, List<String> roles, List<String> scopes) {
        return generateRefreshToken(userId, roles, scopes, ttlConfig.refreshTokenLifetime());
    }

    public String generateRefreshToken(String userId, List<String> roles, List<String> scopes, long lifetimeSeconds) {
        long now = System.currentTimeMillis() / 1000;

        return Jwt.issuer(ttlConfig.issuer())
                .claim(Claims.sub, userId)
                .claim(Claims.groups, roles)
                .claim("scopes", scopes)
                .claim("token_type", "refresh")
                .issuedAt(now)
                .expiresAt(now + lifetimeSeconds)
                .jws().algorithm(signatureAlgorithm)
                .keyId(keyId)
                .sign(privateKey);
    }

    public String generateIdToken(String userId, String clientId, String nonce,
                                  List<String> scopes, String email, String username) {
        return generateIdToken(userId, clientId, nonce, scopes, email, username, ttlConfig.accessTokenLifetime());
    }

    public String generateIdToken(String userId, String clientId, String nonce,
                                  List<String> scopes, String email, String username, long lifetimeSeconds) {
        long now = System.currentTimeMillis() / 1000;

        JwtClaimsBuilder builder = Jwt.issuer(ttlConfig.issuer())
                .subject(userId)
                .audience(clientId)
                .issuedAt(now)
                .expiresAt(now + lifetimeSeconds)
                .claim("auth_time", now);

        if (nonce != null && !nonce.isBlank()) {
            builder.claim("nonce", nonce);
        }
        if (scopes != null) {
            if (scopes.contains("email") && email != null) {
                builder.claim("email", email);
                builder.claim("email_verified", true);
            }
            if (scopes.contains("profile") && username != null) {
                builder.claim("preferred_username", username);
                builder.claim("name", username);
            }
        }

        return builder.jws().algorithm(signatureAlgorithm)
                .keyId(keyId)
                .sign(privateKey);
    }

    public String generateAuthorizationCode() {
        return java.util.UUID.randomUUID().toString();
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

    /**
     * Verifies and parses a JWT, returning empty when verification fails.
     *
     * @param token raw JWT string
     * @return parsed token or empty when invalid
     */
    public Optional<JsonWebToken> parseToken(String token) {
        try {
            return Optional.of(jwtParser.verify(token, publicKey));
        } catch (Exception e) {
            LOG.warnf("Token parsing failed: %s", e.getMessage());
            return Optional.empty();
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
                signatureAlgorithm.name(),
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

    public String getIssuer() {
        return ttlConfig.issuer();
    }
}
