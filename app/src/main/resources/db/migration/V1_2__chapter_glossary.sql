drop view if exists chapter_glossary;
create or replace view chapter_glossary as
with data as (
    select
        MD5(ci.id || '-' || coalesce(elems.c1 ->> 'name', '') || '-' || coalesce(elems.c1 ->> 'category', '') || '-' || order_num)::text as id,
        ci.id as chapter_id,
        ci.source_key,
        ci.number,
        ci.title,
        ci.book_id,

        elems.c1 ->>'name' as name,
        elems.c1 ->>'category' as category,
        elems.c1 ->>'description' as description,
        elems.c1 ->>'ruName' IS NOT NULL as translated,
        elems.c1 ->>'ruName' as translated_name,
        elems.c1 #>> '{}' as raw_json
    from
        chapter_info ci,
        json_array_elements(names) WITH ORDINALITY AS elems (c1, order_num)
)
select
    c1.*,
    to_tsvector('english', c1.name) as search_string1,
    to_tsvector('english', c1.name) || to_tsvector('english', c1.description) as search_string2,
    lower(c1.name || ' ' || c1.description) as search_string3
from
    data c1;

CREATE INDEX idx_gin_content ON chapter_info USING GIN (to_tsvector('english', names));