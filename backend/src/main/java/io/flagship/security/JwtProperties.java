package io.flagship.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flagship.security.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
