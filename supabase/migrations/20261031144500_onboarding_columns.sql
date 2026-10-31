-- Two columns for the onboarding rework (DECISIONS 2026-10-31).
-- Both are nullable, so existing rows stay valid. The row level security
-- policies are per row, so they cover the new columns without a change.

-- The level of a subject that has levels (English: B1 or B2); empty for the others.
ALTER TABLE user_subject ADD COLUMN level varchar(2)
    CONSTRAINT ck_user_subject_level CHECK (level IN ('B1', 'B2'));

-- When the user finished the onboarding; empty while it is not finished.
ALTER TABLE user_profile ADD COLUMN onboarding_completed_at timestamptz;
