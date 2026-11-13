-- The sync of the user data (roadmap 12.3a, plan llm/outputs/plan-12.md sections 3 and 4).
--
-- sync_user_data is the one call of the app: it receives what changed on the phone, checks and merges it per table with
-- the rules of the plan, and returns what changed on the server since the phone's cursor. It runs with the rights of the
-- caller (SECURITY INVOKER), so every statement is under row level security and a row can only be the caller's. Two
-- devices of one account never merge at the same time (a per-user advisory lock held to the end of the transaction).
--
-- All times in the JSON are epoch milliseconds, dates are YYYY-MM-DD text.

-- ---------------------------------------------------------------------
-- A time from the phone: epoch milliseconds, cut to the server's now when it is more than 5 minutes ahead
-- ---------------------------------------------------------------------
CREATE FUNCTION public.fn_sync_ts(ms bigint, nowt timestamptz) RETURNS timestamptz
LANGUAGE sql IMMUTABLE
SET search_path = ''
AS $$
    SELECT CASE
        WHEN ms IS NULL THEN NULL
        WHEN to_timestamp(ms / 1000.0) > nowt + interval '5 minutes' THEN nowt
        ELSE to_timestamp(ms / 1000.0)
    END
$$;

CREATE FUNCTION public.fn_sync_ms(t timestamptz) RETURNS bigint
LANGUAGE sql IMMUTABLE
SET search_path = ''
AS $$ SELECT CASE WHEN t IS NULL THEN NULL ELSE (extract(epoch FROM t) * 1000)::bigint END $$;

-- ---------------------------------------------------------------------
-- The reset of the progress: rounds and unit progress started up to the marker go; streak and minutes stay
-- ---------------------------------------------------------------------
CREATE FUNCTION public.reset_progress(p_reset_at timestamptz) RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
    uid uuid := (SELECT auth.uid());
BEGIN
    IF uid IS NULL THEN
        RAISE EXCEPTION 'not signed in' USING ERRCODE = 'insufficient_privilege';
    END IF;
    -- the answers go with their rounds (ON DELETE CASCADE)
    DELETE FROM public.attempt WHERE user_id = uid AND started_at <= p_reset_at;
    DELETE FROM public.user_unit_progress WHERE user_id = uid AND started_at <= p_reset_at;
    UPDATE public.user_profile SET progress_reset_at = GREATEST(progress_reset_at, p_reset_at) WHERE user_id = uid;
END;
$$;

-- ---------------------------------------------------------------------
-- sync_user_data
-- ---------------------------------------------------------------------
CREATE FUNCTION public.sync_user_data(
    p_cursor       text,
    p_device_id    text,
    p_guest_merge  boolean,
    p_changes      jsonb,
    -- the page size of the pull; a parameter only so that the tests can use a small one
    p_page_rows    integer DEFAULT 5000
) RETURNS jsonb
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
    uid          uuid := (SELECT auth.uid());
    nowt         timestamptz := now();
    cursor_ts    timestamptz;
    rejected     jsonb := '[]'::jsonb;
    ignored      integer := 0;
    reset_at     timestamptz;
    old_reset    timestamptz;
    new_reset    timestamptz;
    changes      jsonb := coalesce(p_changes, '{}'::jsonb);
    prof         jsonb;
    sett         jsonb;
    strk         jsonb;
    r            jsonb;
    a            jsonb;
    started      timestamptz;
    finished     timestamptz;
    upd          timestamptz;
    cur_old      smallint;
    last_old     date;
    long_old     smallint;
    cur_new      smallint;
    last_new     date;
    long_new     smallint;
    row_count    integer;
    more         boolean := false;
    page_cursor  timestamptz;
    result       jsonb;
    attempts_out jsonb;
    bad_keys     text[];
BEGIN
    IF uid IS NULL THEN
        RAISE EXCEPTION 'not signed in' USING ERRCODE = 'insufficient_privilege';
    END IF;
    IF jsonb_typeof(changes) <> 'object' THEN
        RAISE EXCEPTION 'changes must be an object' USING ERRCODE = 'invalid_parameter_value';
    END IF;
    IF p_device_id IS NULL OR p_device_id = '' THEN
        RAISE EXCEPTION 'device id is required' USING ERRCODE = 'invalid_parameter_value';
    END IF;

    PERFORM pg_advisory_xact_lock(hashtext(uid::text));
    -- the trigger of the synced tables keeps the change time the phone sent
    PERFORM set_config('maturimate.sync', 'on', true);

    -- an empty text means no cursor (a client that cannot send null)
    IF p_cursor IS NOT NULL AND p_cursor <> '' THEN
        cursor_ts := p_cursor::timestamptz;
        IF cursor_ts < nowt - interval '365 days' THEN
            RAISE EXCEPTION 'cursor_too_old' USING ERRCODE = 'P0001';
        END IF;
    END IF;

    -- one call carries at most 300 rounds and 3000 rows in all
    IF jsonb_array_length(coalesce(changes -> 'attempts', '[]'::jsonb)) > 300
        OR (SELECT coalesce(sum(jsonb_array_length(value)), 0)
            FROM jsonb_each(changes) WHERE jsonb_typeof(value) = 'array' AND key <> 'attempts') > 3000 THEN
        RAISE EXCEPTION 'sync_too_large' USING ERRCODE = 'P0001';
    END IF;

    SELECT progress_reset_at INTO old_reset FROM public.user_profile WHERE user_id = uid;

    -- ---- 1. profile (and the role, and a newer reset marker) ----------------------------------------------------
    prof := changes -> 'profile';
    IF prof IS NOT NULL AND jsonb_typeof(prof) = 'object' THEN
        BEGIN
            SELECT array_agg(k) INTO bad_keys FROM jsonb_object_keys(prof) k
            WHERE k NOT IN ('first_name', 'last_name', 'teaching_language', 'graduation_date', 'onboarding_completed_at',
                            'progress_reset_at', 'role_id', 'updated_at');
            IF bad_keys IS NOT NULL THEN
                RAISE EXCEPTION 'unknown keys %', bad_keys USING ERRCODE = 'check_violation';
            END IF;
            IF (prof ->> 'role_id') IS NOT NULL AND (prof ->> 'role_id')::int NOT BETWEEN 1 AND 3 THEN
                RAISE EXCEPTION 'role not allowed' USING ERRCODE = 'check_violation';
            END IF;
            IF (prof ->> 'graduation_date') IS NOT NULL AND (prof ->> 'graduation_date')::date > current_date + interval '10 years' THEN
                RAISE EXCEPTION 'graduation date too far' USING ERRCODE = 'check_violation';
            END IF;
            upd := public.fn_sync_ts((prof ->> 'updated_at')::bigint, nowt);

            IF p_guest_merge THEN
                -- the account wins: a guest profile is taken only by an account that has none; the reset marker keeps the newest
                INSERT INTO public.user_profile AS p (user_id, first_name, last_name, teaching_language, graduation_date,
                                                      onboarding_completed_at, progress_reset_at, updated_at)
                VALUES (uid, coalesce(prof ->> 'first_name', ''), coalesce(prof ->> 'last_name', ''), prof ->> 'teaching_language',
                        (prof ->> 'graduation_date')::date, public.fn_sync_ts((prof ->> 'onboarding_completed_at')::bigint, nowt),
                        public.fn_sync_ts((prof ->> 'progress_reset_at')::bigint, nowt), coalesce(upd, nowt))
                ON CONFLICT (user_id) DO UPDATE SET
                    progress_reset_at = GREATEST(p.progress_reset_at, EXCLUDED.progress_reset_at)
                WHERE EXCLUDED.progress_reset_at > coalesce(p.progress_reset_at, '-infinity');
                -- a role only for an account that has none of the onboarding roles
                IF (prof ->> 'role_id') IS NOT NULL AND NOT EXISTS (
                    SELECT 1 FROM public.user_role WHERE user_id = uid AND role_id IN (1, 2, 3)) THEN
                    INSERT INTO public.user_role (user_id, role_id) VALUES (uid, (prof ->> 'role_id')::smallint);
                END IF;
            ELSE
                INSERT INTO public.user_profile AS p (user_id, first_name, last_name, teaching_language, graduation_date,
                                                      onboarding_completed_at, progress_reset_at, updated_at)
                VALUES (uid, coalesce(prof ->> 'first_name', ''), coalesce(prof ->> 'last_name', ''), prof ->> 'teaching_language',
                        (prof ->> 'graduation_date')::date, public.fn_sync_ts((prof ->> 'onboarding_completed_at')::bigint, nowt),
                        public.fn_sync_ts((prof ->> 'progress_reset_at')::bigint, nowt), coalesce(upd, nowt))
                ON CONFLICT (user_id) DO UPDATE SET
                    first_name = CASE WHEN EXCLUDED.updated_at > p.updated_at
                                      THEN coalesce(nullif(EXCLUDED.first_name, ''), p.first_name) ELSE p.first_name END,
                    last_name = CASE WHEN EXCLUDED.updated_at > p.updated_at
                                     THEN coalesce(nullif(EXCLUDED.last_name, ''), p.last_name) ELSE p.last_name END,
                    teaching_language = CASE WHEN EXCLUDED.updated_at > p.updated_at THEN EXCLUDED.teaching_language ELSE p.teaching_language END,
                    graduation_date = CASE WHEN EXCLUDED.updated_at > p.updated_at THEN EXCLUDED.graduation_date ELSE p.graduation_date END,
                    onboarding_completed_at = CASE WHEN EXCLUDED.updated_at > p.updated_at THEN EXCLUDED.onboarding_completed_at ELSE p.onboarding_completed_at END,
                    progress_reset_at = GREATEST(p.progress_reset_at, EXCLUDED.progress_reset_at),
                    updated_at = GREATEST(p.updated_at, EXCLUDED.updated_at)
                WHERE EXCLUDED.updated_at > p.updated_at
                   OR EXCLUDED.progress_reset_at > coalesce(p.progress_reset_at, '-infinity');
                -- the onboarding roles follow a newer profile
                IF FOUND AND (prof ->> 'role_id') IS NOT NULL
                   AND coalesce(upd, nowt) >= (SELECT updated_at FROM public.user_profile WHERE user_id = uid) THEN
                    DELETE FROM public.user_role WHERE user_id = uid AND role_id IN (1, 2, 3);
                    INSERT INTO public.user_role (user_id, role_id) VALUES (uid, (prof ->> 'role_id')::smallint);
                END IF;
            END IF;
        EXCEPTION WHEN check_violation OR invalid_text_representation OR foreign_key_violation
                  OR numeric_value_out_of_range OR datetime_field_overflow OR invalid_datetime_format
                  OR string_data_right_truncation OR not_null_violation THEN
            rejected := rejected || jsonb_build_object('table', 'user_profile', 'key', uid, 'reason', 'BAD_VALUE');
        END;

        -- a newer reset marker deletes the rounds and units before it, so the rounds that follow are checked against it
        SELECT progress_reset_at INTO new_reset FROM public.user_profile WHERE user_id = uid;
        IF new_reset IS NOT NULL AND (old_reset IS NULL OR new_reset > old_reset) THEN
            PERFORM public.reset_progress(new_reset);
        END IF;
    END IF;
    SELECT progress_reset_at INTO reset_at FROM public.user_profile WHERE user_id = uid;

    -- ---- 2. settings (the time zone is not synced) --------------------------------------------------------------
    sett := changes -> 'settings';
    IF sett IS NOT NULL AND jsonb_typeof(sett) = 'object' THEN
        BEGIN
            upd := public.fn_sync_ts((sett ->> 'updated_at')::bigint, nowt);
            INSERT INTO public.user_settings AS s (user_id, ui_language, daily_goal_minutes, reminder_enabled, reminder_time, updated_at)
            VALUES (uid, coalesce(sett ->> 'ui_language', 'SK'), (sett ->> 'daily_goal_minutes')::smallint,
                    coalesce((sett ->> 'reminder_enabled')::boolean, false), (sett ->> 'reminder_time')::time, coalesce(upd, nowt))
            ON CONFLICT (user_id) DO UPDATE SET
                ui_language = EXCLUDED.ui_language, daily_goal_minutes = EXCLUDED.daily_goal_minutes,
                reminder_enabled = EXCLUDED.reminder_enabled, reminder_time = EXCLUDED.reminder_time,
                updated_at = EXCLUDED.updated_at
            WHERE NOT p_guest_merge AND EXCLUDED.updated_at > s.updated_at;
        EXCEPTION WHEN check_violation OR invalid_text_representation OR numeric_value_out_of_range
                  OR datetime_field_overflow OR invalid_datetime_format OR not_null_violation THEN
            rejected := rejected || jsonb_build_object('table', 'user_settings', 'key', uid, 'reason', 'BAD_VALUE');
        END;
    END IF;

    -- ---- 3. subjects ---------------------------------------------------------------------------------------------
    FOR r IN SELECT value FROM jsonb_array_elements(coalesce(changes -> 'subjects', '[]'::jsonb)) LOOP
        BEGIN
            upd := public.fn_sync_ts((r ->> 'updated_at')::bigint, nowt);
            IF p_guest_merge THEN
                -- the union: a subject the account does not have is added, an account row is never changed
                INSERT INTO public.user_subject (user_id, subject_id, source, level, added_at, updated_at)
                VALUES (uid, (r ->> 'subject_id')::smallint, coalesce(r ->> 'source', 'CHOSEN'), r ->> 'level',
                        coalesce(public.fn_sync_ts((r ->> 'added_at')::bigint, nowt), nowt), coalesce(upd, nowt))
                ON CONFLICT (user_id, subject_id) DO NOTHING;
            ELSE
                INSERT INTO public.user_subject AS s (user_id, subject_id, source, level, added_at, removed_at, updated_at)
                VALUES (uid, (r ->> 'subject_id')::smallint, coalesce(r ->> 'source', 'CHOSEN'), r ->> 'level',
                        coalesce(public.fn_sync_ts((r ->> 'added_at')::bigint, nowt), nowt),
                        public.fn_sync_ts((r ->> 'removed_at')::bigint, nowt), coalesce(upd, nowt))
                ON CONFLICT (user_id, subject_id) DO UPDATE SET
                    source = EXCLUDED.source, level = EXCLUDED.level, removed_at = EXCLUDED.removed_at,
                    added_at = LEAST(s.added_at, EXCLUDED.added_at), updated_at = EXCLUDED.updated_at
                WHERE EXCLUDED.updated_at > s.updated_at;
            END IF;
        EXCEPTION WHEN check_violation OR invalid_text_representation OR foreign_key_violation
                  OR numeric_value_out_of_range OR datetime_field_overflow OR not_null_violation THEN
            rejected := rejected || jsonb_build_object('table', 'user_subject', 'key', r -> 'subject_id', 'reason', 'BAD_VALUE');
        END;
    END LOOP;

    -- ---- 4. rounds with their answers: one unit, all or nothing -------------------------------------------------
    FOR r IN SELECT value FROM jsonb_array_elements(coalesce(changes -> 'attempts', '[]'::jsonb)) LOOP
        BEGIN
            started := public.fn_sync_ts((r ->> 'started_at')::bigint, nowt);
            finished := public.fn_sync_ts((r ->> 'finished_at')::bigint, nowt);
            IF (r ->> 'status') NOT IN ('FINISHED', 'ABANDONED') THEN
                RAISE EXCEPTION 'only finished or abandoned rounds travel' USING ERRCODE = 'check_violation';
            END IF;
            IF coalesce((r ->> 'max_points')::int, 0) > 1000
                OR jsonb_array_length(coalesce(r -> 'answers', '[]'::jsonb)) > 200 THEN
                RAISE EXCEPTION 'round too large' USING ERRCODE = 'check_violation';
            END IF;
            -- a round started before the reset marker belongs to the old progress
            IF reset_at IS NOT NULL AND started <= reset_at THEN
                ignored := ignored + 1;
                CONTINUE;
            END IF;
            IF finished IS NOT NULL AND finished < started THEN
                finished := started;
            END IF;

            INSERT INTO public.attempt (attempt_id, user_id, attempt_type, topic_id, learning_unit_id, status, started_at,
                                        finished_at, earned_points, max_points)
            VALUES ((r ->> 'attempt_id')::uuid, uid, r ->> 'attempt_type', (r ->> 'topic_id')::bigint,
                    (r ->> 'learning_unit_id')::bigint, r ->> 'status', started, finished,
                    (r ->> 'earned_points')::smallint, (r ->> 'max_points')::smallint)
            ON CONFLICT (attempt_id) DO NOTHING;

            FOR a IN SELECT value FROM jsonb_array_elements(coalesce(r -> 'answers', '[]'::jsonb)) LOOP
                INSERT INTO public.attempt_answer (attempt_id, task_id, user_id, is_correct, earned_points, max_points,
                                                   answered_at, given_answer)
                VALUES ((r ->> 'attempt_id')::uuid, (a ->> 'task_id')::bigint, uid, (a ->> 'is_correct')::boolean,
                        (a ->> 'earned_points')::smallint, (a ->> 'max_points')::smallint,
                        coalesce(public.fn_sync_ts((a ->> 'answered_at')::bigint, nowt), nowt), a ->> 'given_answer')
                ON CONFLICT (attempt_id, task_id) DO NOTHING;
            END LOOP;
        EXCEPTION
            WHEN foreign_key_violation THEN
                rejected := rejected || jsonb_build_object('table', 'attempt', 'key', r -> 'attempt_id', 'reason', 'CONTENT_UNKNOWN');
            WHEN check_violation OR invalid_text_representation OR numeric_value_out_of_range
                 OR datetime_field_overflow OR not_null_violation OR string_data_right_truncation THEN
                rejected := rejected || jsonb_build_object('table', 'attempt', 'key', r -> 'attempt_id', 'reason', 'BAD_VALUE');
        END;
    END LOOP;

    -- ---- 5. unit progress: the furthest wins --------------------------------------------------------------------
    FOR r IN SELECT value FROM jsonb_array_elements(coalesce(changes -> 'unit_progress', '[]'::jsonb)) LOOP
        BEGIN
            started := public.fn_sync_ts((r ->> 'started_at')::bigint, nowt);
            IF reset_at IS NOT NULL AND started <= reset_at THEN
                ignored := ignored + 1;
                CONTINUE;
            END IF;
            INSERT INTO public.user_unit_progress AS u (user_id, learning_unit_id, status, started_at, completed_at, updated_at)
            VALUES (uid, (r ->> 'learning_unit_id')::bigint, r ->> 'status', started,
                    public.fn_sync_ts((r ->> 'completed_at')::bigint, nowt),
                    coalesce(public.fn_sync_ts((r ->> 'updated_at')::bigint, nowt), nowt))
            ON CONFLICT (user_id, learning_unit_id) DO UPDATE SET
                status = CASE WHEN 'COMPLETED' IN (u.status, EXCLUDED.status) THEN 'COMPLETED' ELSE 'IN_PROGRESS' END,
                started_at = LEAST(u.started_at, EXCLUDED.started_at),
                completed_at = LEAST(u.completed_at, EXCLUDED.completed_at),
                updated_at = GREATEST(u.updated_at, EXCLUDED.updated_at);
        EXCEPTION
            WHEN foreign_key_violation THEN
                rejected := rejected || jsonb_build_object('table', 'user_unit_progress', 'key', r -> 'learning_unit_id', 'reason', 'CONTENT_UNKNOWN');
            WHEN check_violation OR invalid_text_representation OR numeric_value_out_of_range
                 OR datetime_field_overflow OR not_null_violation THEN
                rejected := rejected || jsonb_build_object('table', 'user_unit_progress', 'key', r -> 'learning_unit_id', 'reason', 'BAD_VALUE');
        END;
    END LOOP;

    -- ---- 6. streak: the longest is kept, the current one comes from the later day ----------------------------------
    strk := changes -> 'streak';
    IF strk IS NOT NULL AND jsonb_typeof(strk) = 'object' THEN
        BEGIN
            cur_new := (strk ->> 'current_streak')::smallint;
            long_new := (strk ->> 'longest_streak')::smallint;
            last_new := (strk ->> 'last_activity_date')::date;
            IF cur_new < 0 OR long_new < cur_new OR long_new > 10000
                OR last_new > ((nowt + interval '14 hours')::date) THEN
                RAISE EXCEPTION 'bad streak' USING ERRCODE = 'check_violation';
            END IF;
            upd := coalesce(public.fn_sync_ts((strk ->> 'updated_at')::bigint, nowt), nowt);

            SELECT current_streak, longest_streak, last_activity_date INTO cur_old, long_old, last_old
            FROM public.user_streak WHERE user_id = uid;
            IF FOUND THEN
                cur_new := CASE
                    WHEN last_old IS NULL OR last_new > last_old THEN cur_new
                    WHEN last_new = last_old THEN GREATEST(cur_old, cur_new)
                    ELSE cur_old END;
                last_new := GREATEST(last_old, last_new);
                long_new := GREATEST(long_old, long_new, cur_new);
                UPDATE public.user_streak SET current_streak = cur_new, longest_streak = long_new,
                       last_activity_date = last_new, updated_at = GREATEST(updated_at, upd)
                WHERE user_id = uid
                  AND (current_streak, longest_streak, last_activity_date) IS DISTINCT FROM (cur_new, long_new, last_new);
            ELSE
                INSERT INTO public.user_streak (user_id, current_streak, longest_streak, last_activity_date, updated_at)
                VALUES (uid, cur_new, long_new, last_new, upd);
            END IF;
        EXCEPTION WHEN check_violation OR invalid_text_representation OR numeric_value_out_of_range
                  OR datetime_field_overflow OR invalid_datetime_format OR not_null_violation THEN
            rejected := rejected || jsonb_build_object('table', 'user_streak', 'key', uid, 'reason', 'BAD_VALUE');
        END;
    END IF;

    -- ---- 7. minutes of the days: each device writes its own rows, the larger counter wins --------------------------
    FOR r IN SELECT value FROM jsonb_array_elements(coalesce(changes -> 'activity', '[]'::jsonb)) LOOP
        BEGIN
            IF (r ->> 'device_id') IS DISTINCT FROM p_device_id THEN
                rejected := rejected || jsonb_build_object('table', 'activity_log', 'key', r -> 'activity_date', 'reason', 'NOT_YOUR_DEVICE');
                CONTINUE;
            END IF;
            IF (r ->> 'seconds_studied')::int > 86400 OR (r ->> 'tasks_answered')::int > 5000
                OR (r ->> 'activity_date')::date > ((nowt + interval '14 hours')::date) THEN
                RAISE EXCEPTION 'bad activity' USING ERRCODE = 'check_violation';
            END IF;
            INSERT INTO public.activity_log AS l (user_id, activity_date, device_id, seconds_studied, tasks_answered, updated_at)
            VALUES (uid, (r ->> 'activity_date')::date, p_device_id, (r ->> 'seconds_studied')::int,
                    (r ->> 'tasks_answered')::smallint, coalesce(public.fn_sync_ts((r ->> 'updated_at')::bigint, nowt), nowt))
            ON CONFLICT (user_id, activity_date, device_id) DO UPDATE SET
                seconds_studied = GREATEST(l.seconds_studied, EXCLUDED.seconds_studied),
                tasks_answered = GREATEST(l.tasks_answered, EXCLUDED.tasks_answered),
                updated_at = GREATEST(l.updated_at, EXCLUDED.updated_at)
            WHERE EXCLUDED.seconds_studied > l.seconds_studied OR EXCLUDED.tasks_answered > l.tasks_answered;
        EXCEPTION WHEN check_violation OR invalid_text_representation OR numeric_value_out_of_range
                  OR datetime_field_overflow OR invalid_datetime_format OR not_null_violation THEN
            rejected := rejected || jsonb_build_object('table', 'activity_log', 'key', r -> 'activity_date', 'reason', 'BAD_VALUE');
        END;
    END LOOP;

    -- ---- the pull --------------------------------------------------------------------------------------------------
    -- the rounds, oldest change first, at most one page; their answers come with them
    WITH page AS (
        SELECT * FROM public.attempt
        WHERE user_id = uid AND (cursor_ts IS NULL OR server_updated_at > cursor_ts)
        ORDER BY server_updated_at, attempt_id
        LIMIT p_page_rows + 1
    ), cut AS (
        SELECT * FROM page ORDER BY server_updated_at, attempt_id LIMIT p_page_rows
    )
    SELECT
        coalesce(jsonb_agg(jsonb_build_object(
            'attempt_id', c.attempt_id, 'attempt_type', c.attempt_type, 'topic_id', c.topic_id,
            'learning_unit_id', c.learning_unit_id, 'status', c.status, 'started_at', public.fn_sync_ms(c.started_at),
            'finished_at', public.fn_sync_ms(c.finished_at), 'earned_points', c.earned_points, 'max_points', c.max_points,
            'answers', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                    'task_id', aa.task_id, 'is_correct', aa.is_correct, 'earned_points', aa.earned_points,
                    'max_points', aa.max_points, 'answered_at', public.fn_sync_ms(aa.answered_at),
                    'given_answer', aa.given_answer) ORDER BY aa.task_id), '[]'::jsonb)
                FROM public.attempt_answer aa WHERE aa.attempt_id = c.attempt_id AND aa.user_id = uid)
        ) ORDER BY c.server_updated_at, c.attempt_id), '[]'::jsonb),
        (SELECT count(*) FROM page) > p_page_rows,
        max(c.server_updated_at)
    INTO attempts_out, more, page_cursor
    FROM cut c;

    IF NOT more THEN
        page_cursor := clock_timestamp();
    END IF;

    result := jsonb_build_object(
        'server_time', public.fn_sync_ms(nowt),
        'cursor', page_cursor::text,
        'more', more,
        'rejected', rejected,
        'ignored', ignored,
        'attempts', attempts_out,
        'roles', (SELECT coalesce(jsonb_agg(role_id ORDER BY role_id), '[]'::jsonb) FROM public.user_role WHERE user_id = uid),
        'profile', (SELECT jsonb_build_object(
                'first_name', p.first_name, 'last_name', p.last_name, 'teaching_language', p.teaching_language,
                'graduation_date', p.graduation_date::text, 'onboarding_completed_at', public.fn_sync_ms(p.onboarding_completed_at),
                'progress_reset_at', public.fn_sync_ms(p.progress_reset_at), 'updated_at', public.fn_sync_ms(p.updated_at),
                'role_id', (SELECT min(role_id) FROM public.user_role WHERE user_id = uid AND role_id IN (1, 2, 3)))
            FROM public.user_profile p WHERE p.user_id = uid AND (cursor_ts IS NULL OR p.server_updated_at > cursor_ts)),
        'settings', (SELECT jsonb_build_object(
                'ui_language', s.ui_language, 'daily_goal_minutes', s.daily_goal_minutes, 'reminder_enabled', s.reminder_enabled,
                'reminder_time', to_char(s.reminder_time, 'HH24:MI'), 'updated_at', public.fn_sync_ms(s.updated_at))
            FROM public.user_settings s WHERE s.user_id = uid AND (cursor_ts IS NULL OR s.server_updated_at > cursor_ts)),
        'subjects', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                'subject_id', s.subject_id, 'source', s.source, 'level', s.level, 'added_at', public.fn_sync_ms(s.added_at),
                'removed_at', public.fn_sync_ms(s.removed_at), 'updated_at', public.fn_sync_ms(s.updated_at)) ORDER BY s.subject_id), '[]'::jsonb)
            FROM public.user_subject s WHERE s.user_id = uid AND (cursor_ts IS NULL OR s.server_updated_at > cursor_ts)),
        'unit_progress', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                'learning_unit_id', u.learning_unit_id, 'status', u.status, 'started_at', public.fn_sync_ms(u.started_at),
                'completed_at', public.fn_sync_ms(u.completed_at), 'updated_at', public.fn_sync_ms(u.updated_at)) ORDER BY u.learning_unit_id), '[]'::jsonb)
            FROM public.user_unit_progress u WHERE u.user_id = uid AND (cursor_ts IS NULL OR u.server_updated_at > cursor_ts)),
        'streak', (SELECT jsonb_build_object(
                'current_streak', k.current_streak, 'longest_streak', k.longest_streak,
                'last_activity_date', k.last_activity_date::text, 'updated_at', public.fn_sync_ms(k.updated_at))
            FROM public.user_streak k WHERE k.user_id = uid AND (cursor_ts IS NULL OR k.server_updated_at > cursor_ts)),
        'activity', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                'activity_date', l.activity_date::text, 'device_id', l.device_id, 'seconds_studied', l.seconds_studied,
                'tasks_answered', l.tasks_answered, 'updated_at', public.fn_sync_ms(l.updated_at)) ORDER BY l.activity_date, l.device_id), '[]'::jsonb)
            FROM public.activity_log l WHERE l.user_id = uid AND (cursor_ts IS NULL OR l.server_updated_at > cursor_ts)),
        'exam_dates', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                'exam_date_id', e.exam_date_id, 'exam_year', e.exam_year, 'subject_id', e.subject_id, 'exam_on', e.exam_on::text,
                'published_at', public.fn_sync_ms(e.published_at), 'note', e.note) ORDER BY e.exam_date_id), '[]'::jsonb)
            FROM public.exam_date e),
        'content_releases', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                'subject_id', c.subject_id, 'schema_version', c.schema_version, 'content_version', c.content_version,
                'size_bytes', c.size_bytes, 'sha256', c.sha256) ORDER BY c.subject_id, c.schema_version), '[]'::jsonb)
            FROM public.content_release c WHERE c.is_current)
    );

    RETURN result;
END;
$$;

REVOKE ALL ON FUNCTION public.sync_user_data FROM PUBLIC;
REVOKE ALL ON FUNCTION public.reset_progress FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION public.sync_user_data FROM anon;
        REVOKE ALL ON FUNCTION public.reset_progress FROM anon;
    END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_user_data TO authenticated;
GRANT EXECUTE ON FUNCTION public.reset_progress TO authenticated;
