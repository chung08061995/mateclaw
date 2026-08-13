CREATE TABLE IF NOT EXISTS mate_provider_account (
    id BIGINT PRIMARY KEY,
    provider_id VARCHAR(128) NOT NULL,
    label VARCHAR(160) NOT NULL,
    external_account_id VARCHAR(256),
    auth_type VARCHAR(32) NOT NULL DEFAULT 'api_key',
    credential_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    quota_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    quota_used_percent NUMERIC(5,2),
    quota_reset_at BIGINT,
    quota_updated_at BIGINT,
    alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    alert_threshold_percent NUMERIC(5,2) NOT NULL DEFAULT 80.00,
    alert_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    last_error_code VARCHAR(128),
    last_error_message TEXT,
    last_success_at BIGINT,
    last_failure_at BIGINT,
    last_used_at BIGINT,
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    legacy_import BOOLEAN,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_provider_account_provider FOREIGN KEY (provider_id)
        REFERENCES mate_model_provider(provider_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_provider_account_pool
    ON mate_provider_account (provider_id, enabled, priority);
CREATE UNIQUE INDEX IF NOT EXISTS uk_provider_account_external
    ON mate_provider_account (provider_id, external_account_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_provider_account_legacy
    ON mate_provider_account (provider_id, legacy_import);
