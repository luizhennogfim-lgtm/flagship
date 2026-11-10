package io.flagship.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.flagship.evaluation.DatabaseFlagSnapshotLoader;
import io.flagship.evaluation.FlagSnapshot;
import io.flagship.evaluation.SnapshotLookup;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CachingFlagSnapshotLoaderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID organizationId = UUID.randomUUID();
    private final SnapshotLookup fromDatabase = SnapshotLookup.found(
            new FlagSnapshot(UUID.randomUUID(), "checkout", false, true, 50, List.of()));

    private DatabaseFlagSnapshotLoader delegate;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private CachingFlagSnapshotLoader loader;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        delegate = mock(DatabaseFlagSnapshotLoader.class);
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        loader = new CachingFlagSnapshotLoader(delegate, redis, mapper, new SimpleMeterRegistry(), new CacheProperties(60));
    }

    @Test
    void aCachedSnapshotIsReturnedWithoutTouchingTheDatabase() throws Exception {
        when(ops.get(CacheKeys.epoch(organizationId))).thenReturn("3");
        when(ops.get(CacheKeys.snapshot(organizationId, "3", "production", "checkout")))
                .thenReturn(mapper.writeValueAsString(fromDatabase));

        SnapshotLookup result = loader.load(organizationId, "checkout", "production");

        assertThat(result).isEqualTo(fromDatabase);
        verifyNoInteractions(delegate);
    }

    @Test
    void aMissLoadsFromTheDatabaseAndStoresTheResultWithATtl() {
        when(ops.get(anyString())).thenReturn(null);
        when(delegate.load(organizationId, "checkout", "production")).thenReturn(fromDatabase);

        SnapshotLookup result = loader.load(organizationId, "checkout", "production");

        assertThat(result).isEqualTo(fromDatabase);
        verify(ops).set(eq(CacheKeys.snapshot(organizationId, "0", "production", "checkout")), anyString(),
                eq(Duration.ofSeconds(60)));
    }

    @Test
    void whenRedisIsDownTheDatabaseAnswers() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis is down"));
        when(delegate.load(organizationId, "checkout", "production")).thenReturn(fromDatabase);

        SnapshotLookup result = loader.load(organizationId, "checkout", "production");

        assertThat(result).isEqualTo(fromDatabase);
    }
}
