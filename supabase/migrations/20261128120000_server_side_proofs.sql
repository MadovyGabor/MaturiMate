-- The server checks for itself what the app checked alone until now (review of S1 to S5, card 12; accepted risks of
-- card 11: S1-1 server part, N-8, N-4 server part).
--
-- 1. sync_user_data bound to the account the phone means. The app checks before and after every call that the session is
--    the phone's own account; a session that changes between that check and the call (a Google sign-in of another
--    account during the deletion proof) would still send this phone's rows to the other account. The new overload takes
--    p_expected_user, the phone's account id, and refuses the call with SQLSTATE PT409 (PostgREST answers 409) and the
--    message `user_mismatch` when it is not the caller (auth.uid()). Nothing is read or written then.
--    The old signature stays, unchanged, for the app versions already installed: dropping it would make every sync of
--    such a version fail until it is updated. It is no hole: the binding protects an honest app against its own session
--    changing under it; a client that wants to skip it can always leave the parameter out (or send null), and row level
--    security keeps every caller to their own rows in both. A later migration can drop the old one.
--
-- 2. delete_my_account needs a recent sign-in IN THE TOKEN. The app asks for the password or a fresh Google sign-in before
--    the deletion, but the server deleted for any valid token, so an old session (a phone left open, a copied token) was
--    enough. Now the token's `amr` (GoTrue: the ways the session was signed in, each {method, timestamp in seconds}) must
--    hold a password, oauth or otp entry from the last 5 minutes; else `recent_login_required` (insufficient_privilege,
--    PostgREST answers 403). The app sends the proof's own session: the password check's (amr: password) or the fresh
--    Google sign-in's (amr: oauth, from the ID-token grant on Android and the PKCE code exchange on desktop). A token
--    refresh keeps the session's amr entries and their times, it adds no new sign-in.
--
-- 3. clean_up_empty_users removes Auth users that never became an account of the app: older than 7 days, no sign-in in
--    the last 7 days, and no row at all in any app table (no profile, no consent of any kind, no settings, role, subject,
--    progress, report or download). They come from sign-ups abandoned before their end (a confirmed e-mail or a Google
--    sign-in without the onboarding's save) and from deletion proofs made with another Google account that had no
--    account here (Google sign-in creates the user). They hold only an e-mail address, which is not kept without purpose
--    (GDPR art. 5(1)(c) and (e)). Users with a session used in the last 7 days are kept too. Run daily by pg_cron, like
--    clean_up_logs; p_dry_run only counts (and returns the same number the run would delete).

-- ---------------------------------------------------------------------
-- 1. The sync bound to the account the phone means
-- ---------------------------------------------------------------------
-- p_expected_user has no default: with one, a call without it would match both functions (ambiguous). Named arguments
-- choose: PostgREST calls this one when the body has p_expected_user, the old one when it does not.
-- From here on the name has two functions: every later GRANT, REVOKE, ALTER, COMMENT or DROP must name the full
-- signature, e.g. public.sync_user_data(text, text, boolean, jsonb, uuid, integer); the bare name is an error.
CREATE FUNCTION public.sync_user_data(
    p_cursor        text,
    p_device_id     text,
    p_guest_merge   boolean,
    p_changes       jsonb,
    p_expected_user uuid,
    p_page_rows     integer DEFAULT 5000
) RETURNS jsonb
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
    uid uuid := (SELECT auth.uid());
BEGIN
    -- without a session the old function says "not signed in" (insufficient_privilege), as before
    IF uid IS NOT NULL AND p_expected_user IS NOT NULL AND p_expected_user <> uid THEN
        RAISE EXCEPTION 'user_mismatch' USING ERRCODE = 'PT409';
    END IF;
    RETURN public.sync_user_data(p_cursor, p_device_id, p_guest_merge, p_changes, p_page_rows);
END;
$$;

REVOKE ALL ON FUNCTION public.sync_user_data(text, text, boolean, jsonb, uuid, integer) FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION public.sync_user_data(text, text, boolean, jsonb, uuid, integer) FROM anon;
    END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_user_data(text, text, boolean, jsonb, uuid, integer) TO authenticated;

-- ---------------------------------------------------------------------
-- 2. The deletion needs a sign-in of the last 5 minutes in the token
-- ---------------------------------------------------------------------
-- Everything else is the definition of 20261111120000_consent_archive.sql unchanged.
CREATE OR REPLACE FUNCTION public.delete_my_account() RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    uid     uuid := (SELECT auth.uid());
    claims  jsonb := (SELECT auth.jwt());
    amr     jsonb;
    mail    text;
    secret  text;
BEGIN
    IF uid IS NULL THEN
        RAISE EXCEPTION 'not signed in' USING ERRCODE = 'insufficient_privilege';
    END IF;

    -- the proof: a password, Google (oauth) or e-mail code (otp) sign-in of this session in the last 5 minutes
    -- (a token without amr, or with anything else in it, proves nothing; CASE keeps a bad value from failing the cast)
    amr := CASE WHEN jsonb_typeof(claims -> 'amr') = 'array' THEN claims -> 'amr' ELSE '[]'::jsonb END;
    IF NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(amr) AS e
        WHERE CASE WHEN jsonb_typeof(e) = 'object'
                        AND e ->> 'method' IN ('password', 'oauth', 'otp')
                        AND jsonb_typeof(e -> 'timestamp') = 'number'
                        AND (e ->> 'timestamp') ~ '^[0-9]{1,12}(\.[0-9]+)?$'
                   THEN to_timestamp((e ->> 'timestamp')::double precision) > now() - interval '5 minutes'
                   ELSE false END) THEN
        RAISE EXCEPTION 'recent_login_required' USING ERRCODE = 'insufficient_privilege';
    END IF;

    SELECT lower(btrim(u.email)) INTO mail FROM auth.users u WHERE u.id = uid;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'no such account';
    END IF;

    SELECT s.decrypted_secret INTO secret FROM vault.decrypted_secrets s WHERE s.name = 'consent_hmac_key';
    IF secret IS NULL OR secret = '' THEN
        RAISE EXCEPTION 'consent_hmac_key is not set: the account is not deleted';
    END IF;

    INSERT INTO public.consent_archive
        (user_id, email_hmac, consent_type, document_version, accepted_at, withdrawn_at)
    SELECT c.user_id,
           encode(extensions.hmac(coalesce(nullif(mail, ''), uid::text), secret, 'sha256'), 'hex'),
           c.consent_type, c.document_version, c.accepted_at, c.withdrawn_at
    FROM public.user_consent c
    WHERE c.user_id = uid;

    DELETE FROM auth.users WHERE id = uid;
END;
$$;

-- CREATE OR REPLACE keeps the privileges; they are stated again so this file alone says who may call it
REVOKE ALL ON FUNCTION public.delete_my_account() FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION public.delete_my_account() FROM anon;
    END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.delete_my_account() TO authenticated;

-- ---------------------------------------------------------------------
-- 3. The clean-up of Auth users that never became an account
-- ---------------------------------------------------------------------
CREATE FUNCTION public.clean_up_empty_users(p_dry_run boolean DEFAULT false) RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    n integer;
BEGIN
    -- at most 1000 a run, oldest first: a mistake can never take everything at once
    WITH candidate AS (
        SELECT u.id FROM auth.users u
        WHERE u.created_at < now() - interval '7 days'
          AND coalesce(u.last_sign_in_at, u.created_at) < now() - interval '7 days'
          -- no session used in the last 7 days either (a refresh moves refreshed_at, GoTrue 2024+; updated_at and
          -- created_at for older rows). refreshed_at is a timestamp without time zone, read as UTC (the server's zone).
          AND NOT EXISTS (SELECT 1 FROM auth.sessions s
                          WHERE s.user_id = u.id
                            AND coalesce(s.refreshed_at, s.updated_at, s.created_at) > now() - interval '7 days')
          AND NOT EXISTS (SELECT 1 FROM public.user_profile x WHERE x.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.user_consent x WHERE x.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.user_settings x WHERE x.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.user_role x WHERE x.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.user_subject x WHERE x.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.attempt x WHERE x.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.user_unit_progress x WHERE x.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.user_streak x WHERE x.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.activity_log x WHERE x.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.content_report x WHERE x.reporter_id = u.id OR x.resolved_by_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM public.content_download x WHERE x.user_id = u.id)
        ORDER BY u.created_at
        LIMIT 1000
        -- a user that is being signed in or changed right now is skipped, not waited for
        FOR UPDATE OF u SKIP LOCKED
    ), gone AS (
        -- they have no rows of the app; their Auth rows (identities, sessions) go with them. A dry run deletes nothing.
        DELETE FROM auth.users d USING candidate c WHERE d.id = c.id AND NOT p_dry_run
        RETURNING d.id
    )
    SELECT count(*) INTO n FROM candidate;
    RETURN n;
END;
$$;

-- only the server runs it (the schedule below, as the owner; the service role from the dashboard)
REVOKE ALL ON FUNCTION public.clean_up_empty_users(boolean) FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION public.clean_up_empty_users(boolean) FROM anon, authenticated;
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        PERFORM cron.schedule('maturimate-clean-up-empty-users', '45 3 * * *', 'SELECT public.clean_up_empty_users()');
    ELSE
        RAISE WARNING 'pg_cron is not installed: clean_up_empty_users() is not scheduled (llm/supabase-setup.md has the call)';
    END IF;
END;
$$;

-- PostgREST reads the new function and the new overload at once
NOTIFY pgrst, 'reload schema';
