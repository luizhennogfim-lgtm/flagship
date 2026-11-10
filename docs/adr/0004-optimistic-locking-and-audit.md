# ADR 0004 - Optimistic locking and transactional audit trail

**Status:** accepted

## Optimistic locking
Each flag state (`flag_environment_configs`) has a `version`. A client must send the version it last read. If it no longer matches, the API answers `409 Conflict` instead of silently overwriting someone else's change. The check happens twice: explicitly in the service (cheap, readable error) and by Hibernate's `@Version` at flush time (protects the real race between two simultaneous requests). Pessimistic locks were rejected: flag edits are rare and short, so conflicts are unlikely and blocking rows would only add contention.

## Audit trail
`AuditService.record` runs inside the caller's transaction, so a change and its audit record are committed or rolled back together: there is never a change without history, nor history of a change that did not happen. Old and new values are stored as JSONB snapshots, which lets new fields be audited without schema changes. Records are append-only.

## Soft delete
Flags are archived, not deleted, so their history stays meaningful and the key cannot be silently reused.
