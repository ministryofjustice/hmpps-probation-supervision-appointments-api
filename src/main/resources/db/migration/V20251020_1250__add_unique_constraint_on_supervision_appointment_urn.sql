-- Add unique constraint on supervision_appointment_urn
ALTER TABLE delius_outlook_mappings
    ADD CONSTRAINT uq_supervision_urn UNIQUE (supervision_appointment_urn);

-- Create index on supervision_appointment_urn
CREATE INDEX idx_delius_outlook__supervision_urn
    ON delius_outlook_mappings (supervision_appointment_urn);
