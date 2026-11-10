package io.flagship.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "flagship.cache")
public record CacheProperties(@DefaultValue("60") long ttlSeconds) {
}
