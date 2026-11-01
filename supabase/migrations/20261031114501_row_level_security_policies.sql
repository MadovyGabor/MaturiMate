-- Row level security policies. Only signed-in users (the authenticated role)
-- get any access; anon gets none. auth.uid() is wrapped in a SELECT so it is
-- evaluated once per query, not once per row.
--
-- Content tables (subject, topic, task and the rest of the task bank) get no
-- policy: the app receives content as a file (starter pack, then a download
-- from private storage), never through the API. Only the importer writes them,
-- with a key that bypasses row level security.

-- ---------------------------------------------------------------------
-- Rows of the user: read and write only their own
-- ---------------------------------------------------------------------

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'user_profile', 'user_settings', 'user_subject', 'attempt',
        'user_unit_progress', 'user_task_progress', 'user_streak', 'activity_log']
    LOOP
        EXECUTE format(
            'CREATE POLICY %I ON %I FOR SELECT TO authenticated USING (user_id = (SELECT auth.uid()))',
            t || '_select_own', t);
        EXECUTE format(
            'CREATE POLICY %I ON %I FOR INSERT TO authenticated WITH CHECK (user_id = (SELECT auth.uid()))',
            t || '_insert_own', t);
        EXECUTE format(
            'CREATE POLICY %I ON %I FOR UPDATE TO authenticated USING (user_id = (SELECT auth.uid())) WITH CHECK (user_id = (SELECT auth.uid()))',
            t || '_update_own', t);
    END LOOP;
END;
$$;

-- a user removes a subject they no longer study
CREATE POLICY user_subject_delete_own ON user_subject FOR DELETE TO authenticated
    USING (user_id = (SELECT auth.uid()));

-- ---------------------------------------------------------------------
-- Roles: a user picks only student, learner or teacher for themselves
-- (curator and admin are given on the server)
-- ---------------------------------------------------------------------

CREATE POLICY role_select_all ON role FOR SELECT TO authenticated USING (true);

CREATE POLICY user_role_select_own ON user_role FOR SELECT TO authenticated
    USING (user_id = (SELECT auth.uid()));
CREATE POLICY user_role_insert_own ON user_role FOR INSERT TO authenticated
    WITH CHECK (user_id = (SELECT auth.uid()) AND role_id IN (1, 2, 3));
CREATE POLICY user_role_delete_own ON user_role FOR DELETE TO authenticated
    USING (user_id = (SELECT auth.uid()) AND role_id IN (1, 2, 3));

-- ---------------------------------------------------------------------
-- Consents: a user adds them and may withdraw them, but never rewrites them
-- ---------------------------------------------------------------------

CREATE POLICY user_consent_select_own ON user_consent FOR SELECT TO authenticated
    USING (user_id = (SELECT auth.uid()));
CREATE POLICY user_consent_insert_own ON user_consent FOR INSERT TO authenticated
    WITH CHECK (user_id = (SELECT auth.uid()) AND withdrawn_at IS NULL);
CREATE POLICY user_consent_update_own ON user_consent FOR UPDATE TO authenticated
    USING (user_id = (SELECT auth.uid())) WITH CHECK (user_id = (SELECT auth.uid()));
-- only withdrawn_at can change
REVOKE UPDATE ON user_consent FROM authenticated;
GRANT UPDATE (withdrawn_at) ON user_consent TO authenticated;

-- ---------------------------------------------------------------------
-- Content reports: anyone signed in reports; curators and admins resolve
-- ---------------------------------------------------------------------

CREATE POLICY content_report_insert_own ON content_report FOR INSERT TO authenticated
    WITH CHECK (reporter_id = (SELECT auth.uid()) AND status = 'OPEN'
                AND resolved_at IS NULL AND resolved_by_id IS NULL);
CREATE POLICY content_report_select_own_or_staff ON content_report FOR SELECT TO authenticated
    USING (reporter_id = (SELECT auth.uid())
           OR EXISTS (SELECT 1 FROM user_role r
                      WHERE r.user_id = (SELECT auth.uid()) AND r.role_id IN (4, 5)));
CREATE POLICY content_report_update_staff ON content_report FOR UPDATE TO authenticated
    USING (EXISTS (SELECT 1 FROM user_role r
                   WHERE r.user_id = (SELECT auth.uid()) AND r.role_id IN (4, 5)))
    WITH CHECK (resolved_by_id = (SELECT auth.uid()));

-- ---------------------------------------------------------------------
-- Leaderboard: everyone signed in reads it; only the server writes it
-- ---------------------------------------------------------------------

CREATE POLICY season_select_all ON season FOR SELECT TO authenticated USING (true);
CREATE POLICY season_score_select_all ON season_score FOR SELECT TO authenticated USING (true);

-- ---------------------------------------------------------------------
-- Not signed in: no access at all, also without a matching policy
-- ---------------------------------------------------------------------

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon;
