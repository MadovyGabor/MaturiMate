-- Milestone 7.0a: lessons made of pages and boxes, the kind of a practice unit (mini test, topic test,
-- final test) and the explanation of a wrong option.

-- ---- the kind of a practice unit --------------------------------------
ALTER TABLE learning_unit ADD COLUMN practice_kind varchar(10);
UPDATE learning_unit SET practice_kind = 'TOPIC' WHERE unit_type = 'PRACTICE';
ALTER TABLE learning_unit ADD CONSTRAINT ck_learning_unit_kind CHECK (
    (unit_type = 'SUMMARY'  AND practice_kind IS NULL) OR
    (unit_type = 'PRACTICE' AND practice_kind IN ('MINI', 'TOPIC', 'FINAL')));

-- ---- lesson pages and their blocks (paragraphs and boxes) --------------
CREATE TABLE unit_page (
    learning_unit_id  bigint   NOT NULL,
    page_no           smallint NOT NULL,
    title             varchar(200),
    CONSTRAINT pk_unit_page PRIMARY KEY (learning_unit_id, page_no),
    CONSTRAINT fk_unit_page_unit FOREIGN KEY (learning_unit_id)
        REFERENCES learning_unit (learning_unit_id) ON DELETE CASCADE,
    CONSTRAINT ck_unit_page_no CHECK (page_no >= 1)
);

CREATE TABLE page_block (
    learning_unit_id  bigint      NOT NULL,
    page_no           smallint    NOT NULL,
    block_no          smallint    NOT NULL,
    block_type        varchar(10) NOT NULL,
    body_text         text        NOT NULL,
    CONSTRAINT pk_page_block PRIMARY KEY (learning_unit_id, page_no, block_no),
    CONSTRAINT fk_page_block_page FOREIGN KEY (learning_unit_id, page_no)
        REFERENCES unit_page (learning_unit_id, page_no) ON DELETE CASCADE,
    CONSTRAINT ck_page_block_no CHECK (block_no >= 1),
    CONSTRAINT ck_page_block_type CHECK (block_type IN ('PARAGRAPH', 'EXAMPLE', 'TIP', 'NOTE', 'WARNING'))
);

-- An existing lesson text becomes one page with one paragraph.
INSERT INTO unit_page (learning_unit_id, page_no, title)
SELECT learning_unit_id, 1, NULL FROM learning_unit WHERE unit_type = 'SUMMARY';
INSERT INTO page_block (learning_unit_id, page_no, block_no, block_type, body_text)
SELECT learning_unit_id, 1, 1, 'PARAGRAPH', body_text FROM learning_unit WHERE unit_type = 'SUMMARY';

ALTER TABLE learning_unit DROP CONSTRAINT ck_learning_unit_body;
ALTER TABLE learning_unit DROP COLUMN body_text;

-- Content is not exposed through the API: row level security without policies, like the other content tables.
ALTER TABLE unit_page ENABLE ROW LEVEL SECURITY;
ALTER TABLE page_block ENABLE ROW LEVEL SECURITY;

-- ---- the explanation of a wrong option ---------------------------------
ALTER TABLE task_option ADD COLUMN why text;

-- ---- final tests sit on parent topics ----------------------------------
-- Every unit but a FINAL test belongs to a leaf topic; a FINAL test belongs to a parent topic.
CREATE FUNCTION fn_learning_unit_check_topic() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public AS $$
DECLARE
    v_has_children boolean;
BEGIN
    SELECT EXISTS (SELECT 1 FROM public.topic WHERE parent_topic_id = NEW.topic_id) INTO v_has_children;
    IF NEW.practice_kind = 'FINAL' AND NOT v_has_children THEN
        RAISE EXCEPTION 'learning unit %: a FINAL test belongs to a parent topic, but topic % has no sub-topics',
            NEW.learning_unit_id, NEW.topic_id;
    END IF;
    IF NEW.practice_kind IS DISTINCT FROM 'FINAL' AND v_has_children THEN
        RAISE EXCEPTION 'topic % has sub-topics; only FINAL tests belong to it', NEW.topic_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER trg_learning_unit_leaf_topic ON learning_unit;
CREATE TRIGGER trg_learning_unit_check_topic
    BEFORE INSERT OR UPDATE OF topic_id, practice_kind ON learning_unit
    FOR EACH ROW EXECUTE FUNCTION fn_learning_unit_check_topic();

-- A topic that holds tasks or non-FINAL learning units cannot get sub-topics.
CREATE OR REPLACE FUNCTION fn_topic_check_tree() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public AS $$
DECLARE
    v_ancestor bigint;
BEGIN
    IF NEW.parent_topic_id IS NULL THEN
        RETURN NEW;
    END IF;

    -- a topic must not be moved below itself or one of its descendants
    IF TG_OP = 'UPDATE' THEN
        v_ancestor := NEW.parent_topic_id;
        WHILE v_ancestor IS NOT NULL LOOP
            IF v_ancestor = NEW.topic_id THEN
                RAISE EXCEPTION 'topic % cannot be placed below itself or its descendants',
                    NEW.topic_id;
            END IF;
            SELECT parent_topic_id INTO v_ancestor
              FROM public.topic WHERE topic_id = v_ancestor;
        END LOOP;
    END IF;

    IF EXISTS (SELECT 1 FROM public.task WHERE topic_id = NEW.parent_topic_id)
       OR EXISTS (SELECT 1 FROM public.learning_unit
                   WHERE topic_id = NEW.parent_topic_id AND practice_kind IS DISTINCT FROM 'FINAL') THEN
        RAISE EXCEPTION 'topic % already holds tasks or learning units and cannot get sub-topics',
            NEW.parent_topic_id;
    END IF;
    RETURN NEW;
END;
$$;
