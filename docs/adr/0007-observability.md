# ADR 0007 - Observability: metrics, logs and the quality gate

**Status:** accepted

## Metrics (Micrometer -> Prometheus -> Grafana)
Besides the standard JVM, HTTP and connection-pool metrics, Flagship publishes the numbers that describe its own job:

| Metric | Tags | Answers |
|---|---|---|
| `flagship.evaluations` | `reason`, `enabled` | How many evaluations, and why did they answer ON/OFF? |
| `flagship.evaluation.duration` | - | How long does a decision take (p50/p95/p99)? |
| `flagship.cache.requests` | `result=hit|miss|error` | Is the cache working? Is Redis failing? |
| `flagship.ratelimit.rejected` | `policy=auth|evaluate|api` | Who is being throttled? |

**Tags are always a small fixed set.** Flag keys, environments, user ids and API keys are NOT tags: their values are unbounded, and every distinct value creates a new time series (a "cardinality explosion" that can bring Prometheus down). Per-flag questions are answered from the audit log or logs, not from metrics.

## Logs
Every request gets an `X-Request-Id` (generated, or taken from the caller when it is short and safe, which prevents log injection). It is put in the logging context, so all lines of one request share the id. In the `docker` profile logs are JSON (Elastic Common Schema), ready for any log platform; locally the id is printed in each line.

## Quality gate
- `maven-enforcer` refuses to build with Java older than 21.
- JaCoCo measures coverage from unit and integration tests together; `mvn verify` fails below 60% line coverage. The number is a floor to prevent erosion, not a goal: raise it as the project grows.
- The CI pipeline (`.github/workflows/ci.yml`) runs compile, unit tests, integration tests, the quality gate and packaging, then builds the Docker image. A frontend job runs lint and the production build.

## Not done (production notes)
`/actuator/prometheus` is reachable without a token, like the health endpoints. In production expose it only on an internal network (or a separate management port) and block it at the reverse proxy.
