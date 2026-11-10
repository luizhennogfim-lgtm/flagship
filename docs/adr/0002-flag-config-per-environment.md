# ADR 0002 - Flag configuration per environment

**Status:** accepted

## Decision
A flag (`feature_flags`) holds identity data only: key, name, description. Its runtime state lives in `flag_environment_configs`, one row per (flag, environment): `enabled`, `rollout_percentage` and a `version` column used for optimistic locking.

## Why
The same flag is usually ON in development, partially rolled out in staging and OFF in production. Separate rows let each environment change independently, and optimistic locking prevents two admins from silently overwriting each other.
