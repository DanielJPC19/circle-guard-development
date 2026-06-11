ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS unconfirmed_fencing_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS auto_threshold_seconds      BIGINT  NOT NULL DEFAULT 3600;
