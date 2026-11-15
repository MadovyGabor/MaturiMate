-- save_sign_up never overwrites an account that is set up (review of S1 to S5, N2).
--
-- A session restored on a phone (or a Google account used before) could reach the sign-up form, and save_sign_up then
-- replaced the account's names, profile, settings, onboarding roles and subjects with the form's.
--
-- An account is set up when its profile has an onboarding end AND it has an active (not withdrawn) TERMS consent. Only
-- this function stores that consent, so the pair marks a sign-up that went through; a finished profile alone may be a
-- guest's, pushed by the sync before the sign-up was saved, and that account still needs this function and its consents.
-- A set-up account is refused with SQLSTATE PT409 (PostgREST answers 409 Conflict), and nothing is written. The app
-- applies the same rule before it calls the function (RemoteProfile.isSetUp, AccountSetupImpl) and turns such a sign-up
-- into a sign-in; this is the server's own guard for older app versions and for anything that goes around the app.
--
-- Everything else is the definition of 20261112120000_sync_groundwork.sql unchanged: SECURITY INVOKER (the row level
-- security policies apply to every statement), the empty search_path, the same grants.

CREATE OR REPLACE FUNCTION public.save_sign_up(
    p_first_name              text,
    p_last_name               text,
    p_teaching_language       text,
    p_graduation_date         date,
    p_ui_language             text,
    p_timezone                text,
    p_daily_goal_minutes      smallint,
    p_reminder_enabled        boolean,
    p_reminder_time           time,
    p_role_id                 smallint,
    p_subjects                jsonb,
    p_consents                text[],
    p_document_version        text,
    p_onboarding_completed_at timestamptz
) RETURNS void
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

    -- an account that finished its sign-up keeps what it has
    IF EXISTS (SELECT 1 FROM public.user_profile WHERE user_id = uid AND onboarding_completed_at IS NOT NULL)
       AND EXISTS (SELECT 1 FROM public.user_consent
                   WHERE user_id = uid AND consent_type = 'TERMS' AND withdrawn_at IS NULL) THEN
        RAISE EXCEPTION 'account_already_set_up' USING ERRCODE = 'PT409';
    END IF;

    INSERT INTO public.user_profile (user_id, first_name, last_name, teaching_language, graduation_date, onboarding_completed_at)
    VALUES (uid, p_first_name, p_last_name, p_teaching_language, p_graduation_date, p_onboarding_completed_at)
    ON CONFLICT (user_id) DO UPDATE SET
        first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        teaching_language = EXCLUDED.teaching_language,
        graduation_date = EXCLUDED.graduation_date,
        onboarding_completed_at = EXCLUDED.onboarding_completed_at;

    INSERT INTO public.user_settings (user_id, ui_language, timezone, daily_goal_minutes, reminder_enabled, reminder_time)
    VALUES (uid, p_ui_language, p_timezone, p_daily_goal_minutes, p_reminder_enabled, p_reminder_time)
    ON CONFLICT (user_id) DO UPDATE SET
        ui_language = EXCLUDED.ui_language,
        timezone = EXCLUDED.timezone,
        daily_goal_minutes = EXCLUDED.daily_goal_minutes,
        reminder_enabled = EXCLUDED.reminder_enabled,
        reminder_time = EXCLUDED.reminder_time;

    DELETE FROM public.user_role WHERE user_id = uid AND role_id IN (1, 2, 3);
    IF p_role_id IS NOT NULL THEN
        INSERT INTO public.user_role (user_id, role_id) VALUES (uid, p_role_id);
    END IF;

    -- the subjects not in the list are marked removed, the sent ones are inserted or brought back
    UPDATE public.user_subject SET removed_at = now()
    WHERE user_id = uid AND removed_at IS NULL
      AND subject_id NOT IN (SELECT s.subject_id
                             FROM jsonb_to_recordset(coalesce(p_subjects, '[]'::jsonb)) AS s(subject_id smallint));

    INSERT INTO public.user_subject (user_id, subject_id, source, level)
    SELECT uid, s.subject_id, s.source, s.level
    FROM jsonb_to_recordset(coalesce(p_subjects, '[]'::jsonb)) AS s(subject_id smallint, source varchar(10), level varchar(2))
    ON CONFLICT (user_id, subject_id) DO UPDATE SET
        removed_at = NULL, source = EXCLUDED.source, level = EXCLUDED.level;

    INSERT INTO public.user_consent (user_id, consent_type, document_version)
    SELECT uid, c, p_document_version FROM unnest(coalesce(p_consents, '{}'::text[])) AS c
    ON CONFLICT DO NOTHING;
END;
$$;

-- CREATE OR REPLACE keeps the privileges; they are stated again so this file alone says who may call it
REVOKE ALL ON FUNCTION public.save_sign_up FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION public.save_sign_up FROM anon;
    END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.save_sign_up TO authenticated;
