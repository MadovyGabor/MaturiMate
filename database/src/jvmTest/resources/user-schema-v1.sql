-- The user database as version 1 created it (before the migration 1.sqm), taken from the .sq files of commit
-- bc742f4^. Used by SchemaMigrationTest: this schema, migrated to the newest version, must equal a freshly created
-- one. Do not edit.

-- from Profile.sq
-- Profile, role, settings and subjects of the user. Same tables as in supabase/migrations,
-- without the foreign keys to auth.users, role and subject (server-only and content-database tables).
-- Phone only: first_name and last_name may be empty, because a guest has no name until sign-up.
CREATE TABLE user_profile (
    user_id            TEXT    NOT NULL,
    first_name         TEXT,
    last_name          TEXT,
    display_name       TEXT,
    date_of_birth      TEXT,
    school_type        TEXT,
    teaching_language  TEXT,
    graduation_date    TEXT,
    CONSTRAINT pk_user_profile PRIMARY KEY (user_id),
    CONSTRAINT ck_user_profile_birth CHECK (date_of_birth >= '1900-01-01'),
    CONSTRAINT ck_user_profile_school_type
        CHECK (school_type IN ('GYMNASIUM', 'VOCATIONAL', 'OTHER')),
    CONSTRAINT ck_user_profile_language CHECK (teaching_language IN ('SK', 'HU'))
);

-- role_id follows the role catalogue seeded in supabase/migrations (1 STUDENT, 2 LEARNER, 3 TEACHER, 4 CURATOR, 5 ADMIN)
CREATE TABLE user_role (
    user_id      TEXT    NOT NULL,
    role_id      INTEGER NOT NULL,
    assigned_at  INTEGER NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT ck_user_role_role CHECK (role_id BETWEEN 1 AND 5)
);

CREATE TABLE user_settings (
    user_id             TEXT    NOT NULL,
    ui_language         TEXT    NOT NULL DEFAULT 'SK',
    timezone            TEXT    NOT NULL DEFAULT 'Europe/Bratislava',
    theme               TEXT    NOT NULL DEFAULT 'SYSTEM',
    daily_goal_minutes  INTEGER DEFAULT 10,
    reminder_enabled    INTEGER NOT NULL DEFAULT 0 CHECK (reminder_enabled IN (0, 1)),
    reminder_time       TEXT,
    CONSTRAINT pk_user_settings PRIMARY KEY (user_id),
    CONSTRAINT ck_user_settings_language CHECK (ui_language IN ('SK', 'HU')),
    CONSTRAINT ck_user_settings_theme CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    CONSTRAINT ck_user_settings_goal
        CHECK (daily_goal_minutes BETWEEN 1 AND 240),
    CONSTRAINT ck_user_settings_reminder
        CHECK (reminder_enabled = 0 OR reminder_time IS NOT NULL)
);

CREATE TABLE user_subject (
    user_id       TEXT    NOT NULL,
    subject_id    INTEGER NOT NULL,
    target_score  REAL,
    source        TEXT    NOT NULL DEFAULT 'CHOSEN',
    added_at      INTEGER NOT NULL,
    CONSTRAINT pk_user_subject PRIMARY KEY (user_id, subject_id),
    CONSTRAINT ck_user_subject_score CHECK (target_score BETWEEN 0 AND 100),
    CONSTRAINT ck_user_subject_source CHECK (source IN ('AUTO', 'CHOSEN'))
);

-- Phone only: whose rows the local tables hold. A guest gets a temporary id ("guest-" and a
-- random UUID); sign-up replaces it with the account's id (roadmap step 6.5). One row at most.
CREATE TABLE local_user (
    slot      INTEGER NOT NULL CONSTRAINT pk_local_user PRIMARY KEY,
    user_id   TEXT    NOT NULL,
    is_guest  INTEGER NOT NULL DEFAULT 1 CHECK (is_guest IN (0, 1)),
    CONSTRAINT ck_local_user_slot CHECK (slot = 1)
);

-- from Progress.sq
-- Practice rounds and progress of the user. Same tables as in supabase/migrations,
-- without the foreign keys to auth.users and to content tables (topic, learning_unit, task).
CREATE TABLE attempt (
    attempt_id        INTEGER CONSTRAINT pk_attempt PRIMARY KEY,
    user_id           TEXT    NOT NULL,
    attempt_type      TEXT    NOT NULL,
    topic_id          INTEGER,
    learning_unit_id  INTEGER,
    status            TEXT    NOT NULL DEFAULT 'IN_PROGRESS',
    started_at        INTEGER NOT NULL,
    finished_at       INTEGER,
    earned_points     INTEGER,
    max_points        INTEGER,
    CONSTRAINT ck_attempt_type
        CHECK (attempt_type IN ('UNIT_ROUND', 'EXTRA_PRACTICE', 'REVIEW')),
    CONSTRAINT ck_attempt_status
        CHECK (status IN ('IN_PROGRESS', 'FINISHED', 'ABANDONED')),
    -- unit rounds need the unit, a review needs the topic; never both
    CONSTRAINT ck_attempt_scope CHECK (
        (attempt_type IN ('UNIT_ROUND', 'EXTRA_PRACTICE')
            AND learning_unit_id IS NOT NULL AND topic_id IS NULL) OR
        (attempt_type = 'REVIEW'
            AND topic_id IS NOT NULL AND learning_unit_id IS NULL)),
    CONSTRAINT ck_attempt_finished CHECK (finished_at IS NULL OR finished_at >= started_at),
    CONSTRAINT ck_attempt_points CHECK (
        earned_points >= 0 AND max_points >= 0 AND earned_points <= max_points)
);

CREATE TABLE user_unit_progress (
    user_id           TEXT    NOT NULL,
    learning_unit_id  INTEGER NOT NULL,
    status            TEXT    NOT NULL DEFAULT 'IN_PROGRESS',
    started_at        INTEGER NOT NULL,
    completed_at      INTEGER,
    CONSTRAINT pk_user_unit_progress PRIMARY KEY (user_id, learning_unit_id),
    CONSTRAINT ck_user_unit_progress_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_user_unit_progress_completed CHECK (
        (status = 'COMPLETED') = (completed_at IS NOT NULL))
);

CREATE TABLE user_task_progress (
    user_id           TEXT    NOT NULL,
    task_id           INTEGER NOT NULL,
    times_correct     INTEGER NOT NULL DEFAULT 0,
    times_wrong       INTEGER NOT NULL DEFAULT 0,
    correct_streak    INTEGER NOT NULL DEFAULT 0,
    last_answered_at  INTEGER NOT NULL,
    next_review_at    INTEGER,
    status            TEXT    NOT NULL DEFAULT 'LEARNING',
    CONSTRAINT pk_user_task_progress PRIMARY KEY (user_id, task_id),
    CONSTRAINT ck_user_task_progress_counts
        CHECK (times_correct >= 0 AND times_wrong >= 0 AND correct_streak >= 0),
    CONSTRAINT ck_user_task_progress_status CHECK (status IN ('LEARNING', 'MASTERED'))
);

CREATE INDEX ix_attempt_user_started    ON attempt (user_id, started_at);
CREATE INDEX ix_attempt_unit            ON attempt (learning_unit_id);
CREATE INDEX ix_user_unit_progress_unit ON user_unit_progress (learning_unit_id);
CREATE INDEX ix_user_task_progress_due  ON user_task_progress (user_id, next_review_at);
CREATE INDEX ix_user_task_progress_task ON user_task_progress (task_id);

-- attempt_id is assigned by SQLite (INTEGER PRIMARY KEY is the rowid)

-- from Activity.sq
-- Study streak and daily activity of the user. Same tables as in supabase/migrations,
-- without the foreign keys to auth.users.
CREATE TABLE user_streak (
    user_id             TEXT    NOT NULL,
    current_streak      INTEGER NOT NULL DEFAULT 0,
    longest_streak      INTEGER NOT NULL DEFAULT 0,
    last_activity_date  TEXT,
    CONSTRAINT pk_user_streak PRIMARY KEY (user_id),
    CONSTRAINT ck_user_streak_values
        CHECK (current_streak >= 0 AND longest_streak >= current_streak)
);

CREATE TABLE activity_log (
    activity_id      INTEGER CONSTRAINT pk_activity_log PRIMARY KEY,
    user_id          TEXT    NOT NULL,
    activity_date    TEXT    NOT NULL,
    xp_earned        INTEGER NOT NULL DEFAULT 0,
    minutes_studied  INTEGER NOT NULL DEFAULT 0,
    tasks_answered   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_activity_log_day UNIQUE (user_id, activity_date),
    CONSTRAINT ck_activity_log_values
        CHECK (xp_earned >= 0 AND minutes_studied >= 0 AND tasks_answered >= 0)
);
