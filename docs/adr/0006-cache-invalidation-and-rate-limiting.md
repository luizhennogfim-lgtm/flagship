# ADR 0006 - Cache invalidation strategy and rate limiting

**Status:** accepted

## Cache
Evaluation is the hot path: it runs on every request of every application using Flagship, while flags change rarely. Each evaluation therefore reads a snapshot (flag state + rules) from Redis, and only falls back to PostgreSQL on a miss.

**Cache-aside.** `CachingFlagSnapshotLoader` sits in front of `DatabaseFlagSnapshotLoader`. Hits and misses are counted in a Micrometer metric.

**Invalidation by epoch.** Each organization has a counter (`flagship:epoch:<org>`). Snapshot keys contain the epoch, so incrementing it makes every cached snapshot of that organization unreachable at once, with one O(1) command. Old entries disappear by TTL. Alternatives considered:
- Deleting keys one by one needs to know which (environment, flag) keys exist, and misses cached "not found" answers (for example a flag that is created right after an application asked for it).
- `SCAN` + `DEL` is O(n) and slow on a big Redis.

**After commit.** The increment runs in a transaction `afterCommit` callback. If it ran before the commit, a concurrent reader could reload the old data from PostgreSQL and cache it again just before the change becomes visible.

**Consistency we promise.** After a change returns, the next evaluation sees it (read-your-writes), because the epoch is bumped before the HTTP response. The only exception is a Redis outage during the bump: the change is committed, an error is logged, and stale snapshots can live until the TTL (60 s by default). That is the "reasonable consistency" trade-off.

**Redis is an optimization, never a dependency.** Any Redis error on the read path falls back to PostgreSQL.

**Known cost.** The epoch is per organization, so any change invalidates all cached snapshots of that organization. Changes are rare compared to evaluations, so this is fine; a finer-grained scheme (per flag) is a possible evolution.

**Operational note.** Run Redis with a policy that does not evict the epoch keys (they are tiny). If an epoch key were lost, it restarts at 0 and entries of that old epoch could be read again, but only until their TTL.

## Rate limiting
Three separate counters, so one noisy client cannot starve others:
- `/api/v1/auth/**` per client IP (slows password guessing),
- `/api/v1/evaluate` per API key,
- everything else per logged-in user.

A fixed-window counter in Redis (one atomic Lua script: increment + expire) was chosen over a token bucket for simplicity and a single round trip. Its known weakness is a burst of up to 2x the limit across a window boundary. When Redis is unavailable requests are allowed (fail open), because blocking every customer because of a cache outage is worse than briefly not limiting.

Responses carry `X-RateLimit-Limit`, `X-RateLimit-Remaining` and, when blocked, `429` with `Retry-After`.
