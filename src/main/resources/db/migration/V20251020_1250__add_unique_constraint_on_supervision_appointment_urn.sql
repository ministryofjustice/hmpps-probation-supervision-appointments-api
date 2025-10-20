-- Add unique constraint on supervision_appointment_urn
ALTER TABLE delius_outlook_mappings
    ADD CONSTRAINT uq_supervision_urn UNIQUE (supervision_appointment_urn);

