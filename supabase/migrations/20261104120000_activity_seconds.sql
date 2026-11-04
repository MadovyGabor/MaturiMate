-- The study time of a day is counted in seconds, so a short lesson counts towards the daily goal.
ALTER TABLE activity_log RENAME COLUMN minutes_studied TO seconds_studied;
ALTER TABLE activity_log ALTER COLUMN seconds_studied TYPE integer USING seconds_studied * 60;
ALTER TABLE activity_log DROP CONSTRAINT ck_activity_log_values;
ALTER TABLE activity_log ADD CONSTRAINT ck_activity_log_values
    CHECK (xp_earned >= 0 AND seconds_studied >= 0 AND tasks_answered >= 0);
