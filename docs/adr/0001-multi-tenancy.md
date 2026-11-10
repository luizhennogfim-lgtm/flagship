# ADR 0001 - Multi-tenancy model

**Status:** accepted

## Context
Flagship serves many organizations from a single deployment. Data from one organization must never be visible to another.

## Options considered
1. **Database per tenant** - strongest isolation, but expensive to operate and migrate.
2. **Schema per tenant** - good isolation, but migrations must run once per schema.
3. **Shared schema with `organization_id` on every tenant-owned table** - simplest to operate and cheap to scale.

## Decision
Option 3. Every tenant-owned table carries `organization_id`. The tenant of each request is resolved from the authenticated user (Stage 2) and held in `TenantContext`. Repositories and services always filter by it.

## Consequences
- One migration path and one connection pool.
- Isolation depends on application code, so it is covered by integration tests (Stage 2).
- PostgreSQL Row Level Security can be added later as a second safety net.
