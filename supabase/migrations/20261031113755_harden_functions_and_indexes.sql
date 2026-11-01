-- Fixes from the Supabase database advisors after the initial schema.

-- Security: a fixed search_path, so a caller's schema cannot change what the
-- trigger functions see.
ALTER FUNCTION fn_set_updated_at()           SET search_path = pg_catalog, public;
ALTER FUNCTION fn_topic_check_tree()         SET search_path = pg_catalog, public;
ALTER FUNCTION fn_require_leaf_topic()       SET search_path = pg_catalog, public;
ALTER FUNCTION fn_unit_task_check()          SET search_path = pg_catalog, public;
ALTER FUNCTION fn_learning_unit_type_check() SET search_path = pg_catalog, public;
ALTER FUNCTION fn_task_option_check()        SET search_path = pg_catalog, public;
ALTER FUNCTION fn_task_key_check()           SET search_path = pg_catalog, public;
ALTER FUNCTION fn_accepted_answer_check()    SET search_path = pg_catalog, public;

-- Security: Supabase's rls_auto_enable() helper runs as its owner from an event
-- trigger; app users must not call it through the API. It exists only on Supabase.
DO $$
BEGIN
    IF to_regprocedure('public.rls_auto_enable()') IS NOT NULL THEN
        REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM PUBLIC;
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
            REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM anon;
        END IF;
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
            REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM authenticated;
        END IF;
    END IF;
END;
$$;

-- Performance: indexes for the foreign keys that had none.
CREATE INDEX ix_attempt_topic             ON attempt (topic_id);
CREATE INDEX ix_content_report_reporter   ON content_report (reporter_id);
CREATE INDEX ix_content_report_resolver   ON content_report (resolved_by_id);
CREATE INDEX ix_task_key_option           ON task_key (task_id, option_id);
CREATE INDEX ix_task_key_prompt           ON task_key (task_id, prompt_option_id);
