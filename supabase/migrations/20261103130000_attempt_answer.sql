-- Milestone 7.5: every answer of a test round is recorded, so the app can later show which tasks a student got
-- wrong (mistake practice, statistics). One row per task of a round.

-- An answer must belong to an attempt of the same user: the foreign key below uses this pair.
ALTER TABLE attempt ADD CONSTRAINT uq_attempt_user UNIQUE (attempt_id, user_id);

CREATE TABLE attempt_answer (
    attempt_id     bigint    NOT NULL,
    task_id        bigint    NOT NULL,
    user_id        uuid      NOT NULL,
    is_correct     boolean   NOT NULL,
    earned_points  smallint  NOT NULL,
    max_points     smallint  NOT NULL,
    answered_at    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- what the student answered, as short text (for example '1=B;2=A'); for review and statistics
    given_answer   text,
    CONSTRAINT pk_attempt_answer PRIMARY KEY (attempt_id, task_id),
    CONSTRAINT fk_attempt_answer_attempt FOREIGN KEY (attempt_id, user_id)
        REFERENCES attempt (attempt_id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_answer_task FOREIGN KEY (task_id)
        REFERENCES task (task_id) ON DELETE RESTRICT,
    CONSTRAINT ck_attempt_answer_points CHECK (
        earned_points >= 0 AND max_points >= 0 AND earned_points <= max_points)
);

CREATE INDEX ix_attempt_answer_attempt   ON attempt_answer (attempt_id, user_id);
CREATE INDEX ix_attempt_answer_task      ON attempt_answer (task_id);
CREATE INDEX ix_attempt_answer_user_time ON attempt_answer (user_id, answered_at);

-- A user reads and writes only their own answers.
ALTER TABLE attempt_answer ENABLE ROW LEVEL SECURITY;

CREATE POLICY attempt_answer_select_own ON attempt_answer FOR SELECT TO authenticated
    USING (user_id = (SELECT auth.uid()));
CREATE POLICY attempt_answer_insert_own ON attempt_answer FOR INSERT TO authenticated
    WITH CHECK (user_id = (SELECT auth.uid()));
CREATE POLICY attempt_answer_update_own ON attempt_answer FOR UPDATE TO authenticated
    USING (user_id = (SELECT auth.uid())) WITH CHECK (user_id = (SELECT auth.uid()));
