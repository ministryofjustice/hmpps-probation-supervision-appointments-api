-- Supports GET /sms/status/latest/crn/{crn}.
-- Finds the newest SMS notification mapping for a CRN efficiently.
CREATE INDEX idx_notification_mappings_crn_created_at
    ON notification_mappings (crn, created_at DESC);