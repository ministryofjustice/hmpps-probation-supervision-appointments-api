-- Migration: Rename delius_external_reference to supervision_appointment_urn

-- Rename the column
ALTER TABLE delius_outlook_mappings
    RENAME COLUMN delius_external_reference TO supervision_appointment_urn;

-- Rename check constraint
ALTER TABLE delius_outlook_mappings
    RENAME CONSTRAINT chk_delius_ref_not_blank TO chk_supervision_urn_not_blank;

-- Rename unique constraint
ALTER TABLE delius_outlook_mappings
    RENAME CONSTRAINT uq_delius_outlook_pair TO uq_supervision_outlook_pair;

-- Rename index
ALTER INDEX idx_delius_outlook__delius_ref
    RENAME TO idx_delius_outlook__supervision_urn;
