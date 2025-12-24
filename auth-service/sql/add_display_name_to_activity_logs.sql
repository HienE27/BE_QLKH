-- Add display_name column to activity_logs
ALTER TABLE activity_logs
ADD COLUMN display_name VARCHAR(255);

-- Backfill: set display_name = username for existing rows (optional)
UPDATE activity_logs SET display_name = username WHERE display_name IS NULL;


