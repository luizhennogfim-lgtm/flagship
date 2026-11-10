package io.flagship.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.flagship.organization.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Creates and validates signed (HS256) access tokens. */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        byte[] secret = properties.secret() == null ? new byte[0] : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("flagship.security.jwt.secret must have at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expirationSeconds = properties.expirationMinutes() * 60;
    }

    public String issue(UUID userId, UUID organizationId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("org", organizationId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    /**
     * Validates signature and expiry and returns the caller.
     *
     * @throws JwtException            if the token is invalid or expired
     * @throws IllegalArgumentException if a claim has an unexpected format
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("org", String.class)),
                Role.valueOf(claims.get("role", String.class)));
    }
}
