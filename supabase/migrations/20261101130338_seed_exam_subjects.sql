-- The exam subjects the onboarding offers (DECISIONS 2026-10-31 and 2026-11-01, CatalogSubject in the app).
-- user_subject refers to them, so they must exist before a user's subjects are saved, even
-- while their content is not imported yet. The importer keeps the same ids and codes.

INSERT INTO subject (subject_id, code, name_hu, name_sk, track) VALUES
    (1, 'HU_MJL', 'Magyar nyelv és irodalom', 'Maďarský jazyk a literatúra', 'HU'),
    (2, 'SK_SJL', 'Szlovák nyelv és irodalom', 'Slovenský jazyk a literatúra', 'SK'),
    (3, 'ANJ_B1', 'Angol nyelv (B1)',          'Anglický jazyk (B1)',         'BOTH'),
    (6, 'ANJ_B2', 'Angol nyelv (B2)',          'Anglický jazyk (B2)',         'BOTH'),
    (4, 'MAT',    'Matematika',                'Matematika',                  'BOTH'),
    -- Slovak in Hungarian schools is its own exam, not SK_SJL
    (5, 'HU_SJSL', 'Szlovák nyelv és szlovák irodalom', 'Slovenský jazyk a slovenská literatúra', 'HU')
ON CONFLICT (subject_id) DO NOTHING;

-- the identity continues after the fixed ids
SELECT setval(pg_get_serial_sequence('subject', 'subject_id'), GREATEST((SELECT max(subject_id) FROM subject), 1));
