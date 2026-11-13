-- Tests of sync_user_data and reset_progress (step 12.3a). Run like rls_test.sql, after all migrations, as a superuser:
--   psql -v ON_ERROR_STOP=1 -f supabase/tests/sync_test.sql
-- Each check raises an exception when it fails; everything is rolled back at the end.

BEGIN;

INSERT INTO auth.users (id) VALUES
    ('00000000-0000-0000-0000-0000000000e1'),
    ('00000000-0000-0000-0000-0000000000e2');

-- content (the importer writes it, bypassing RLS)
INSERT INTO subject (code, name_hu, name_sk, track) VALUES ('S_SYNC', 'S', 'S', 'HU');
INSERT INTO task_format (code, name, answer_mode) VALUES ('S_MC', 'S', 'CHOICE');
INSERT INTO topic (subject_id, title, order_index, is_published)
    SELECT subject_id, 'S', 1, true FROM subject WHERE code = 'S_SYNC';
INSERT INTO task (topic_id, task_format_id, question_text)
    SELECT t.topic_id, f.task_format_id, 'Q?' FROM topic t, task_format f WHERE t.title = 'S' AND f.code = 'S_MC';
INSERT INTO learning_unit (topic_id, unit_type, title, practice_kind, estimated_minutes, sort_order, is_published)
    SELECT topic_id, 'PRACTICE', 'S', 'TOPIC', 3, 1, true FROM topic WHERE title = 'S';
CREATE TEMP TABLE t_ids AS
    SELECT (SELECT subject_id FROM subject WHERE code = 'S_SYNC') AS subject_id,
           (SELECT topic_id FROM topic WHERE title = 'S') AS topic_id,
           (SELECT learning_unit_id FROM learning_unit WHERE title = 'S') AS unit_id,
           (SELECT task_id FROM task WHERE question_text = 'Q?' AND topic_id = (SELECT topic_id FROM topic WHERE title = 'S')) AS task_id;
GRANT SELECT ON t_ids TO authenticated;

CREATE FUNCTION pg_temp.become(u text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE 'RESET ROLE';
    PERFORM set_config('request.jwt.claim.sub', u, true);
    EXECUTE 'SET LOCAL ROLE authenticated';
END;
$$;

-- epoch milliseconds, minutes from now
CREATE FUNCTION pg_temp.ms(minutes integer) RETURNS bigint LANGUAGE sql AS
$$ SELECT ((extract(epoch FROM now()) + minutes * 60) * 1000)::bigint $$;

-- a round with one answer as the phone sends it
CREATE FUNCTION pg_temp.round(id text, started_min integer, task bigint, unit bigint) RETURNS jsonb LANGUAGE sql AS
$$ SELECT jsonb_build_object('attempt_id', id, 'attempt_type', 'UNIT_ROUND', 'learning_unit_id', unit, 'status', 'FINISHED',
        'started_at', pg_temp.ms(started_min), 'finished_at', pg_temp.ms(started_min + 2), 'earned_points', 1, 'max_points', 1,
        'answers', jsonb_build_array(jsonb_build_object('task_id', task, 'is_correct', true, 'earned_points', 1, 'max_points', 1,
            'answered_at', pg_temp.ms(started_min + 1), 'given_answer', '1=A'))) $$;


-- ---- 1. a first push: everything is stored, a round with an unknown task is rejected whole -------------------------
SELECT pg_temp.become('00000000-0000-0000-0000-0000000000e1');
DO $$
DECLARE
    res jsonb;
    t   t_ids;
    changes jsonb;
BEGIN
    SELECT * INTO t FROM t_ids;
    changes := jsonb_build_object(
        'profile', jsonb_build_object('first_name', 'Anna', 'last_name', 'Kovács', 'teaching_language', 'HU',
            'graduation_date', '2027-03-15', 'onboarding_completed_at', pg_temp.ms(-100), 'role_id', 1, 'updated_at', pg_temp.ms(-100)),
        'settings', jsonb_build_object('ui_language', 'HU', 'daily_goal_minutes', 12, 'reminder_enabled', false, 'updated_at', pg_temp.ms(-100)),
        'subjects', jsonb_build_array(jsonb_build_object('subject_id', t.subject_id, 'source', 'CHOSEN', 'added_at', pg_temp.ms(-100),
            'updated_at', pg_temp.ms(-100))),
        'attempts', jsonb_build_array(
            pg_temp.round('7f3c0c52-0000-4000-8000-000000000001', -50, t.task_id, t.unit_id),
            pg_temp.round('7f3c0c52-0000-4000-8000-000000000002', -40, 999999999, t.unit_id)),
        'unit_progress', jsonb_build_array(jsonb_build_object('learning_unit_id', t.unit_id, 'status', 'COMPLETED',
            'started_at', pg_temp.ms(-50), 'completed_at', pg_temp.ms(-48), 'updated_at', pg_temp.ms(-48))),
        'streak', jsonb_build_object('current_streak', 3, 'longest_streak', 5, 'last_activity_date', '2026-11-10', 'updated_at', pg_temp.ms(-48)),
        'activity', jsonb_build_array(jsonb_build_object('activity_date', '2026-11-10', 'device_id', 'dev1',
            'seconds_studied', 420, 'tasks_answered', 12, 'updated_at', pg_temp.ms(-48))));
    res := sync_user_data(NULL, 'dev1', false, changes);

    IF (SELECT count(*) FROM attempt) <> 1 OR (SELECT count(*) FROM attempt_answer) <> 1 THEN
        RAISE EXCEPTION 'FAIL: the good round was not stored alone (rounds %, answers %)',
            (SELECT count(*) FROM attempt), (SELECT count(*) FROM attempt_answer);
    END IF;
    IF res -> 'rejected' -> 0 ->> 'reason' IS DISTINCT FROM 'CONTENT_UNKNOWN' OR jsonb_array_length(res -> 'rejected') <> 1 THEN
        RAISE EXCEPTION 'FAIL: the round with an unknown task was not rejected: %', res -> 'rejected';
    END IF;
    IF (SELECT first_name FROM user_profile) <> 'Anna' OR (SELECT daily_goal_minutes FROM user_settings) <> 12
       OR (SELECT count(*) FROM user_subject) <> 1 OR (SELECT count(*) FROM user_unit_progress) <> 1
       OR (SELECT current_streak FROM user_streak) <> 3 OR (SELECT seconds_studied FROM activity_log) <> 420
       OR (SELECT role_id FROM user_role WHERE role_id IN (1, 2, 3)) <> 1 THEN
        RAISE EXCEPTION 'FAIL: the first push did not store everything';
    END IF;
    -- the phone's change time is kept (the sync flag), not the server's
    IF (SELECT updated_at FROM user_settings) > now() - interval '90 minutes' THEN
        RAISE EXCEPTION 'FAIL: the sync did not keep the phone''s updated_at (%)', (SELECT updated_at FROM user_settings);
    END IF;
    -- the response carries the account back: all rows on a null cursor, no time zone
    IF jsonb_array_length(res -> 'attempts') <> 1 OR res -> 'profile' ->> 'first_name' <> 'Anna'
       OR res -> 'settings' ? 'timezone' OR jsonb_array_length(res -> 'activity') <> 1 THEN
        RAISE EXCEPTION 'FAIL: the pull is wrong: %', res;
    END IF;
    PERFORM set_config('t.cursor', res ->> 'cursor', true);
END $$;

-- ---- 2. the same push again changes nothing ------------------------------------------------------------------------
DO $$
DECLARE
    t t_ids;
    res jsonb;
BEGIN
    SELECT * INTO t FROM t_ids;
    res := sync_user_data(NULL, 'dev1', false, jsonb_build_object(
        'attempts', jsonb_build_array(pg_temp.round('7f3c0c52-0000-4000-8000-000000000001', -50, t.task_id, t.unit_id)),
        'activity', jsonb_build_array(jsonb_build_object('activity_date', '2026-11-10', 'device_id', 'dev1',
            'seconds_studied', 420, 'tasks_answered', 12, 'updated_at', pg_temp.ms(-48)))));
    IF (SELECT count(*) FROM attempt) <> 1 OR (SELECT count(*) FROM attempt_answer) <> 1 OR (SELECT count(*) FROM activity_log) <> 1 THEN
        RAISE EXCEPTION 'FAIL: a repeated push duplicated rows';
    END IF;
END $$;

-- ---- 3. a second device pulls what the first pushed; the cursor then shows only news -------------------------------
DO $$
DECLARE
    res jsonb;
BEGIN
    res := sync_user_data(NULL, 'dev2', false, '{}'::jsonb);
    IF jsonb_array_length(res -> 'attempts') <> 1 OR jsonb_array_length(res -> 'subjects') <> 1 THEN
        RAISE EXCEPTION 'FAIL: the second device did not get the first one''s data';
    END IF;
    -- with the cursor of the full pull nothing has changed
    res := sync_user_data(res ->> 'cursor', 'dev2', false, '{}'::jsonb);
    IF jsonb_array_length(res -> 'attempts') <> 0 OR res -> 'profile' <> 'null'::jsonb OR jsonb_array_length(res -> 'subjects') <> 0 THEN
        RAISE EXCEPTION 'FAIL: a pull with the new cursor returned old rows: %', res;
    END IF;
END $$;

-- ---- 4. the other user sees and changes nothing --------------------------------------------------------------------
SELECT pg_temp.become('00000000-0000-0000-0000-0000000000e2');
DO $$
DECLARE
    res jsonb;
BEGIN
    res := sync_user_data(NULL, 'devB', false, '{}'::jsonb);
    IF jsonb_array_length(res -> 'attempts') <> 0 OR res -> 'profile' <> 'null'::jsonb OR jsonb_array_length(res -> 'activity') <> 0 THEN
        RAISE EXCEPTION 'FAIL: B got A''s data: %', res;
    END IF;
END $$;
SELECT pg_temp.become('00000000-0000-0000-0000-0000000000e1');

-- ---- 5. time in the future is cut; a row of another device is refused; an unknown key and role 5 are refused ----------
DO $$
DECLARE
    res jsonb;
BEGIN
    res := sync_user_data(NULL, 'dev1', false, jsonb_build_object(
        'settings', jsonb_build_object('ui_language', 'SK', 'daily_goal_minutes', 20, 'reminder_enabled', false,
            'updated_at', pg_temp.ms(60 * 24 * 10)),
        'activity', jsonb_build_array(jsonb_build_object('activity_date', '2026-11-10', 'device_id', 'other',
            'seconds_studied', 1, 'tasks_answered', 1, 'updated_at', pg_temp.ms(0))),
        'profile', jsonb_build_object('first_name', 'X', 'password', 'secret', 'updated_at', pg_temp.ms(0))));
    IF (SELECT updated_at FROM user_settings) > now() + interval '1 minute' THEN
        RAISE EXCEPTION 'FAIL: a future updated_at was stored';
    END IF;
    IF (SELECT daily_goal_minutes FROM user_settings) <> 20 THEN
        RAISE EXCEPTION 'FAIL: the newer settings did not win';
    END IF;
    IF NOT (res -> 'rejected') @> '[{"table": "activity_log", "reason": "NOT_YOUR_DEVICE"}]'::jsonb THEN
        RAISE EXCEPTION 'FAIL: another device''s row was not refused: %', res -> 'rejected';
    END IF;
    IF NOT (res -> 'rejected') @> '[{"table": "user_profile", "reason": "BAD_VALUE"}]'::jsonb
       OR (SELECT first_name FROM user_profile) <> 'Anna' THEN
        RAISE EXCEPTION 'FAIL: a profile with an unknown key was accepted: %', res -> 'rejected';
    END IF;
    res := sync_user_data(NULL, 'dev1', false, jsonb_build_object(
        'profile', jsonb_build_object('first_name', 'Anna', 'role_id', 5, 'updated_at', pg_temp.ms(0))));
    IF NOT (res -> 'rejected') @> '[{"table": "user_profile", "reason": "BAD_VALUE"}]'::jsonb
       OR EXISTS (SELECT 1 FROM user_role WHERE role_id = 5) THEN
        RAISE EXCEPTION 'FAIL: role 5 was accepted from a phone';
    END IF;
    -- an older settings push loses
    PERFORM sync_user_data(NULL, 'dev1', false, jsonb_build_object(
        'settings', jsonb_build_object('ui_language', 'HU', 'daily_goal_minutes', 5, 'reminder_enabled', false, 'updated_at', pg_temp.ms(-500))));
    IF (SELECT daily_goal_minutes FROM user_settings) <> 20 THEN
        RAISE EXCEPTION 'FAIL: an older settings push overwrote a newer one';
    END IF;
END $$;

-- ---- 6. streak and unit progress rules ----------------------------------------------------------------------------
DO $$
DECLARE
    t t_ids;
BEGIN
    SELECT * INTO t FROM t_ids;
    -- an older day with a shorter streak changes nothing
    PERFORM sync_user_data(NULL, 'dev1', false, jsonb_build_object(
        'streak', jsonb_build_object('current_streak', 1, 'longest_streak', 1, 'last_activity_date', '2026-11-09', 'updated_at', pg_temp.ms(0)),
        'unit_progress', jsonb_build_array(jsonb_build_object('learning_unit_id', t.unit_id, 'status', 'IN_PROGRESS',
            'started_at', pg_temp.ms(-60), 'updated_at', pg_temp.ms(0)))));
    IF (SELECT current_streak FROM user_streak) <> 3 OR (SELECT longest_streak FROM user_streak) <> 5 THEN
        RAISE EXCEPTION 'FAIL: an older streak overwrote the newer one';
    END IF;
    -- completed stays completed, the earlier start is kept
    IF (SELECT status FROM user_unit_progress) <> 'COMPLETED' OR (SELECT completed_at FROM user_unit_progress) IS NULL THEN
        RAISE EXCEPTION 'FAIL: a unit went back from completed';
    END IF;
    IF (SELECT started_at FROM user_unit_progress) > now() - interval '55 minutes' THEN
        RAISE EXCEPTION 'FAIL: the earlier start of a unit was not kept';
    END IF;
    -- a later day takes over the current streak, the longest is kept
    PERFORM sync_user_data(NULL, 'dev1', false, jsonb_build_object(
        'streak', jsonb_build_object('current_streak', 4, 'longest_streak', 4, 'last_activity_date', '2026-11-11', 'updated_at', pg_temp.ms(0))));
    IF (SELECT current_streak FROM user_streak) <> 4 OR (SELECT longest_streak FROM user_streak) <> 5
       OR (SELECT last_activity_date FROM user_streak) <> DATE '2026-11-11' THEN
        RAISE EXCEPTION 'FAIL: the streak of the later day was not taken';
    END IF;
    -- a streak that is not possible is refused
    IF NOT (sync_user_data(NULL, 'dev1', false, jsonb_build_object(
        'streak', jsonb_build_object('current_streak', 9, 'longest_streak', 2, 'last_activity_date', '2026-11-11', 'updated_at', pg_temp.ms(0)))) -> 'rejected')
        @> '[{"table": "user_streak"}]'::jsonb THEN
        RAISE EXCEPTION 'FAIL: an impossible streak was accepted';
    END IF;
END $$;

-- ---- 7. two devices of one day: the larger counter of each device, both rows travel ---------------------------------
DO $$
DECLARE
    res jsonb;
BEGIN
    PERFORM sync_user_data(NULL, 'dev1', false, jsonb_build_object('activity', jsonb_build_array(jsonb_build_object(
        'activity_date', '2026-11-10', 'device_id', 'dev1', 'seconds_studied', 300, 'tasks_answered', 5, 'updated_at', pg_temp.ms(0)))));
    IF (SELECT seconds_studied FROM activity_log WHERE device_id = 'dev1') <> 420 THEN
        RAISE EXCEPTION 'FAIL: a smaller counter overwrote the larger one';
    END IF;
    PERFORM sync_user_data(NULL, 'dev2', false, jsonb_build_object('activity', jsonb_build_array(jsonb_build_object(
        'activity_date', '2026-11-10', 'device_id', 'dev2', 'seconds_studied', 100, 'tasks_answered', 2, 'updated_at', pg_temp.ms(0)))));
    res := sync_user_data(NULL, 'dev1', false, '{}'::jsonb);
    IF jsonb_array_length(res -> 'activity') <> 2 THEN
        RAISE EXCEPTION 'FAIL: the rows of both devices do not come back';
    END IF;
END $$;

-- ---- 8. the pull is paged ----------------------------------------------------------------------------------------
DO $$
DECLARE
    t t_ids;
    res jsonb;
    seen int := 0;
    cur text;
BEGIN
    SELECT * INTO t FROM t_ids;
    PERFORM sync_user_data(NULL, 'dev1', false, jsonb_build_object('attempts', jsonb_build_array(
        pg_temp.round('7f3c0c52-0000-4000-8000-000000000011', -30, t.task_id, t.unit_id),
        pg_temp.round('7f3c0c52-0000-4000-8000-000000000012', -29, t.task_id, t.unit_id),
        pg_temp.round('7f3c0c52-0000-4000-8000-000000000013', -28, t.task_id, t.unit_id))));
    -- 4 rounds in all, pages of 3
    res := sync_user_data(NULL, 'dev2', false, '{}'::jsonb, 3);
    seen := jsonb_array_length(res -> 'attempts');
    IF NOT (res ->> 'more')::boolean OR seen <> 3 THEN
        RAISE EXCEPTION 'FAIL: the first page is wrong: more %, rows %', res ->> 'more', seen;
    END IF;
    cur := res ->> 'cursor';
    res := sync_user_data(cur, 'dev2', false, '{}'::jsonb, 3);
    seen := seen + jsonb_array_length(res -> 'attempts');
    IF (res ->> 'more')::boolean OR seen <> 4 THEN
        RAISE EXCEPTION 'FAIL: the second page is wrong: more %, total %', res ->> 'more', seen;
    END IF;
    IF (res ->> 'cursor')::timestamptz <= cur::timestamptz THEN
        RAISE EXCEPTION 'FAIL: the cursor did not move forward';
    END IF;
END $$;

-- ---- 9. the reset marker removes older rounds and ignores a later push of an older round --------------------------------
DO $$
DECLARE
    t t_ids;
    res jsonb;
BEGIN
    SELECT * INTO t FROM t_ids;
    res := sync_user_data(NULL, 'dev1', false, jsonb_build_object(
        'profile', jsonb_build_object('first_name', 'Anna', 'last_name', 'Kovács', 'teaching_language', 'HU',
            'progress_reset_at', pg_temp.ms(-10), 'updated_at', pg_temp.ms(-10))));
    IF (SELECT count(*) FROM attempt) <> 0 OR (SELECT count(*) FROM user_unit_progress) <> 0 THEN
        RAISE EXCEPTION 'FAIL: the reset did not remove the older rounds and units (% / %)',
            (SELECT count(*) FROM attempt), (SELECT count(*) FROM user_unit_progress);
    END IF;
    -- streak and minutes stay
    IF (SELECT count(*) FROM user_streak) <> 1 OR (SELECT count(*) FROM activity_log) <> 2 THEN
        RAISE EXCEPTION 'FAIL: the reset removed the streak or the minutes';
    END IF;
    -- a device that was offline pushes an old round: ignored. A round after the marker is kept.
    res := sync_user_data(NULL, 'dev2', false, jsonb_build_object('attempts', jsonb_build_array(
        pg_temp.round('7f3c0c52-0000-4000-8000-000000000021', -60, t.task_id, t.unit_id),
        pg_temp.round('7f3c0c52-0000-4000-8000-000000000022', -5, t.task_id, t.unit_id))));
    IF (res ->> 'ignored')::int <> 1 OR (SELECT count(*) FROM attempt) <> 1 THEN
        RAISE EXCEPTION 'FAIL: the old round was not ignored or the new one not kept (ignored %, rounds %)',
            res ->> 'ignored', (SELECT count(*) FROM attempt);
    END IF;
    -- the marker keeps the newest and the pull carries it
    IF (res -> 'profile' ->> 'progress_reset_at') IS NULL AND (SELECT progress_reset_at FROM user_profile) IS NULL THEN
        RAISE EXCEPTION 'FAIL: the reset marker is lost';
    END IF;
END $$;

-- ---- 10. a guest signing in to an account: the account's profile stays, the learning data is added -----------------------
DO $$
DECLARE
    t t_ids;
BEGIN
    SELECT * INTO t FROM t_ids;
    PERFORM sync_user_data(NULL, 'dev3', true, jsonb_build_object(
        'profile', jsonb_build_object('first_name', 'Guest', 'last_name', '', 'teaching_language', 'SK', 'role_id', 3,
            'updated_at', pg_temp.ms(100)),
        'settings', jsonb_build_object('ui_language', 'SK', 'daily_goal_minutes', 99, 'reminder_enabled', false, 'updated_at', pg_temp.ms(100)),
        'attempts', jsonb_build_array(pg_temp.round('7f3c0c52-0000-4000-8000-000000000031', -3, t.task_id, t.unit_id))));
    IF (SELECT first_name FROM user_profile) <> 'Anna' OR (SELECT teaching_language FROM user_profile) <> 'HU'
       OR (SELECT daily_goal_minutes FROM user_settings) <> 20 OR (SELECT count(*) FROM attempt) <> 2 THEN
        RAISE EXCEPTION 'FAIL: the guest merge changed the account''s profile or lost the guest''s round';
    END IF;
    -- the account has a role already: the guest's role is not added
    IF EXISTS (SELECT 1 FROM user_role WHERE role_id = 3) THEN
        RAISE EXCEPTION 'FAIL: the guest''s role replaced the account''s';
    END IF;
END $$;

-- ---- 12. the permission to download a pack ---------------------------------------------------------------------------------
RESET ROLE;
INSERT INTO content_release (subject_id, schema_version, content_version, object_path, size_bytes, sha256)
    SELECT subject_id, 7, 2026111201, 'packs/7/x/2026111201/content.db', 1000, repeat('a', 64) FROM t_ids;
SELECT pg_temp.become('00000000-0000-0000-0000-0000000000e1');
DO $$
DECLARE
    t t_ids;
    r record;
BEGIN
    SELECT * INTO t FROM t_ids;
    -- the subject was chosen in the first push of this test
    SELECT * INTO r FROM register_content_download(t.subject_id::smallint, 7::smallint);
    IF r.object_path <> 'packs/7/x/2026111201/content.db' OR r.content_version <> 2026111201 OR r.sha256 <> repeat('a', 64) THEN
        RAISE EXCEPTION 'FAIL: the release is wrong: %', r;
    END IF;
    PERFORM register_content_download(t.subject_id::smallint, 7::smallint);
    PERFORM register_content_download(t.subject_id::smallint, 7::smallint);
    BEGIN
        PERFORM register_content_download(t.subject_id::smallint, 7::smallint);
        RAISE EXCEPTION 'FAIL: the fourth download of the day was allowed';
    EXCEPTION WHEN SQLSTATE 'P0001' THEN NULL;
    END;
    -- no release of another schema version
    BEGIN
        PERFORM register_content_download(t.subject_id::smallint, 99::smallint);
        RAISE EXCEPTION 'FAIL: a release of an unknown schema version was given';
    EXCEPTION WHEN no_data_found THEN NULL;
    END;
END $$;

-- the other user did not choose the subject; nobody reads the log
SELECT pg_temp.become('00000000-0000-0000-0000-0000000000e2');
DO $$
DECLARE
    t t_ids;
BEGIN
    SELECT * INTO t FROM t_ids;
    BEGIN
        PERFORM register_content_download(t.subject_id::smallint, 7::smallint);
        RAISE EXCEPTION 'FAIL: a subject that is not chosen was given';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    BEGIN
        PERFORM count(*) FROM content_download;
        RAISE EXCEPTION 'FAIL: a user can read the download log';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
SELECT pg_temp.become('00000000-0000-0000-0000-0000000000e1');

-- ---- 13. a copy of one's own data -------------------------------------------------------------------------------------
DO $$
DECLARE
    copy jsonb;
BEGIN
    copy := export_my_data();
    IF copy -> 'account' ->> 'id' <> '00000000-0000-0000-0000-0000000000e1'
       OR jsonb_array_length(copy -> 'attempts') < 1
       OR jsonb_array_length(copy -> 'attempts' -> 0 -> 'answers') < 1
       OR jsonb_array_length(copy -> 'content_downloads') <> 3
       OR copy -> 'profile' ->> 'first_name' <> 'Anna'
       OR copy -> 'profile' ? 'server_updated_at' THEN
        RAISE EXCEPTION 'FAIL: the export is wrong: %', copy;
    END IF;
    BEGIN
        PERFORM clean_up_logs();
        RAISE EXCEPTION 'FAIL: a user ran the clean-up';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
SELECT pg_temp.become('00000000-0000-0000-0000-0000000000e2');
DO $$
DECLARE
    copy jsonb;
BEGIN
    copy := export_my_data();
    IF jsonb_array_length(copy -> 'attempts') <> 0 OR jsonb_array_length(copy -> 'content_downloads') <> 0 OR copy -> 'profile' <> 'null'::jsonb THEN
        RAISE EXCEPTION 'FAIL: B got A''s data in the export: %', copy;
    END IF;
END $$;

-- ---- 14. the clean-up removes only what is past its time; a deleted user's download log goes with the user ---------------
RESET ROLE;
INSERT INTO content_download (user_id, subject_id, content_version, requested_at)
    VALUES ('00000000-0000-0000-0000-0000000000e2', 1, 1, now() - interval '91 days');
SELECT clean_up_logs();
DO $$ BEGIN
    IF (SELECT count(*) FROM content_download WHERE user_id = '00000000-0000-0000-0000-0000000000e2') <> 0
       OR (SELECT count(*) FROM content_download WHERE user_id = '00000000-0000-0000-0000-0000000000e1') <> 3 THEN
        RAISE EXCEPTION 'FAIL: the clean-up of the download log is wrong';
    END IF;
END $$;
DELETE FROM auth.users WHERE id = '00000000-0000-0000-0000-0000000000e1';
DO $$ BEGIN
    IF (SELECT count(*) FROM content_download) <> 0 THEN
        RAISE EXCEPTION 'FAIL: the download log of a deleted user stayed';
    END IF;
END $$;

-- ---- 11. the function is for signed-in users only ----------------------------------------------------------------------
RESET ROLE;
SET LOCAL ROLE anon;
DO $$ BEGIN
    PERFORM sync_user_data(NULL, 'x', false, '{}'::jsonb);
    RAISE EXCEPTION 'FAIL: anon called sync_user_data';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;
DO $$ BEGIN
    PERFORM register_content_download(1::smallint, 7::smallint);
    RAISE EXCEPTION 'FAIL: anon called register_content_download';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;
DO $$ BEGIN
    PERFORM export_my_data();
    RAISE EXCEPTION 'FAIL: anon called export_my_data';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;

RESET ROLE;
SELECT 'sync tests OK' AS result;
ROLLBACK;
