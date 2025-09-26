-- Migration: Rename delius_external_reference to supervision_appointment_urn

-- Rename the column
ALTER TABLE delius_outlook_mappings
    RENAME COLUMN delius_external_reference TO supervision_appointment_urn;

-- Drop old check constraint
ALTER TABLE delius_outlook_mappings
DROP CONSTRAINT chk_delius_ref_not_blank;

-- Add new check constraint
ALTER TABLE delius_outlook_mappings
    ADD CONSTRAINT chk_supervision_urn_not_blank CHECK (length(trim(supervision_appointment_urn)) > 0);

-- Drop old unique constraint
ALTER TABLE delius_outlook_mappings
DROP CONSTRAINT uq_delius_outlook_pair;

-- Add new unique constraint
ALTER TABLE delius_outlook_mappings
    ADD CONSTRAINT uq_supervision_outlook_pair UNIQUE (supervision_appointment_urn, outlook_id);

-- Drop old index
DROP INDEX idx_delius_outlook__delius_ref;

-- Create new index
CREATE INDEX idx_delius_outlook__supervision_urn
    ON delius_outlook_mappings (supervision_appointment_urn);
