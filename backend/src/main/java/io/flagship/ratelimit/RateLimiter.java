package io.flagship.ratelimit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter in Redis: the first request creates the counter and starts the window,
 * every request increments it, and the counter disappears when the window ends.
 *
 * <p>The increment and the expiry are done by one Lua script so they are atomic: there is no moment where a counter
 * exists without an expiry (which would block a client forever).
 *
 * <p>Known trade-off of fixed windows: a client can send up to twice the limit around a window boundary.
 * If Redis is unavailable requests are allowed (fail open): availability wins over strictness.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private static final String SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('TTL', KEYS[1])}
            """;

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> script = new DefaultRedisScript<>(SCRIPT, List.class);

    public RateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public record Decision(boolean allowed, int limit, long remaining, long retryAfterSeconds) {
    }

    public Decision tryAcquire(String key, int limit) {
        try {
            List<?> result = redis.execute(script, List.of(properties.keyPrefix() + ":" + key),
                    String.valueOf(properties.windowSeconds()));
            long count = ((Number) result.get(0)).longValue();
            long ttl = ((Number) result.get(1)).longValue();
            return new Decision(count <= limit, limit, Math.max(0, limit - count),
                    ttl > 0 ? ttl : properties.windowSeconds());
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable, allowing the request: {}", ex.toString());
            return new Decision(true, limit, limit, 0);
        }
    }
}
