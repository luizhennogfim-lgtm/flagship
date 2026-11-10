package io.flagship.cache;

import java.util.UUID;

/** Redis key layout, in one place. */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** A counter per organization. Bumping it makes every cached snapshot of that organization unreachable. */
    public static String epoch(UUID organizationId) {
        return "flagship:epoch:" + organizationId;
    }

    /**
     * The environment is length-prefixed so two different (environment, flag) pairs can never produce the same key,
     * whatever characters the caller sends.
     */
    public static String snapshot(UUID organizationId, String epoch, String environmentKey, String flagKey) {
        return "flagship:snap:" + organizationId + ":" + epoch + ":" + environmentKey.length() + ":" + environmentKey
                + ":" + flagKey;
    }

    public static String snapshotPattern(UUID organizationId) {
        return "flagship:snap:" + organizationId + ":*";
    }
}
