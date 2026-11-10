ALTER TABLE scores ADD COLUMN submitted_at INTEGER NOT NULL DEFAULT 0;
UPDATE scores SET submitted_at = at;
