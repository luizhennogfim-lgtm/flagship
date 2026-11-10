# Roadmap to production

What is solid today, what is intentionally simple, and what to do before a real deployment.

## Security
| Item | Today | Next step |
|---|---|---|
| Token lifetime and revocation | 60-minute JWT, role changes apply after expiry ([ADR 0003](adr/0003-stateless-jwt-authentication.md)) | Short-lived access token plus refresh token, with a revocation list |
| Token storage in the browser | `localStorage` ([ADR 0008](adr/0008-frontend.md)) | httpOnly cookie or strict Content-Security-Policy |
| Secrets | `JWT_SECRET` from the environment | Secrets manager (Vault, AWS Secrets Manager); rotate the signing key |
| Tenant isolation | Enforced in application code, covered by tests | Add PostgreSQL Row Level Security as a second safety net ([ADR 0001](adr/0001-multi-tenancy.md)) |
| `/actuator/prometheus` | Public ([ADR 0007](adr/0007-observability.md)) | Separate management port on an internal network, or block at the reverse proxy |
| Accounts | Email and password | Email verification, password reset, SSO (OIDC/SAML), MFA |
| Transport | HTTP in local Docker | TLS at a reverse proxy or load balancer; HSTS |
| Dependencies | Dependabot weekly | Add image scanning (Trivy) and `npm audit` / OWASP dependency check in CI |

## Reliability and performance
- **Redis:** run with persistence and high availability; use an eviction policy that never removes the epoch keys ([ADR 0006](adr/0006-cache-invalidation-and-rate-limiting.md)).
- **Invalidation granularity:** the epoch is per organization. Move to per-flag invalidation if an organization changes flags very often.
- **Rate limiting:** fixed window allows a 2x burst at the boundary; switch to a token bucket or sliding window if that matters.
- **Database:** automated backups, point-in-time recovery, connection pool sizing, and a policy for zero-downtime migrations.
- **Audit retention:** the table is append-only and grows forever; partition by month and archive old data.
- **Load testing:** measure evaluation throughput and p99 latency with k6 against the real cache and database, and set SLOs.

## Product evolution
- Official SDKs (Java, JavaScript) with local caching and streaming updates (SSE or WebSocket) so applications react to a change without polling.
- Multivariate flags (strings, numbers, JSON), today only on/off.
- Scheduled rollouts, automatic rollback on error-rate spikes, and an approval workflow for production changes.
- Flag lifecycle: stale-flag detection and cleanup reminders.
- A statistics endpoint for the dashboard: today the overview counts the first 100 flags on the client.
- Webhooks for flag changes.

## Observability
- Distributed tracing with OpenTelemetry, and alerting rules on error rate, p95 latency, cache hit ratio and `429` volume.
- Ship JSON logs to a log platform and build dashboards on the request id.

## Quality
- Frontend tests: Vitest and React Testing Library for components, Playwright for end-to-end flows.
- Raise the coverage gate gradually from 60%.
- Contract tests between the OpenAPI document and the frontend types (generate `types.ts` from OpenAPI).
- Build and scan the frontend Docker image in CI too (today CI builds the backend image).
