-- A copy of a user's own data, and the clean-up of the logs (roadmap 12.6, plan llm/outputs/plan-12.md sections 8.2 and 8.3).
--
-- export_my_data gives a user a JSON copy of every row the server keeps about them (GDPR access and portability). It is
-- SECURITY DEFINER because the download log has no policy; every part is filtered by auth.uid(), so a user gets their own
-- rows and nothing else.
--
-- The clean-up jobs run on the server with pg_cron (enabled once in the dashboard: Database → Extensions). The local
-- Docker database and CI have no pg_cron: the schedule is skipped there, the clean-up function itself is tested.

CREATE FUNCTION public.export_my_data() RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    uid uuid := (SELECT auth.uid());
BEGIN
    IF uid IS NULL THEN
        RAISE EXCEPTION 'not signed in' USING ERRCODE = 'insufficient_privilege';
    END IF;

    RETURN jsonb_build_object(
        'exported_at', now(),
        'account', (SELECT jsonb_build_object('id', u.id, 'email', u.email, 'created_at', to_jsonb(u) -> 'created_at') FROM auth.users u WHERE u.id = uid),
        'profile', (SELECT to_jsonb(p) - 'server_updated_at' FROM public.user_profile p WHERE p.user_id = uid),
        'settings', (SELECT to_jsonb(s) - 'server_updated_at' FROM public.user_settings s WHERE s.user_id = uid),
        'roles', (SELECT coalesce(jsonb_agg(to_jsonb(r) - 'server_updated_at' ORDER BY r.role_id), '[]'::jsonb) FROM public.user_role r WHERE r.user_id = uid),
        'subjects', (SELECT coalesce(jsonb_agg(to_jsonb(s) - 'server_updated_at' ORDER BY s.subject_id), '[]'::jsonb) FROM public.user_subject s WHERE s.user_id = uid),
        'consents', (SELECT coalesce(jsonb_agg(to_jsonb(c) ORDER BY c.consent_id), '[]'::jsonb) FROM public.user_consent c WHERE c.user_id = uid),
        'attempts', (SELECT coalesce(jsonb_agg((to_jsonb(a) - 'server_updated_at') || jsonb_build_object('answers',
                        (SELECT coalesce(jsonb_agg(to_jsonb(x) - 'server_updated_at' ORDER BY x.answered_at), '[]'::jsonb)
                         FROM public.attempt_answer x WHERE x.attempt_id = a.attempt_id AND x.user_id = uid))
                     ORDER BY a.started_at), '[]'::jsonb) FROM public.attempt a WHERE a.user_id = uid),
        'unit_progress', (SELECT coalesce(jsonb_agg(to_jsonb(u) - 'server_updated_at' ORDER BY u.learning_unit_id), '[]'::jsonb) FROM public.user_unit_progress u WHERE u.user_id = uid),
        'streak', (SELECT to_jsonb(k) - 'server_updated_at' FROM public.user_streak k WHERE k.user_id = uid),
        'activity', (SELECT coalesce(jsonb_agg(to_jsonb(l) - 'server_updated_at' ORDER BY l.activity_date, l.device_id), '[]'::jsonb) FROM public.activity_log l WHERE l.user_id = uid),
        'content_downloads', (SELECT coalesce(jsonb_agg(to_jsonb(d) ORDER BY d.requested_at), '[]'::jsonb) FROM public.content_download d WHERE d.user_id = uid)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.export_my_data FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION public.export_my_data FROM anon;
    END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.export_my_data TO authenticated;

-- ---------------------------------------------------------------------
-- The clean-up: consent proofs past their date, and download log rows older than 90 days
-- ---------------------------------------------------------------------
CREATE FUNCTION public.clean_up_logs() RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = ''
AS $$
    DELETE FROM public.consent_archive WHERE delete_after < now();
    DELETE FROM public.content_download WHERE requested_at < now() - interval '90 days';
$$;

-- only the server runs it (the schedule below, as the owner)
REVOKE ALL ON FUNCTION public.clean_up_logs FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION public.clean_up_logs FROM anon, authenticated;
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        PERFORM cron.schedule('maturimate-clean-up-logs', '30 3 * * *', 'SELECT public.clean_up_logs()');
    END IF;
END;
$$;
