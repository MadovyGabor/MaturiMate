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

-- ---- B sees nothing of A ---------------------------------------------
SELECT pg_temp.become('00000000-0000-0000-0000-00000000000b');

DO $$ BEGIN
    IF (SELECT count(*) FROM user_profile) <> 0 THEN
        RAISE EXCEPTION 'FAIL: B sees A''s profile';
    END IF;
    UPDATE user_profile SET first_name = 'Hacked';
END $$;

-- ---- Roles: student, learner, teacher only ---------------------------
SELECT pg_temp.become('00000000-0000-0000-0000-00000000000a');

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

-- ---- Not signed in: nothing ------------------------------------------
RESET ROLE;
SET LOCAL ROLE anon;
DO $$ BEGIN
    PERFORM count(*) FROM user_profile;
    RAISE EXCEPTION 'FAIL: anon reads profiles';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;

RESET ROLE;
SELECT 'RLS tests OK' AS result;
ROLLBACK;
