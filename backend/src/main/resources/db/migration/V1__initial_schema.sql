-- =============================================================================
-- DeployForge AI - initial schema
--
-- Flyway owns the schema. Hibernate runs with ddl-auto=validate and must never
-- generate or alter tables.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Users & GitHub identity
-- ----------------------------------------------------------------------------
CREATE TABLE users
(
    id              UUID PRIMARY KEY,
    name            VARCHAR(255),
    email           VARCHAR(320),
    avatar_url      VARCHAR(1024),
    github_user_id  BIGINT       NOT NULL,
    github_username VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_users_github_user_id UNIQUE (github_user_id)
);

CREATE INDEX ix_users_github_username ON users (github_username);

-- OAuth access tokens are encrypted at the application layer (AES-256-GCM)
-- before they ever reach this table.
CREATE TABLE github_connections
(
    id                     UUID PRIMARY KEY,
    user_id                UUID        NOT NULL,
    github_account_id      BIGINT      NOT NULL,
    access_token_encrypted TEXT        NOT NULL,
    token_type             VARCHAR(32),
    scopes                 VARCHAR(512),
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_github_connection_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ux_github_connection_user UNIQUE (user_id)
);

-- ----------------------------------------------------------------------------
-- Workspaces & membership (RBAC)
-- ----------------------------------------------------------------------------
CREATE TABLE workspaces
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    slug       VARCHAR(120) NOT NULL,
    owner_id   UUID         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_workspace_slug UNIQUE (slug),
    CONSTRAINT fk_workspace_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE workspace_members
(
    id           UUID PRIMARY KEY,
    workspace_id UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    role         VARCHAR(32) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workspace_member_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_member_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ux_workspace_member UNIQUE (workspace_id, user_id)
);

CREATE INDEX ix_workspace_member_workspace_user ON workspace_members (workspace_id, user_id);
CREATE INDEX ix_workspace_member_user ON workspace_members (user_id);

-- ----------------------------------------------------------------------------
-- Projects
-- ----------------------------------------------------------------------------
CREATE TABLE projects
(
    id                 UUID PRIMARY KEY,
    workspace_id       UUID         NOT NULL,
    name               VARCHAR(120) NOT NULL,
    slug               VARCHAR(120) NOT NULL,
    description        VARCHAR(1000),
    repository_owner   VARCHAR(255) NOT NULL,
    repository_name    VARCHAR(255) NOT NULL,
    repository_url     VARCHAR(1024) NOT NULL,
    repository_id      BIGINT,
    repository_private BOOLEAN      NOT NULL DEFAULT FALSE,
    default_branch     VARCHAR(255) NOT NULL,
    framework          VARCHAR(64),
    runtime            VARCHAR(64),
    status             VARCHAR(32)  NOT NULL,
    root_directory     VARCHAR(512),
    install_command    VARCHAR(1024),
    build_command      VARCHAR(1024),
    start_command      VARCHAR(1024),
    dockerfile_path    VARCHAR(512),
    container_port     INTEGER,
    health_check_path  VARCHAR(512),
    created_by         UUID,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_project_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ux_project_slug UNIQUE (workspace_id, slug)
);

CREATE INDEX ix_project_workspace ON projects (workspace_id, created_at DESC);
CREATE INDEX ix_project_repository ON projects (repository_owner, repository_name);

-- ----------------------------------------------------------------------------
-- Environments & encrypted variables
-- ----------------------------------------------------------------------------
CREATE TABLE environments
(
    id                  UUID PRIMARY KEY,
    project_id          UUID        NOT NULL,
    name                VARCHAR(64) NOT NULL,
    type                VARCHAR(32) NOT NULL,
    branch              VARCHAR(255) NOT NULL,
    auto_deploy_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_environment_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ux_environment_name UNIQUE (project_id, name)
);

CREATE INDEX ix_environment_project ON environments (project_id);

CREATE TABLE environment_variables
(
    id              UUID PRIMARY KEY,
    environment_id  UUID         NOT NULL,
    env_key         VARCHAR(255) NOT NULL,
    encrypted_value TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_env_var_environment FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE CASCADE,
    CONSTRAINT ux_env_var_key UNIQUE (environment_id, env_key)
);

CREATE INDEX ix_env_var_environment_key ON environment_variables (environment_id, env_key);

-- ----------------------------------------------------------------------------
-- Deployments
-- ----------------------------------------------------------------------------
CREATE TABLE deployments
(
    id                     UUID PRIMARY KEY,
    project_id             UUID         NOT NULL,
    environment_id         UUID         NOT NULL,
    deployment_number      INTEGER      NOT NULL,
    commit_sha             VARCHAR(64),
    commit_message         VARCHAR(2000),
    commit_author          VARCHAR(255),
    branch                 VARCHAR(255) NOT NULL,
    trigger_type           VARCHAR(32)  NOT NULL,
    status                 VARCHAR(32)  NOT NULL,
    framework              VARCHAR(64),
    runtime                VARCHAR(64),
    root_directory         VARCHAR(512),
    install_command        VARCHAR(1024),
    build_command          VARCHAR(1024),
    start_command          VARCHAR(1024),
    dockerfile_path        VARCHAR(512),
    container_port         INTEGER,
    host_port              INTEGER,
    image_tag              VARCHAR(512),
    container_id           VARCHAR(128),
    deployment_url         VARCHAR(1024),
    failure_stage          VARCHAR(32),
    failure_message        VARCHAR(4000),
    exit_code              INTEGER,
    source_deployment_id   UUID,
    promoted               BOOLEAN      NOT NULL DEFAULT FALSE,
    cancellation_requested BOOLEAN      NOT NULL DEFAULT FALSE,
    queued_at              TIMESTAMPTZ,
    started_at             TIMESTAMPTZ,
    finished_at            TIMESTAMPTZ,
    duration_ms            BIGINT,
    created_by             UUID,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_deployment_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_deployment_environment FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE CASCADE,
    CONSTRAINT fk_deployment_source FOREIGN KEY (source_deployment_id) REFERENCES deployments (id) ON DELETE SET NULL,
    CONSTRAINT fk_deployment_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ux_deployment_number UNIQUE (project_id, deployment_number)
);

CREATE INDEX ix_deployment_project_created ON deployments (project_id, created_at DESC);
CREATE INDEX ix_deployment_environment_created ON deployments (environment_id, created_at DESC);
CREATE INDEX ix_deployment_status ON deployments (status);

-- Timeline entries. One row per pipeline step, used by the deployment timeline UI.
CREATE TABLE deployment_steps
(
    id              UUID PRIMARY KEY,
    deployment_id   UUID        NOT NULL,
    step            VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    sequence_number INTEGER     NOT NULL,
    detail          VARCHAR(2000),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    duration_ms     BIGINT,
    CONSTRAINT fk_deployment_step_deployment FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE CASCADE,
    CONSTRAINT ux_deployment_step UNIQUE (deployment_id, step)
);

CREATE INDEX ix_deployment_step_order ON deployment_steps (deployment_id, sequence_number);

CREATE TABLE deployment_logs
(
    id              BIGSERIAL PRIMARY KEY,
    deployment_id   UUID        NOT NULL,
    logged_at       TIMESTAMPTZ NOT NULL,
    level           VARCHAR(16) NOT NULL,
    source          VARCHAR(32) NOT NULL,
    message         TEXT        NOT NULL,
    sequence_number BIGINT      NOT NULL,
    CONSTRAINT fk_deployment_log_deployment FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE CASCADE,
    CONSTRAINT ux_deployment_log_sequence UNIQUE (deployment_id, sequence_number)
);

CREATE INDEX ix_deployment_log_cursor ON deployment_logs (deployment_id, sequence_number);

-- ----------------------------------------------------------------------------
-- AI failure analysis
-- ----------------------------------------------------------------------------
CREATE TABLE deployment_analyses
(
    id                    UUID PRIMARY KEY,
    deployment_id         UUID        NOT NULL,
    status                VARCHAR(32) NOT NULL,
    summary               TEXT,
    root_cause            TEXT,
    evidence              TEXT,
    suggested_fixes       TEXT,
    severity              VARCHAR(16),
    confidence            DOUBLE PRECISION,
    provider              VARCHAR(64),
    model                 VARCHAR(128),
    raw_provider_response TEXT,
    error_message         VARCHAR(1000),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_analysis_deployment FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE CASCADE,
    CONSTRAINT ux_analysis_deployment UNIQUE (deployment_id)
);

-- ----------------------------------------------------------------------------
-- Routing
-- ----------------------------------------------------------------------------
CREATE TABLE project_domains
(
    id             UUID PRIMARY KEY,
    project_id     UUID         NOT NULL,
    environment_id UUID,
    hostname       VARCHAR(255) NOT NULL,
    target_port    INTEGER,
    kind           VARCHAR(32)  NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_domain_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_domain_environment FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE CASCADE,
    CONSTRAINT ux_domain_hostname UNIQUE (hostname)
);

-- ----------------------------------------------------------------------------
-- Monitoring samples (aggregated, retention enforced by the cleanup job)
-- ----------------------------------------------------------------------------
CREATE TABLE deployment_metrics
(
    id                 BIGSERIAL PRIMARY KEY,
    deployment_id      UUID        NOT NULL,
    project_id         UUID        NOT NULL,
    sampled_at         TIMESTAMPTZ NOT NULL,
    cpu_percent        DOUBLE PRECISION,
    memory_bytes       BIGINT,
    memory_limit_bytes BIGINT,
    restart_count      INTEGER,
    container_status   VARCHAR(32),
    CONSTRAINT fk_metric_deployment FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE CASCADE
);

CREATE INDEX ix_deployment_metric_recent ON deployment_metrics (deployment_id, sampled_at DESC);

-- ----------------------------------------------------------------------------
-- Notifications & activity
-- ----------------------------------------------------------------------------
CREATE TABLE notifications
(
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL,
    type          VARCHAR(48)  NOT NULL,
    title         VARCHAR(255) NOT NULL,
    message       VARCHAR(2000),
    is_read       BOOLEAN      NOT NULL DEFAULT FALSE,
    project_id    UUID,
    deployment_id UUID,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_notification_user_created ON notifications (user_id, created_at DESC);

CREATE TABLE activity_events
(
    id            UUID PRIMARY KEY,
    workspace_id  UUID,
    project_id    UUID,
    actor_id      UUID,
    actor_name    VARCHAR(255),
    action        VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id   VARCHAR(128),
    metadata      TEXT,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_activity_workspace_created ON activity_events (workspace_id, created_at DESC);
CREATE INDEX ix_activity_project_created ON activity_events (project_id, created_at DESC);

-- ----------------------------------------------------------------------------
-- Webhook idempotency (GitHub delivery ids)
-- ----------------------------------------------------------------------------
CREATE TABLE webhook_deliveries
(
    id                   UUID PRIMARY KEY,
    delivery_id          VARCHAR(128) NOT NULL,
    event                VARCHAR(64)  NOT NULL,
    action               VARCHAR(64),
    repository_full_name VARCHAR(512),
    processed            BOOLEAN      NOT NULL DEFAULT FALSE,
    result               VARCHAR(255),
    received_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_webhook_delivery UNIQUE (delivery_id)
);

CREATE INDEX ix_webhook_delivery_received ON webhook_deliveries (received_at DESC);
