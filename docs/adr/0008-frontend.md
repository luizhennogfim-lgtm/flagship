# ADR 0008 - Frontend: same-origin proxy, token storage and conflict handling

**Status:** accepted

## Same origin instead of CORS
The API has no CORS configuration. In development, Vite serves the dashboard and proxies `/api` to Spring Boot; in Docker, nginx does the same. The browser only ever talks to one origin, so the backend stays closed to cross-origin calls, which is the safer default for an API that uses bearer tokens.

## One place that mirrors the API
`src/api/types.ts` mirrors the backend records and `src/api/client.ts` is the only code that calls `fetch`. It attaches the token, turns RFC 7807 responses into a typed `ApiError` (with per-field messages), reacts to `401` by signing the user out and to `429` by showing the retry time. When the API changes, the change is local.

## Server state with TanStack Query
Lists and details are cached and invalidated after each mutation instead of being copied into global state. This keeps the screens consistent with the server with little code.

## Token storage
The JWT is kept in `localStorage` so a page refresh keeps the user logged in. Trade-off: any script running on the page could read it (XSS). The app renders no user-supplied HTML, which limits the risk today. Production hardening: a strict Content-Security-Policy and short token lifetime, or an httpOnly cookie with a refresh flow.

## Conflict handling (optimistic locking)
Saving a release or the rules sends the `version` the screen last read. On `409` the screen reloads the latest state and shows the message, so a user never overwrites a colleague's change without seeing it.

## Roles in the UI
Buttons follow the same rules as the API (VIEWER reads, EDITOR edits, ADMIN/OWNER manages). This is a convenience only: the real enforcement is in the backend with `@PreAuthorize`.
