# Flagship frontend

React 18 + TypeScript + Vite admin dashboard for the Flagship API.

## Run (development)

Requirements: Node 20+, the backend running on http://localhost:8080 (`docker compose up -d` + `mvn spring-boot:run`).

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

The backend has no CORS configuration on purpose: Vite proxies `/api` to the backend, so the browser only talks to one origin.
Use another backend address with `VITE_API_TARGET=http://host:port npm run dev`.

## Checks

```bash
npm run lint       # TypeScript type check
npm run build      # type check + production build in dist/
```

## Run in Docker

Add the service from `docker-compose.frontend.snippet.yml` to the root `docker-compose.yml`, then:

```bash
docker compose --profile full up --build     # http://localhost:5173 (nginx proxies /api to the backend)
```

## Where things are

- `src/api/types.ts` mirrors the backend records. If the API changes, start here.
- `src/api/client.ts` fetch wrapper (token, RFC 7807 errors, 401 and 429 handling).
- `src/pages/FlagDetail.tsx` release control (rollout), targeting rules editor and evaluation playground.

## Notes

- The JWT is kept in `localStorage` so a refresh keeps you logged in. Any script running on the page can read it (XSS risk).
  Mitigation in production: strict CSP and short token lifetime, or move to an httpOnly cookie.
- Optimistic locking: saving a release or rules sends the `version` you last read. If someone else changed it, the API
  answers 409 and the screen reloads the latest state.
