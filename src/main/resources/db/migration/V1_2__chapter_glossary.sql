drop view if exists chapter_glossary;
create or replace view chapter_glossary as
with data as (
    select
        MD5(ci.id || '-' || coalesce(c1 ->> 'name', ''))::text as id,
        ci.id as chapter_id,
        ci.source_key,
        ci.number,
        ci.title,
        ci.book_id,
        
        c1 ->>'name' as name,
        c1 ->>'category' as category,
        c1 ->>'description' as description,
        c1 ->>'ruName' IS NOT NULL as translated,
        c1 #>> '{}' as raw_json
    from
        chapter_info ci,
        json_array_elements(names) as c1
)
select
    c1.*,
    to_tsvector('english', c1.name) as search_string1,
    to_tsvector('english', c1.name) || to_tsvector('english', c1.description) as search_string2,
    lower(c1.name || ' ' || c1.description) as search_string3
from
    data c1;

CREATE INDEX idx_gin_content ON chapter_info USING GIN (to_tsvector('english', names));