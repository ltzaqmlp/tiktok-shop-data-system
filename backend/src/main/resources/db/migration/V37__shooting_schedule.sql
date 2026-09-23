ALTER TABLE shooting_ticket RENAME COLUMN deadline TO planned_end;
ALTER TABLE shooting_ticket ADD COLUMN planned_start timestamptz;
ALTER TABLE shooting_ticket ADD CONSTRAINT shooting_ticket_planned_time_check
  CHECK (planned_start IS NULL OR planned_start < planned_end);
