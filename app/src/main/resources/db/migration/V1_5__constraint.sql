ALTER TABLE chapter_info ADD CONSTRAINT chapter_info_unique UNIQUE (id);
ALTER TABLE books ADD CONSTRAINT books_unique UNIQUE (id);
