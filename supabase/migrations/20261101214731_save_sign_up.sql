-- The end of a sign-up in one transaction (code review 2026-11-01, 3.1 and 3.2).
--
-- Before this, the app wrote the profile, settings, role, subjects, consents and the onboarding
-- end with separate requests; a dropped connection could leave the account half written (for
-- example without subjects), and a retry stored the consents a second time.

-- ---------------------------------------------------------------------
-- An active consent is stored once
-- ---------------------------------------------------------------------

-- duplicates from earlier retries: the oldest row stays
DELETE FROM user_consent newer
    USING user_consent older
    WHERE newer.withdrawn_at IS NULL
      AND older.withdrawn_at IS NULL
      AND newer.user_id = older.user_id
      AND newer.consent_type = older.consent_type
      AND newer.document_version = older.document_version
      AND newer.consent_id > older.consent_id;

-- a withdrawn consent may be given again, so only the active ones are unique
CREATE UNIQUE INDEX uq_user_consent_active
    ON user_consent (user_id, consent_type, document_version)
    WHERE withdrawn_at IS NULL;

-- ---------------------------------------------------------------------
-- save_sign_up: everything a new account sends, at once
-- ---------------------------------------------------------------------

-- Runs with the caller's rights (SECURITY INVOKER), so the row level security policies apply to
-- every statement: the rows are always the signed-in user's own (auth.uid()), the role can only
-- be student, learner or teacher, and consents can only be added.
CREATE FUNCTION public.save_sign_up(
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
    -- [{"subject_id": 2, "source": "AUTO", "level": null}, ...]
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

    -- the onboarding roles are replaced; roles the server gives (curator, admin) stay
    DELETE FROM public.user_role WHERE user_id = uid AND role_id IN (1, 2, 3);
    IF p_role_id IS NOT NULL THEN
        INSERT INTO public.user_role (user_id, role_id) VALUES (uid, p_role_id);
    END IF;

    DELETE FROM public.user_subject WHERE user_id = uid;
    INSERT INTO public.user_subject (user_id, subject_id, source, level)
    SELECT uid, s.subject_id, s.source, s.level
    FROM jsonb_to_recordset(coalesce(p_subjects, '[]'::jsonb)) AS s(subject_id smallint, source varchar(10), level varchar(2));

    -- a consent that is already active is not stored again (uq_user_consent_active)
    INSERT INTO public.user_consent (user_id, consent_type, document_version)
    SELECT uid, c, p_document_version FROM unnest(coalesce(p_consents, '{}'::text[])) AS c
    ON CONFLICT DO NOTHING;
END;
$$;

REVOKE ALL ON FUNCTION public.save_sign_up FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION public.save_sign_up FROM anon;
    END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.save_sign_up TO authenticated;
