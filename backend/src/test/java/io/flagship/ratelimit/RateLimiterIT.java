package io.flagship.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.flagship.AbstractIntegrationTest;

/** The counter and its Lua script against a real Redis. */
class RateLimiterIT extends AbstractIntegrationTest {

    @Autowired
    RateLimiter limiter;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    RateLimitProperties properties;

    @Test
    void allowsTheLimitAndBlocksTheNextRequest() {
        String key = "it-" + UUID.randomUUID();

        for (int i = 1; i <= 3; i++) {
            RateLimiter.Decision decision = limiter.tryAcquire(key, 3);
            assertThat(decision.allowed()).as("request %d", i).isTrue();
            assertThat(decision.remaining()).isEqualTo(3 - i);
        }
        RateLimiter.Decision blocked = limiter.tryAcquire(key, 3);

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remaining()).isZero();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, (long) properties.windowSeconds());
    }

    @Test
    void everyCounterHasAnExpiryAndKeysAreIndependent() {
        String first = "it-" + UUID.randomUUID();
        String second = "it-" + UUID.randomUUID();

        limiter.tryAcquire(first, 1);
        limiter.tryAcquire(first, 1);

        assertThat(limiter.tryAcquire(second, 1).allowed()).isTrue();
        Long secondsToLive = redis.getExpire(properties.keyPrefix() + ":" + first);
        assertThat(secondsToLive).isBetween(1L, (long) properties.windowSeconds());
    }
}
