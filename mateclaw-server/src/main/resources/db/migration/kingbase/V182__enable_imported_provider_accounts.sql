-- Keep provider catalog visibility separate from account-pool availability.
UPDATE mate_provider_account
SET enabled = TRUE,
    status = CASE
        WHEN quota_status = 'EXHAUSTED' THEN 'EXHAUSTED'
        WHEN quota_status = 'RATE_LIMITED' THEN 'COOLDOWN'
        ELSE 'AVAILABLE'
    END,
    update_time = CURRENT_TIMESTAMP
WHERE legacy_import = TRUE;
