package io.flagship.cache;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Invalidates cached evaluation data when flags, rules or environments change.
 *
 * <p>Invalidation = incrementing the organization's epoch counter. Cached snapshots are stored under a key that
 * contains the epoch, so after the increment nobody can read the old ones (they simply expire later). It is one
 * cheap operation and needs no scanning or deleting.
 *
 * <p>The increment happens AFTER the database transaction commits. Doing it before would let a concurrent reader
 * load the old data from the database and cache it again before the change is visible.
 */
@Component
public class CacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidator.class);

    private final StringRedisTemplate redis;

    public CacheInvalidator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void afterCommitInvalidate(UUID organizationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bump(organizationId);
                }
            });
        } else {
            bump(organizationId);
        }
    }

    private void bump(UUID organizationId) {
        try {
            redis.opsForValue().increment(CacheKeys.epoch(organizationId));
        } catch (RuntimeException ex) {
            // The change is already committed. Cached data may stay stale until its TTL expires.
            log.error("Could not invalidate the cache of organization {}; stale data may be served for up to the TTL",
                    organizationId, ex);
        }
    }
}
