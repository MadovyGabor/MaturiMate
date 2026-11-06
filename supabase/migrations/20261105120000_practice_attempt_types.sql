-- Milestone 9a.2: two new kinds of round, the free practice (the student picks finished topics and a number of
-- questions) and the mistake practice (the tasks whose latest answer is wrong). Neither belongs to one unit or
-- one topic, so both have both ids empty. 'MISTAKE_PRACTICE' is 16 characters, so the column is widened.

ALTER TABLE attempt ALTER COLUMN attempt_type TYPE varchar(20);

ALTER TABLE attempt DROP CONSTRAINT ck_attempt_type;
ALTER TABLE attempt ADD CONSTRAINT ck_attempt_type
    CHECK (attempt_type IN ('UNIT_ROUND', 'EXTRA_PRACTICE', 'REVIEW', 'FREE_PRACTICE', 'MISTAKE_PRACTICE'));

-- unit rounds need the unit, a review needs the topic, a practice round has neither; never both
ALTER TABLE attempt DROP CONSTRAINT ck_attempt_scope;
ALTER TABLE attempt ADD CONSTRAINT ck_attempt_scope CHECK (
    (attempt_type IN ('UNIT_ROUND', 'EXTRA_PRACTICE')
        AND learning_unit_id IS NOT NULL AND topic_id IS NULL) OR
    (attempt_type = 'REVIEW'
        AND topic_id IS NOT NULL AND learning_unit_id IS NULL) OR
    (attempt_type IN ('FREE_PRACTICE', 'MISTAKE_PRACTICE')
        AND learning_unit_id IS NULL AND topic_id IS NULL));
