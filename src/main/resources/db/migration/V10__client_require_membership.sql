-- Per-client membership policy.
-- true  (default) — only assigned users may authorize with the client
-- false — any enabled user may authorize; membership list is optional
ALTER TABLE clients
    ADD COLUMN require_membership BOOLEAN;

UPDATE clients
    SET require_membership = TRUE
    WHERE require_membership IS NULL;

ALTER TABLE clients
    ALTER COLUMN require_membership SET NOT NULL,
    ALTER COLUMN require_membership SET DEFAULT TRUE;
