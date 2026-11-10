-- Targeting rules per (flag, environment) and API keys used by external applications.

CREATE TABLE flag_rules (
    id              UUID PRIMARY KEY,
    flag_config_id  UUID         NOT NULL REFERENCES flag_environment_configs (id) ON DELETE CASCADE,
    priority        INTEGER      NOT NULL,
    rule_type       VARCHAR(20)  NOT NULL,
    attribute       VARCHAR(80),
    operator        VARCHAR(20),
    rule_values     JSONB        NOT NULL,
    serve_enabled   BOOLEAN      NOT NULL,
    description     VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_rule_type CHECK (rule_type IN ('USER_LIST', 'GROUP', 'ATTRIBUTE'))
);
CREATE INDEX idx_flag_rules_config ON flag_rules (flag_config_id, priority);

CREATE TABLE api_keys (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    key_prefix       VARCHAR(12)  NOT NULL,
    key_hash         VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 of the key; the key itself is never stored
    created_by       UUID         NOT NULL REFERENCES users (id),
    created_at       TIMESTAMPTZ  NOT NULL,
    revoked_at       TIMESTAMPTZ
);
CREATE INDEX idx_api_keys_org ON api_keys (organization_id, created_at DESC);
