-- The permission to download a content pack (roadmap 12.4c, plan llm/outputs/plan-12.md section 6.3).
--
-- The packs lie in a private Cloudflare R2 bucket. The app asks a Cloudflare Worker for a link; the Worker calls this function
-- with the user's own token (so it runs as the user), and only when it answers does the Worker sign a short-lived link.
-- The function checks that the user studies the subject, counts the downloads of the day (3 per subject), writes the log row
-- (user, subject, version, time: nothing else) and returns where the pack is. It is SECURITY DEFINER because the log table
-- has no policy: neither the app nor the user can read or write it directly.

CREATE FUNCTION public.register_content_download(p_subject_id smallint, p_schema_version smallint)
RETURNS TABLE (object_path text, content_version bigint, size_bytes bigint, sha256 text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    uid      uuid := (SELECT auth.uid());
    release  public.content_release%ROWTYPE;
    per_day  constant integer := 3;
BEGIN
    IF uid IS NULL THEN
        RAISE EXCEPTION 'not signed in' USING ERRCODE = 'insufficient_privilege';
    END IF;

    -- only the subjects the user studies
    IF NOT EXISTS (SELECT 1 FROM public.user_subject
                   WHERE user_id = uid AND subject_id = p_subject_id AND removed_at IS NULL) THEN
        RAISE EXCEPTION 'subject not chosen' USING ERRCODE = 'insufficient_privilege';
    END IF;

    SELECT * INTO release FROM public.content_release r
    WHERE r.subject_id = p_subject_id AND r.schema_version = p_schema_version AND r.is_current;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'no release' USING ERRCODE = 'no_data_found';
    END IF;

    -- the daily limit stops a broken client that asks again and again
    IF (SELECT count(*) FROM public.content_download d
        WHERE d.user_id = uid AND d.subject_id = p_subject_id AND d.requested_at >= date_trunc('day', now())) >= per_day THEN
        RAISE EXCEPTION 'download_limit' USING ERRCODE = 'P0001';
    END IF;

    INSERT INTO public.content_download (user_id, subject_id, content_version)
    VALUES (uid, p_subject_id, release.content_version);

    RETURN QUERY SELECT release.object_path::text, release.content_version, release.size_bytes, release.sha256::text;
END;
$$;

REVOKE ALL ON FUNCTION public.register_content_download FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION public.register_content_download FROM anon;
    END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.register_content_download TO authenticated;
