-- V181 copied existing configured credentials into the account pool. Provider
-- catalog visibility is a separate concern, so those credentials should be
-- available for attribution and account-level failover immediately.
UPDATE mate_provider_account
SET enabled = TRUE,
    status = CASE
        WHEN quota_status = 'EXHAUSTED' THEN 'EXHAUSTED'
        WHEN quota_status = 'RATE_LIMITED' THEN 'COOLDOWN'
        ELSE 'AVAILABLE'
    END,
    update_time = CURRENT_TIMESTAMP
WHERE legacy_import = TRUE;
