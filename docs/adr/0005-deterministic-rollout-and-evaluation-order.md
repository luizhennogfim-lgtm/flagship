# ADR 0005 - Deterministic rollout and evaluation order

**Status:** accepted

## Rollout
A user's bucket is `SHA-256(flagKey + ":" + userId)` (first 8 bytes) modulo 100. The user is inside a rollout of N% when `bucket < N`.

- **Stable:** the same user always lands in the same bucket, so they never flicker in and out.
- **Monotonic:** raising 10% to 25% only adds users; nobody loses a feature they had.
- **Stateless:** no table of "who got what". Any instance computes the same answer, and the logic is a pure function, trivial to cache and test.
- **Independent per flag:** the flag key is part of the hash, so the same 10% of users are not the guinea pigs for every flag.

Rejected alternative: random assignment stored per user. It needs writes on the hot path and a table that grows with every user.

## Order of precedence
1. archived flag -> OFF
2. flag OFF in the environment -> OFF (kill switch)
3. targeting rules by priority, first match wins (it can serve ON or OFF)
4. percentage rollout

Rules come before the percentage so a person can be forced in (beta testers) or out (blocked accounts) regardless of the rollout. The kill switch comes first so "turn it off now" always works.

## Missing data
Unknown flags and environments answer OFF with a reason instead of an error, so a renamed flag never breaks the calling application. A missing user attribute never matches a rule, not even NOT_EQUALS: "unknown" is not "different".

## API keys
Applications authenticate with an API key, not a person's JWT. Only the SHA-256 hash is stored, and the full key is shown once at creation. Keys are tied to an organization, which is how the evaluation endpoint knows whose flags to read.
