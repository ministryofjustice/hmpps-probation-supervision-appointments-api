ALTER TABLE notification_mappings
    ADD COLUMN crn               TEXT NULL,
    ADD COLUMN status            TEXT NULL,
    ADD COLUMN status_updated_at TIMESTAMP WITH TIME ZONE NULL;

-- Supports the status-check listener/batch job's "find unresolved rows" query
CREATE INDEX idx_notification_mappings_status_unresolved
    ON notification_mappings (created_at)
    WHERE status IS NULL OR status IN ('created', 'sending');
