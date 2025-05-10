INSERT INTO books (id, title, book_state)
VALUES('00000000-0000-0000-0000-000000000001',
       'novel_en_min',
       '{ "itemIndex": 2, "promptIndex": 0, "state": "CLEANING" }'::json)
       ON CONFLICT (id) DO NOTHING;

--SEPARATOR--

INSERT INTO chapter_info (id, source_key, "number", title, text, proofread_text, summary, keywords, self_consistency, quotes, "characters", themes, perspective, tone, foreshadowing, NAMES, scenes, book_id)
VALUES('00000000-0000-0000-0000-000000000002',
       'https://my-site.ai/novel/chapter-1-30041322',
       1,
       'Chapter 1',
       '<no-data/>',
       '<no-data/>',
       '<no-data/>',
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

INSERT INTO chapter_info (id, source_key, "number", title, text, proofread_text, summary, keywords, self_consistency, quotes, "characters", themes, perspective, tone, foreshadowing, NAMES, scenes, book_id)
VALUES('00000000-0000-0000-0000-000000000003',
       'https://my-site.ai/novel/chapter-2-27051224',
       2,
       'Chapter 2',
       '<no-data/>',
'<no-data/>',
'<no-data/>',
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

INSERT INTO chapter_info (id, source_key, "number", title, text, proofread_text, summary, keywords, self_consistency, quotes, "characters", themes, perspective, tone, foreshadowing, NAMES, scenes, book_id)
VALUES('00000000-0000-0000-0000-000000000004',
       'https://my-site.ai/novel/chapter-3',
       3,
       'Chapter 3',
       '<no-data/>',
       '<no-data/>',
       '<no-data/>',
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