package io.flagship.evaluation;

import java.util.UUID;

/** Where evaluation data comes from. Stage 5 adds a Redis-backed implementation in front of the database one. */
public interface FlagSnapshotLoader {

    SnapshotLookup load(UUID organizationId, String flagKey, String environmentKey);
}
