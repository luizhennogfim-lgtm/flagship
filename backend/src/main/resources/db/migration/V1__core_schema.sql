-- Flagship core schema. Multi-tenancy model: shared database, shared schema,
-- every tenant-owned table carries organization_id (see docs/adr/0001-multi-tenancy.md).

CREATE TABLE organizations (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(80)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE teams (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_team_name_per_org UNIQUE (organization_id, name)
);

CREATE TABLE memberships (
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    team_id          UUID        REFERENCES teams (id) ON DELETE SET NULL,
    role             VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_membership_role CHECK (role IN ('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')),
    CONSTRAINT uq_membership UNIQUE (organization_id, user_id)
);
CREATE INDEX idx_memberships_user ON memberships (user_id);

CREATE TABLE environments (
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    key              VARCHAR(40) NOT NULL,
    name             VARCHAR(80) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_environment_key_per_org UNIQUE (organization_id, key)
);

CREATE TABLE feature_flags (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    key              VARCHAR(100) NOT NULL,
    name             VARCHAR(120) NOT NULL,
    description      TEXT,
    archived         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by       UUID         NOT NULL REFERENCES users (id),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_flag_key_per_org UNIQUE (organization_id, key)
);

-- One row per (flag, environment): the same flag can be ON in staging and OFF in production.
CREATE TABLE flag_environment_configs (
    id                  UUID PRIMARY KEY,
    flag_id             UUID        NOT NULL REFERENCES feature_flags (id) ON DELETE CASCADE,
    environment_id      UUID        NOT NULL REFERENCES environments (id) ON DELETE CASCADE,
    enabled             BOOLEAN     NOT NULL DEFAULT FALSE,
    rollout_percentage  INTEGER     NOT NULL DEFAULT 0,
    version             BIGINT      NOT NULL DEFAULT 0,   -- optimistic locking
    updated_by          UUID        REFERENCES users (id),
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_rollout_range CHECK (rollout_percentage BETWEEN 0 AND 100),
    CONSTRAINT uq_flag_environment UNIQUE (flag_id, environment_id)
);

CREATE TABLE audit_logs (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    actor_id         UUID         REFERENCES users (id),
    entity_type      VARCHAR(60)  NOT NULL,
    entity_id        UUID         NOT NULL,
    action           VARCHAR(40)  NOT NULL,
    old_value        JSONB,
    new_value        JSONB,
    created_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_audit_org_created ON audit_logs (organization_id, created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs (organization_id, entity_type, entity_id);
