CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- Prevents duplicate contact events from being published.
ALTER TABLE notification_mappings
    ADD COLUMN contact_event_published_at TIMESTAMP WITH TIME ZONE NULL;
