# Validation checklist

Tick each item after you verify it **on your machine**. Nothing here is pre-checked on purpose.

## Build and tests
- [ ] `cd backend && mvn test` passes
- [ ] `cd backend && mvn verify` passes (Docker running), including the 60% coverage gate
- [ ] `cd frontend && npm run lint` passes
- [ ] `cd frontend && npm run build` passes
- [ ] A test exists that exercises concurrent updates of the same flag state (expects one success and one `409`)
- [ ] A test proves one organization cannot read another organization's flags

## Local run
- [ ] `docker compose up -d` starts PostgreSQL and Redis healthy
- [ ] Backend starts and `http://localhost:8080/actuator/health` shows `UP`
- [ ] Swagger UI opens at `http://localhost:8080/swagger-ui.html`
- [ ] Frontend opens at `http://localhost:5173`
- [ ] `docker compose --profile full up --build` brings the backend to healthy

## Functional
- [ ] Register creates the user and organization; the three default environments exist
- [ ] Create a flag, turn it ON in production at 25%, save
- [ ] Evaluating 1,000 different user ids gives roughly 25% ON
- [ ] The same user id gets the same answer on repeated calls
- [ ] Raising 25% to 50% never turns an ON user OFF
- [ ] A `GROUP` rule forces beta users ON even at 0%
- [ ] Turning the flag OFF returns `FLAG_DISABLED` immediately (cache invalidation works)
- [ ] Editing the same flag in two tabs: the second save shows a conflict message
- [ ] An API key evaluates correctly; after revoking it, evaluation returns `401`
- [ ] Audit log shows who, what, when, before and after for each change

## Security
- [ ] A VIEWER cannot create or edit flags (API answers `403`)
- [ ] An EDITOR cannot open the audit log or create API keys
- [ ] Requests without a token answer `401`
- [ ] Login rate limit answers `429` after 20 attempts in a minute
- [ ] `JWT_SECRET` is not the default value outside local development

## Observability
- [ ] `docker compose --profile monitoring up -d` starts Prometheus and Grafana
- [ ] The Prometheus target for the backend is `UP`
- [ ] The Grafana dashboard shows data after some evaluations
- [ ] Every response has an `X-Request-Id` and the same id appears in the logs

## CI and repository
- [ ] The GitHub Actions run is green on `main` (backend, Docker image, frontend)
- [ ] `frontend/package-lock.json` is committed
- [ ] `.env` is in `.gitignore` and not committed
- [ ] README renders correctly on GitHub, including the Mermaid diagrams
- [ ] Screenshots added to the README (dashboard, flag detail, audit log)
