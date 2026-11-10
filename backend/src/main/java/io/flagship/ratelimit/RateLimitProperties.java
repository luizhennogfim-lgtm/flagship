package io.flagship.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** All limits are "requests per window" (the window defaults to 60 seconds). */
@ConfigurationProperties(prefix = "flagship.rate-limit")
public record RateLimitProperties(
        @DefaultValue("60") int windowSeconds,
        @DefaultValue("20") int authLimit,
        @DefaultValue("3000") int evaluateLimit,
        @DefaultValue("300") int apiLimit,
        @DefaultValue("flagship:rl") String keyPrefix) {
}
