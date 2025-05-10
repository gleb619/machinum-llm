INSERT INTO books (id, title, book_state)
VALUES('00000000-0000-0000-0000-000000000001',
       'novel_en_min',
       '{ "itemIndex": 0, "promptIndex": 0, "state": "CLEANING" }'::json)
       ON CONFLICT (id) DO NOTHING;

--SEPARATOR--

INSERT INTO chapter_info (id, source_key, "number", title, raw_text, proofread_text, summary, keywords, self_consistency, quotes, "characters", themes, perspective, tone, foreshadowing, NAMES, scenes, book_id)
VALUES('00000000-0000-0000-0000-000000000002',
       'https://some-site.com/1',
       1,
       'Chapter 1',
       'First chapter text.',
        NULL,
        NULL,
        '[]'::json,
        '{"questionsAndAnswers": []}'::json,
        '[]'::json,
        '[]'::json,
        NULL,
        NULL,
        NULL,
        NULL,
        '[]'::json,
        '[]'::json,
        '00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;

--SEPARATOR--

INSERT INTO chapter_info (id, source_key, "number", title, raw_text, proofread_text, summary, keywords, self_consistency, quotes, "characters", themes, perspective, tone, foreshadowing, NAMES, scenes, book_id)
VALUES('00000000-0000-0000-0000-000000000003',
       'https://some-site.org/2',
       2,
       'Chapter 2',
       'Second chapter text.',
        NULL,
        NULL,
        '[]'::json,
        '{"questionsAndAnswers": []}'::json,
        '[]'::json,
        '[]'::json,
        NULL,
        NULL,
        NULL,
        NULL,
        '[]'::json,
        '[]'::json,
        '00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;

--SEPARATOR--

INSERT INTO chapter_info (id, source_key, "number", title, raw_text, proofread_text, summary, keywords, self_consistency, quotes, "characters", themes, perspective, tone, foreshadowing, NAMES, scenes, book_id)
VALUES('00000000-0000-0000-0000-000000000004',
       'https://some-site.com/3',
       3,
       'Chapter 3',
       'Third chapter text.',
        NULL,
        NULL,
        '[]'::json,
        '{"questionsAndAnswers": []}'::json,
        '[]'::json,
        '[]'::json,
        NULL,
        NULL,
        NULL,
        NULL,
        '[]'::json,
        '[]'::json,
        '00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;

--SEPARATOR--