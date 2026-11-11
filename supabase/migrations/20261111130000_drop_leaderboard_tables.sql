-- The leaderboard tables go (decision 2026-11-11, content/DECISIONS.md). Nothing in the app, the tests or the importer
-- uses them, they hold no rows, and points were dropped on 2026-11-03. A leaderboard, if it is ever built, comes back with
-- its own design: an XP source, a display name and a rule for who appears in it.
-- The policies, the indexes and the updated_at trigger of the tables go with them.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM season) OR EXISTS (SELECT 1 FROM season_score) THEN
        RAISE EXCEPTION 'season or season_score has rows: keep them or copy them before dropping the tables';
    END IF;
END;
$$;

DROP TABLE season_score;
DROP TABLE season;
