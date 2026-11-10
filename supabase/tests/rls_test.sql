-- Row level security tests. Run on a database with the auth stand-in
-- (dbServer/postgres/00_auth_stub.sql) and all migrations, as a superuser:
--   psql -v ON_ERROR_STOP=1 -f supabase/tests/rls_test.sql
-- Each check raises an exception when it fails. Everything runs in one
-- transaction that is rolled back, so the database stays unchanged.

BEGIN;

-- Three users: A and B are students, C is a curator (given by the server)
INSERT INTO auth.users (id) VALUES
    ('00000000-0000-0000-0000-00000000000a'),
    ('00000000-0000-0000-0000-00000000000b'),
    ('00000000-0000-0000-0000-00000000000c');
INSERT INTO user_role (user_id, role_id) VALUES ('00000000-0000-0000-0000-00000000000c', 4);

-- One task to report on (written by the importer, which bypasses RLS)
INSERT INTO subject (code, name_hu, name_sk, track) VALUES ('T_SUBJ', 'T', 'T', 'HU');
INSERT INTO task_format (code, name, answer_mode) VALUES ('T_MC', 'T', 'CHOICE');
INSERT INTO topic (subject_id, title, order_index, is_published)
    SELECT subject_id, 'T', 1, true FROM subject WHERE code = 'T_SUBJ';
INSERT INTO task (topic_id, task_format_id, question_text)
    SELECT t.topic_id, f.task_format_id, 'Q?' FROM topic t, task_format f
    WHERE t.title = 'T' AND f.code = 'T_MC';
-- users cannot read the subject table, so its id is kept here
CREATE TEMP TABLE t_subject AS SELECT subject_id FROM subject WHERE code = 'T_SUBJ';
GRANT SELECT ON t_subject TO authenticated;

-- Switches to a signed-in user for the rest of the transaction
CREATE FUNCTION pg_temp.become(u text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE 'RESET ROLE';
    PERFORM set_config('request.jwt.claim.sub', u, true);
    EXECUTE 'SET LOCAL ROLE authenticated';
END;
$$;

-- ---- A writes and reads only their own rows -------------------------
SELECT pg_temp.become('00000000-0000-0000-0000-00000000000a');

INSERT INTO user_profile (user_id, first_name, last_name)
    VALUES ('00000000-0000-0000-0000-00000000000a', 'Anna', 'Kovács');
INSERT INTO user_settings (user_id) VALUES ('00000000-0000-0000-0000-00000000000a');

DO $$ BEGIN
    INSERT INTO user_profile (user_id, first_name, last_name)
        VALUES ('00000000-0000-0000-0000-00000000000b', 'X', 'Y');
    RAISE EXCEPTION 'FAIL: A wrote a profile for B';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;

DO $$ BEGIN
    UPDATE user_settings SET user_id = '00000000-0000-0000-0000-00000000000b'
        WHERE user_id = '00000000-0000-0000-0000-00000000000a';
    RAISE EXCEPTION 'FAIL: A moved their settings to B';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;

-- the onboarding columns: A sets their own English level and onboarding end
INSERT INTO user_subject (user_id, subject_id, level)
    SELECT '00000000-0000-0000-0000-00000000000a', subject_id, 'B2' FROM t_subject;
UPDATE user_profile SET onboarding_completed_at = now()
    WHERE user_id = '00000000-0000-0000-0000-00000000000a';
DO $$ BEGIN
    IF (SELECT level FROM user_subject) IS DISTINCT FROM 'B2'
        OR (SELECT onboarding_completed_at FROM user_profile) IS NULL THEN
        RAISE EXCEPTION 'FAIL: A could not save their level or onboarding end';
    END IF;
END $$;

-- ---- B sees nothing of A ---------------------------------------------
SELECT pg_temp.become('00000000-0000-0000-0000-00000000000b');

DO $$ BEGIN
    IF (SELECT count(*) FROM user_profile) <> 0 THEN
        RAISE EXCEPTION 'FAIL: B sees A''s profile';
    END IF;
    IF (SELECT count(*) FROM user_subject) <> 0 THEN
        RAISE EXCEPTION 'FAIL: B sees A''s subjects';
    END IF;
    UPDATE user_profile SET first_name = 'Hacked';
    UPDATE user_subject SET level = 'B1';
END $$;

-- ---- Roles: student, learner, teacher only ---------------------------
SELECT pg_temp.become('00000000-0000-0000-0000-00000000000a');

DO $$ BEGIN
    IF (SELECT level FROM user_subject) IS DISTINCT FROM 'B2' THEN
        RAISE EXCEPTION 'FAIL: B changed A''s English level';
    END IF;
END $$;

INSERT INTO user_role (user_id, role_id) VALUES ('00000000-0000-0000-0000-00000000000a', 1);
DO $$ BEGIN
    INSERT INTO user_role (user_id, role_id) VALUES ('00000000-0000-0000-0000-00000000000a', 5);
    RAISE EXCEPTION 'FAIL: A made themselves admin';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;

-- ---- Consents: added and withdrawn, never rewritten ------------------
INSERT INTO user_consent (user_id, consent_type, document_version)
    VALUES ('00000000-0000-0000-0000-00000000000a', 'TERMS', '1.0');
UPDATE user_consent SET withdrawn_at = now() + interval '1 minute'
    WHERE user_id = '00000000-0000-0000-0000-00000000000a';
DO $$ BEGIN
    UPDATE user_consent SET document_version = '0.1'
        WHERE user_id = '00000000-0000-0000-0000-00000000000a';
    RAISE EXCEPTION 'FAIL: A rewrote a consent';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;

-- ---- Content: not readable or writable through the API --------------
DO $$ BEGIN
    IF (SELECT count(*) FROM task) <> 0 THEN
        RAISE EXCEPTION 'FAIL: a user reads the task bank';
    END IF;
END $$;
DO $$ BEGIN
    INSERT INTO subject (code, name_hu, name_sk, track) VALUES ('X', 'x', 'x', 'HU');
    RAISE EXCEPTION 'FAIL: a user wrote content';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;

-- ---- Content reports: A reports, cannot resolve; C resolves ----------
RESET ROLE;
CREATE TEMP TABLE t_ids AS SELECT task_id FROM task WHERE question_text = 'Q?';
GRANT SELECT ON t_ids TO authenticated;
SELECT pg_temp.become('00000000-0000-0000-0000-00000000000a');

INSERT INTO content_report (reporter_id, task_id, report_type, message)
    SELECT '00000000-0000-0000-0000-00000000000a', task_id, 'TYPO', 'Elírás' FROM t_ids;
DO $$
DECLARE n int;
BEGIN
    UPDATE content_report SET status = 'RESOLVED';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN RAISE EXCEPTION 'FAIL: A resolved their own report'; END IF;
END $$;

SELECT pg_temp.become('00000000-0000-0000-0000-00000000000b');
DO $$ BEGIN
    IF (SELECT count(*) FROM content_report) <> 0 THEN
        RAISE EXCEPTION 'FAIL: B sees A''s report';
    END IF;
END $$;

SELECT pg_temp.become('00000000-0000-0000-0000-00000000000c');
DO $$
DECLARE n int;
BEGIN
    UPDATE content_report SET status = 'RESOLVED', resolved_at = now() + interval '1 minute',
        resolved_by_id = '00000000-0000-0000-0000-00000000000c';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 1 THEN RAISE EXCEPTION 'FAIL: the curator could not resolve the report'; END IF;
END $$;

-- ---- save_sign_up: the end of a sign-up in one call -----------------
SELECT pg_temp.become('00000000-0000-0000-0000-00000000000b');

SELECT save_sign_up('Béla', 'Nagy', 'HU', DATE '2027-03-08', 'HU', 'Europe/Bratislava', 10::smallint,
    true, TIME '18:00', 1::smallint, '[{"subject_id": 1, "source": "AUTO", "level": null},
    {"subject_id": 6, "source": "AUTO", "level": null}]'::jsonb,
    ARRAY['TERMS', 'PRIVACY', 'AGE_16'], '1.0', now());
DO $$ BEGIN
    IF (SELECT first_name FROM user_profile) IS DISTINCT FROM 'Béla'
        OR (SELECT onboarding_completed_at FROM user_profile) IS NULL
        OR (SELECT reminder_time FROM user_settings) IS DISTINCT FROM TIME '18:00'
        OR (SELECT array_agg(role_id) FROM user_role) IS DISTINCT FROM ARRAY[1::smallint]
        OR (SELECT count(*) FROM user_subject) <> 2
        OR (SELECT count(*) FROM user_consent) <> 3 THEN
        RAISE EXCEPTION 'FAIL: save_sign_up did not write everything';
    END IF;
END $$;

-- a retry (the answer got lost) replaces the answers and keeps one copy of each consent
SELECT save_sign_up('Béla', 'Nagy', 'HU', DATE '2027-03-08', 'HU', 'Europe/Bratislava', 15::smallint,
    false, NULL, 2::smallint, '[{"subject_id": 4, "source": "CHOSEN", "level": null}]'::jsonb,
    ARRAY['TERMS', 'PRIVACY', 'AGE_16'], '1.0', now());
DO $$ BEGIN
    IF (SELECT count(*) FROM user_consent) <> 3
        OR (SELECT array_agg(role_id) FROM user_role) IS DISTINCT FROM ARRAY[2::smallint]
        OR (SELECT array_agg(subject_id) FROM user_subject) IS DISTINCT FROM ARRAY[4::smallint]
        OR (SELECT daily_goal_minutes FROM user_settings) <> 15 THEN
        RAISE EXCEPTION 'FAIL: a retry of save_sign_up duplicated or kept old rows';
    END IF;
END $$;

-- a withdrawn consent can be given again
UPDATE user_consent SET withdrawn_at = now() + interval '1 minute' WHERE consent_type = 'TERMS';
SELECT save_sign_up('Béla', 'Nagy', 'HU', NULL, 'HU', 'Europe/Bratislava', 15::smallint,
    false, NULL, 2::smallint, '[]'::jsonb, ARRAY['TERMS'], '1.0', now());
DO $$ BEGIN
    IF (SELECT count(*) FROM user_consent WHERE consent_type = 'TERMS') <> 2 THEN
        RAISE EXCEPTION 'FAIL: a withdrawn consent could not be given again';
    END IF;
END $$;

-- the function cannot give a server role, and writes nothing of another user
DO $$ BEGIN
    PERFORM save_sign_up('Béla', 'Nagy', 'HU', NULL, 'HU', 'Europe/Bratislava', 15::smallint,
        false, NULL, 5::smallint, '[]'::jsonb, ARRAY[]::text[], '1.0', now());
    RAISE EXCEPTION 'FAIL: save_sign_up made B an admin';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;
SELECT pg_temp.become('00000000-0000-0000-0000-00000000000a');
DO $$ BEGIN
    IF (SELECT first_name FROM user_profile) IS DISTINCT FROM 'Anna' THEN
        RAISE EXCEPTION 'FAIL: save_sign_up of B changed A';
    END IF;
END $$;

-- ---- The answers of a round: each user's own -------------------------
RESET ROLE;
INSERT INTO learning_unit (topic_id, unit_type, title, practice_kind, estimated_minutes, sort_order, is_published)
    SELECT topic_id, 'PRACTICE', 'T', 'MINI', 1, 1, true FROM topic WHERE title = 'T';
CREATE TEMP TABLE t_unit AS SELECT learning_unit_id FROM learning_unit WHERE title = 'T';
GRANT SELECT ON t_unit TO authenticated;
CREATE TEMP TABLE t_task AS SELECT task_id FROM task WHERE question_text = 'Q?';
GRANT SELECT ON t_task TO authenticated;

SELECT pg_temp.become('00000000-0000-0000-0000-00000000000a');
INSERT INTO attempt (user_id, attempt_type, learning_unit_id, earned_points, max_points)
    SELECT '00000000-0000-0000-0000-00000000000a', 'UNIT_ROUND', learning_unit_id, 1, 1 FROM t_unit;
INSERT INTO attempt_answer (attempt_id, task_id, user_id, is_correct, earned_points, max_points)
    SELECT a.attempt_id, t.task_id, '00000000-0000-0000-0000-00000000000a', true, 1, 1 FROM attempt a, t_task t;

-- a points value above the maximum is refused
DO $$ BEGIN
    UPDATE attempt_answer SET earned_points = 5;
    RAISE EXCEPTION 'FAIL: more points than possible were accepted';
EXCEPTION WHEN check_violation THEN NULL;
END $$;
-- A can change their own answer
UPDATE attempt_answer SET is_correct = false;
DO $$ BEGIN
    IF (SELECT is_correct FROM attempt_answer) THEN RAISE EXCEPTION 'FAIL: A could not update their answer'; END IF;
END $$;

RESET ROLE;
CREATE TEMP TABLE t_attempt AS SELECT attempt_id FROM attempt;
GRANT SELECT ON t_attempt TO authenticated;

SELECT pg_temp.become('00000000-0000-0000-0000-00000000000b');
DO $$ BEGIN
    IF (SELECT count(*) FROM attempt_answer) <> 0 THEN RAISE EXCEPTION 'FAIL: B reads the answers of A'; END IF;
END $$;
-- B cannot write an answer in the name of A ...
DO $$ BEGIN
    INSERT INTO attempt_answer (attempt_id, task_id, user_id, is_correct, earned_points, max_points)
        SELECT a.attempt_id, t.task_id, '00000000-0000-0000-0000-00000000000a', true, 1, 1 FROM t_attempt a, t_task t;
    RAISE EXCEPTION 'FAIL: B wrote an answer for A';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;
-- (the answer of A is removed first: with it in place the primary key refuses the next insert before the foreign key is checked)
RESET ROLE;
DELETE FROM attempt_answer;
SELECT pg_temp.become('00000000-0000-0000-0000-00000000000b');
-- ... nor under the attempt of A in their own name (the foreign key ties an answer to the attempt's user)
DO $$ BEGIN
    INSERT INTO attempt_answer (attempt_id, task_id, user_id, is_correct, earned_points, max_points)
        SELECT a.attempt_id, t.task_id, '00000000-0000-0000-0000-00000000000b', true, 1, 1 FROM t_attempt a, t_task t;
    RAISE EXCEPTION 'FAIL: B wrote an answer under the attempt of A';
EXCEPTION WHEN foreign_key_violation THEN NULL;
END $$;

SELECT pg_temp.become('00000000-0000-0000-0000-00000000000a');

-- ---- Practice rounds: no unit and no topic ---------------------------
INSERT INTO attempt (user_id, attempt_type) VALUES ('00000000-0000-0000-0000-00000000000a', 'FREE_PRACTICE');
INSERT INTO attempt (user_id, attempt_type) VALUES ('00000000-0000-0000-0000-00000000000a', 'MISTAKE_PRACTICE');
-- a practice round that belongs to a unit is refused
DO $$ BEGIN
    INSERT INTO attempt (user_id, attempt_type, learning_unit_id)
        SELECT '00000000-0000-0000-0000-00000000000a', 'FREE_PRACTICE', learning_unit_id FROM t_unit;
    RAISE EXCEPTION 'FAIL: a free practice round with a unit was accepted';
EXCEPTION WHEN check_violation THEN NULL;
END $$;
-- and a unit round without a unit is still refused
DO $$ BEGIN
    INSERT INTO attempt (user_id, attempt_type) VALUES ('00000000-0000-0000-0000-00000000000a', 'UNIT_ROUND');
    RAISE EXCEPTION 'FAIL: a unit round without a unit was accepted';
EXCEPTION WHEN check_violation THEN NULL;
END $$;

-- ---- The progress tables: each user's own, deletes and change times ---
INSERT INTO user_unit_progress (user_id, learning_unit_id, status, completed_at)
    SELECT '00000000-0000-0000-0000-00000000000a', learning_unit_id, 'COMPLETED', now() FROM t_unit;
INSERT INTO user_streak (user_id, current_streak, longest_streak, last_activity_date)
    VALUES ('00000000-0000-0000-0000-00000000000a', 1, 1, current_date);
INSERT INTO activity_log (user_id, activity_date, device_id, seconds_studied)
    VALUES ('00000000-0000-0000-0000-00000000000a', current_date, 'phone', 60),
           ('00000000-0000-0000-0000-00000000000a', current_date, 'desktop', 90);
-- one row per device and day
DO $$ BEGIN
    INSERT INTO activity_log (user_id, activity_date, device_id)
        VALUES ('00000000-0000-0000-0000-00000000000a', current_date, 'phone');
    RAISE EXCEPTION 'FAIL: a second row for the same device and day was accepted';
EXCEPTION WHEN unique_violation THEN NULL;
END $$;
DO $$ BEGIN
    INSERT INTO activity_log (user_id, activity_date, device_id)
        VALUES ('00000000-0000-0000-0000-00000000000a', current_date - 1, '');
    RAISE EXCEPTION 'FAIL: an empty device id was accepted';
EXCEPTION WHEN check_violation THEN NULL;
END $$;
-- the server stamps the change time, whatever the client sends
UPDATE user_streak SET current_streak = 2, longest_streak = 2, updated_at = '2000-01-01';
DO $$ BEGIN
    IF (SELECT updated_at FROM user_streak) < now() - interval '1 minute' THEN
        RAISE EXCEPTION 'FAIL: updated_at did not move on an update';
    END IF;
END $$;

SELECT pg_temp.become('00000000-0000-0000-0000-00000000000b');
DO $$ BEGIN
    IF (SELECT count(*) FROM user_unit_progress) + (SELECT count(*) FROM user_streak)
        + (SELECT count(*) FROM activity_log) + (SELECT count(*) FROM attempt) <> 0 THEN
        RAISE EXCEPTION 'FAIL: B reads the progress of A';
    END IF;
END $$;
-- B deletes nothing of A (a policy filters the rows; no error, no effect)
DELETE FROM attempt_answer;
DELETE FROM attempt;
DELETE FROM user_unit_progress;
DELETE FROM user_streak;
DELETE FROM activity_log;

SELECT pg_temp.become('00000000-0000-0000-0000-00000000000a');
DO $$ BEGIN
    IF (SELECT count(*) FROM attempt) = 0 OR (SELECT count(*) FROM user_unit_progress) = 0
        OR (SELECT count(*) FROM user_streak) = 0 OR (SELECT count(*) FROM activity_log) <> 2 THEN
        RAISE EXCEPTION 'FAIL: B deleted rows of A';
    END IF;
END $$;
-- A deletes their own progress, and the answers go with their rounds
DELETE FROM attempt;
DELETE FROM user_unit_progress;
DELETE FROM user_streak;
DELETE FROM activity_log;
DO $$ BEGIN
    IF (SELECT count(*) FROM attempt) + (SELECT count(*) FROM attempt_answer) + (SELECT count(*) FROM user_unit_progress)
        + (SELECT count(*) FROM user_streak) + (SELECT count(*) FROM activity_log) <> 0 THEN
        RAISE EXCEPTION 'FAIL: A could not delete their own progress';
    END IF;
END $$;

-- ---- Not signed in: nothing ------------------------------------------
RESET ROLE;
SET LOCAL ROLE anon;
DO $$ BEGIN
    PERFORM count(*) FROM user_profile;
    RAISE EXCEPTION 'FAIL: anon reads profiles';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;
DO $$ BEGIN
    PERFORM save_sign_up('X', 'Y', 'HU', NULL, 'HU', 'Europe/Bratislava', 10::smallint,
        false, NULL, 1::smallint, '[]'::jsonb, ARRAY[]::text[], '1.0', now());
    RAISE EXCEPTION 'FAIL: anon called save_sign_up';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;

RESET ROLE;
SELECT 'RLS tests OK' AS result;
ROLLBACK;
