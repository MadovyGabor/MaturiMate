-- Milestone 11.1: the user tables as the sync (Milestone 12) needs them, changed once while the progress tables are
-- still empty. After this migration every change to them is a migration pair (phone and server).
-- The phone side is database/src/commonMain/sqldelight/user/migrations/5.sqm. Decisions: content/DECISIONS.md, 2026-11-10.
--
-- 1. A round (attempt) has an id made by the device (uuid): a number from one phone would collide with the same
--    number from another.
-- 2. Times are `timestamptz`; the phone stores instants, and a `timestamp` without a zone cannot be compared.
-- 3. `updated_at` on the tables that are synced and can change, and `progress_reset_at` on the profile (a reset
--    cannot be seen in rows that are gone).
-- 4. The daily activity is kept per device, so two devices of one account add up exactly.
-- 5. Unused columns, the attempt type EXTRA_PRACTICE and the table user_task_progress (a cache the phone builds from
--    the answers; it is not synced) go.
-- 6. The progress tables get DELETE policies: the reset and the deletion of an account need them.

-- The tables are rebuilt in place, which loses data if there is any. The app never wrote rounds to the server
-- (the sync is not built), so they are empty; if they are not, stop and copy the rows by hand instead.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM attempt) OR EXISTS (SELECT 1 FROM attempt_answer) THEN
        RAISE EXCEPTION 'attempt or attempt_answer has rows: rebuild them with a data copy instead of this migration';
    END IF;
END;
$$;

-- ---------------------------------------------------------------------
-- 1. Rounds with ids made by the device
-- ---------------------------------------------------------------------

ALTER TABLE attempt_answer DROP CONSTRAINT fk_attempt_answer_attempt;
ALTER TABLE attempt DROP CONSTRAINT uq_attempt_user;

ALTER TABLE attempt ALTER COLUMN attempt_id DROP IDENTITY;
ALTER TABLE attempt ALTER COLUMN attempt_id TYPE uuid USING gen_random_uuid();
-- the app sends the id; the default is for rows written by hand (tests)
ALTER TABLE attempt ALTER COLUMN attempt_id SET DEFAULT gen_random_uuid();
ALTER TABLE attempt_answer ALTER COLUMN attempt_id TYPE uuid USING gen_random_uuid();

ALTER TABLE attempt ADD CONSTRAINT uq_attempt_user UNIQUE (attempt_id, user_id);
ALTER TABLE attempt_answer ADD CONSTRAINT fk_attempt_answer_attempt
    FOREIGN KEY (attempt_id, user_id) REFERENCES attempt (attempt_id, user_id) ON DELETE CASCADE;

-- EXTRA_PRACTICE was never used; the free practice took its place
ALTER TABLE attempt DROP CONSTRAINT ck_attempt_type;
ALTER TABLE attempt ADD CONSTRAINT ck_attempt_type
    CHECK (attempt_type IN ('UNIT_ROUND', 'REVIEW', 'FREE_PRACTICE', 'MISTAKE_PRACTICE'));
ALTER TABLE attempt DROP CONSTRAINT ck_attempt_scope;
ALTER TABLE attempt ADD CONSTRAINT ck_attempt_scope CHECK (
    (attempt_type = 'UNIT_ROUND'
        AND learning_unit_id IS NOT NULL AND topic_id IS NULL) OR
    (attempt_type = 'REVIEW'
        AND topic_id IS NOT NULL AND learning_unit_id IS NULL) OR
    (attempt_type IN ('FREE_PRACTICE', 'MISTAKE_PRACTICE')
        AND learning_unit_id IS NULL AND topic_id IS NULL));

-- ---------------------------------------------------------------------
-- 2. Times with a zone (the old values were UTC)
-- ---------------------------------------------------------------------

ALTER TABLE attempt
    ALTER COLUMN started_at TYPE timestamptz USING started_at AT TIME ZONE 'UTC',
    ALTER COLUMN finished_at TYPE timestamptz USING finished_at AT TIME ZONE 'UTC';
ALTER TABLE attempt_answer
    ALTER COLUMN answered_at TYPE timestamptz USING answered_at AT TIME ZONE 'UTC';
ALTER TABLE user_unit_progress
    ALTER COLUMN started_at TYPE timestamptz USING started_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE timestamptz USING completed_at AT TIME ZONE 'UTC';
ALTER TABLE user_role
    ALTER COLUMN assigned_at TYPE timestamptz USING assigned_at AT TIME ZONE 'UTC';
ALTER TABLE user_subject
    ALTER COLUMN added_at TYPE timestamptz USING added_at AT TIME ZONE 'UTC';
-- a policy that mentions withdrawn_at blocks the change of its type: it is dropped and created again as it was
DROP POLICY user_consent_insert_own ON user_consent;
ALTER TABLE user_consent
    ALTER COLUMN accepted_at TYPE timestamptz USING accepted_at AT TIME ZONE 'UTC',
    ALTER COLUMN withdrawn_at TYPE timestamptz USING withdrawn_at AT TIME ZONE 'UTC';
CREATE POLICY user_consent_insert_own ON user_consent FOR INSERT TO authenticated
    WITH CHECK (user_id = (SELECT auth.uid()) AND withdrawn_at IS NULL);

-- ---------------------------------------------------------------------
-- 5. What goes (before the new columns, so the checks are rebuilt once)
-- ---------------------------------------------------------------------

ALTER TABLE user_profile
    DROP COLUMN display_name,
    DROP COLUMN date_of_birth,
    DROP COLUMN school_type;
ALTER TABLE user_settings DROP COLUMN theme;
ALTER TABLE user_subject DROP COLUMN target_score;
-- dropping xp_earned also drops the check that mentions it
ALTER TABLE activity_log DROP COLUMN xp_earned;
ALTER TABLE activity_log ADD CONSTRAINT ck_activity_log_values
    CHECK (seconds_studied >= 0 AND tasks_answered >= 0);

-- a cache of the phone, built from attempt_answer; its policies go with it
DROP TABLE user_task_progress;

-- ---------------------------------------------------------------------
-- 3. Change times and the reset marker
-- ---------------------------------------------------------------------

ALTER TABLE user_profile
    ADD COLUMN progress_reset_at timestamptz,
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE user_settings      ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE user_unit_progress ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE user_streak        ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE activity_log       ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

-- The server stamps every update (fn_set_updated_at); which clock wins in a sync is decided in 12.1.
CREATE TRIGGER trg_user_profile_updated_at
    BEFORE UPDATE ON user_profile FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_user_settings_updated_at
    BEFORE UPDATE ON user_settings FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_user_unit_progress_updated_at
    BEFORE UPDATE ON user_unit_progress FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_user_streak_updated_at
    BEFORE UPDATE ON user_streak FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_activity_log_updated_at
    BEFORE UPDATE ON activity_log FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ---------------------------------------------------------------------
-- 4. The daily activity per device
-- ---------------------------------------------------------------------

-- rows of the old layout (one per day) get a placeholder device; the app sends the id of the installation
ALTER TABLE activity_log ADD COLUMN device_id text NOT NULL DEFAULT 'unknown';
ALTER TABLE activity_log ALTER COLUMN device_id DROP DEFAULT;
ALTER TABLE activity_log ADD CONSTRAINT ck_activity_log_device CHECK (device_id <> '');
ALTER TABLE activity_log DROP CONSTRAINT uq_activity_log_day;
ALTER TABLE activity_log ADD CONSTRAINT uq_activity_log_day UNIQUE (user_id, activity_date, device_id);

-- ---------------------------------------------------------------------
-- 6. DELETE policies of the progress tables
-- ---------------------------------------------------------------------

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['attempt', 'attempt_answer', 'user_unit_progress', 'user_streak', 'activity_log']
    LOOP
        EXECUTE format(
            'CREATE POLICY %I ON %I FOR DELETE TO authenticated USING (user_id = (SELECT auth.uid()))',
            t || '_delete_own', t);
    END LOOP;
END;
$$;
