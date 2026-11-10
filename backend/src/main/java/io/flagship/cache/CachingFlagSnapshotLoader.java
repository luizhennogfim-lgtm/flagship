package io.flagship.cache;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.flagship.evaluation.DatabaseFlagSnapshotLoader;
import io.flagship.evaluation.FlagSnapshotLoader;
import io.flagship.evaluation.SnapshotLookup;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Cache-aside in front of the database loader.
 *
 * <p>If Redis is slow, down or returns garbage, evaluation falls back to PostgreSQL: the cache makes the system
 * faster, it is never allowed to make it fail.
 */
@Primary
@Component
public class CachingFlagSnapshotLoader implements FlagSnapshotLoader {

    private static final Logger log = LoggerFactory.getLogger(CachingFlagSnapshotLoader.class);
    private static final String METRIC = "flagship.cache.requests";

    private final DatabaseFlagSnapshotLoader delegate;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final Duration ttl;

    public CachingFlagSnapshotLoader(DatabaseFlagSnapshotLoader delegate, StringRedisTemplate redis,
                                     ObjectMapper mapper, MeterRegistry meters, CacheProperties properties) {
        this.delegate = delegate;
        this.redis = redis;
        this.mapper = mapper;
        this.meters = meters;
        this.ttl = Duration.ofSeconds(properties.ttlSeconds());
    }

    @Override
    public SnapshotLookup load(UUID organizationId, String flagKey, String environmentKey) {
        String key = null;
        try {
            String epoch = redis.opsForValue().get(CacheKeys.epoch(organizationId));
            key = CacheKeys.snapshot(organizationId, epoch == null ? "0" : epoch, environmentKey, flagKey);
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                SnapshotLookup hit = mapper.readValue(cached, SnapshotLookup.class);
                meters.counter(METRIC, "result", "hit").increment();
                return hit;
            }
            meters.counter(METRIC, "result", "miss").increment();
        } catch (RuntimeException | JsonProcessingException ex) {
            meters.counter(METRIC, "result", "error").increment();
            log.warn("Cache read failed, using the database: {}", ex.toString());
        }

        SnapshotLookup fresh = delegate.load(organizationId, flagKey, environmentKey);

        if (key != null) {
            try {
                redis.opsForValue().set(key, mapper.writeValueAsString(fresh), ttl);
            } catch (RuntimeException | JsonProcessingException ex) {
                log.warn("Cache write failed: {}", ex.toString());
            }
        }
        return fresh;
    }
}
