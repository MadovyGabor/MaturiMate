-- The exam subjects the onboarding offers (DECISIONS 2026-10-31, CatalogSubject in the app).
-- user_subject refers to them, so they must exist before a user's subjects are saved, even
-- while their content is not imported yet. The importer keeps the same ids and codes.

INSERT INTO subject (subject_id, code, name_hu, name_sk, track) VALUES
    (1, 'HU_MJL', 'Magyar nyelv és irodalom', 'Maďarský jazyk a literatúra', 'HU'),
    (2, 'SK_SJL', 'Szlovák nyelv és irodalom', 'Slovenský jazyk a literatúra', 'BOTH'),
    (3, 'ANJ',    'Angol nyelv',               'Anglický jazyk',              'BOTH'),
    (4, 'MAT',    'Matematika',                'Matematika',                  'BOTH')
ON CONFLICT (subject_id) DO NOTHING;

-- the identity continues after the fixed ids
SELECT setval(pg_get_serial_sequence('subject', 'subject_id'), GREATEST((SELECT max(subject_id) FROM subject), 1));
