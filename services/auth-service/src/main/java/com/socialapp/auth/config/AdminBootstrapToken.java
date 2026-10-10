package com.socialapp.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Validates the optional, one-time secret used by the internal admin bootstrap route. */
@Component
public class AdminBootstrapToken {

    private final byte[] configuredToken;

    public AdminBootstrapToken(@Value("${security.admin-bootstrap-token:}") String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            this.configuredToken = null;
            return;
        }

        byte[] tokenBytes = rawToken.getBytes(StandardCharsets.UTF_8);
        if (tokenBytes.length < 32) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_TOKEN must contain at least 32 UTF-8 bytes");
        }
        this.configuredToken = tokenBytes;
    }

    public boolean matches(String suppliedToken) {
        if (configuredToken == null || suppliedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(configuredToken, suppliedToken.getBytes(StandardCharsets.UTF_8));
    }
}
