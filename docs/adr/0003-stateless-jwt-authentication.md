# ADR 0003 - Stateless JWT authentication with the tenant inside the token

**Status:** accepted

## Decision
Login returns a signed JWT (HS256) holding the user id (`sub`), the organization (`org`) and the role (`role`). Every request is authenticated by `JwtAuthenticationFilter`, which also binds the organization to `TenantContext`. Services read the tenant only from `TenantContext`, never from request parameters, so a caller cannot ask for another organization's data.

## Why
- No server-side session: any instance can serve any request (horizontal scaling).
- The tenant comes from a signed token, which cannot be forged without the secret.
- A user who belongs to several organizations logs in once per organization (`organizationSlug`) and gets one token per organization.

## Trade-offs
- A role change only applies after the current token expires (60 minutes by default). Refresh tokens and revocation are a production evolution.
- The secret must come from the environment (`JWT_SECRET`) in any real deployment; the application refuses secrets shorter than 32 characters.
