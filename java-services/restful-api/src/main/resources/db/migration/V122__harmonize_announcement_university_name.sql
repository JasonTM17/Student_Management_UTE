-- V122: Harmonize remaining legacy university-name strings in mutable announcement
-- rows and article-gallery captions. Forward-only data repair — no applied
-- migration is touched, matching the V45 branding harmonization pattern.

-- 1. Accent-correct variant: 'Sư phạm Kỹ thuật' -> 'Công nghệ Kỹ thuật'
UPDATE engagement."Announcement"
SET content = REPLACE(content, 'Sư phạm Kỹ thuật', 'Công nghệ Kỹ thuật'),
    title = REPLACE(title, 'Sư phạm Kỹ thuật', 'Công nghệ Kỹ thuật'),
    "publishedBy" = REPLACE("publishedBy", 'Sư phạm Kỹ thuật', 'Công nghệ Kỹ thuật')
WHERE content ILIKE '%Sư phạm Kỹ thuật%'
   OR title ILIKE '%Sư phạm Kỹ thuật%'
   OR "publishedBy" ILIKE '%Sư phạm Kỹ thuật%';

-- 2. Uppercase banner variant: 'SƯ PHẠM KỸ THUẬT' -> 'CÔNG NGHỆ KỸ THUẬT'
UPDATE engagement."Announcement"
SET content = REPLACE(content, 'SƯ PHẠM KỸ THUẬT', 'CÔNG NGHỆ KỸ THUẬT'),
    title = REPLACE(title, 'SƯ PHẠM KỸ THUẬT', 'CÔNG NGHỆ KỸ THUẬT'),
    "publishedBy" = REPLACE("publishedBy", 'SƯ PHẠM KỸ THUẬT', 'CÔNG NGHỆ KỸ THUẬT')
WHERE content ILIKE '%SƯ PHẠM KỸ THUẬT%'
   OR title ILIKE '%SƯ PHẠM KỸ THUẬT%'
   OR "publishedBy" ILIKE '%SƯ PHẠM KỸ THUẬT%';

-- 3. Unaccented banner variant: 'SU PHAM KY THUAT' -> 'CONG NGHE KY THUAT'
UPDATE engagement."Announcement"
SET content = REPLACE(content, 'SU PHAM KY THUAT', 'CONG NGHE KY THUAT'),
    title = REPLACE(title, 'SU PHAM KY THUAT', 'CONG NGHE KY THUAT'),
    "publishedBy" = REPLACE("publishedBy", 'SU PHAM KY THUAT', 'CONG NGHE KY THUAT')
WHERE content ILIKE '%SU PHAM KY THUAT%'
   OR title ILIKE '%SU PHAM KY THUAT%'
   OR "publishedBy" ILIKE '%SU PHAM KY THUAT%';

-- 4. Catch-all pass for any casing the literal passes miss (e.g.
-- 'Sư Phạm Kỹ Thuật'); the official name is kept in title case.
UPDATE engagement."Announcement"
SET content = REGEXP_REPLACE(content, 'sư phạm kỹ thuật', 'Công nghệ Kỹ thuật', 'gi'),
    title = REGEXP_REPLACE(title, 'sư phạm kỹ thuật', 'Công nghệ Kỹ thuật', 'gi'),
    "publishedBy" = REGEXP_REPLACE("publishedBy", 'sư phạm kỹ thuật', 'Công nghệ Kỹ thuật', 'gi')
WHERE content ~* 'sư phạm kỹ thuật'
   OR title ~* 'sư phạm kỹ thuật'
   OR "publishedBy" ~* 'sư phạm kỹ thuật';

UPDATE engagement."Announcement"
SET content = REGEXP_REPLACE(content, 'su pham ky thuat', 'Công nghệ Kỹ thuật', 'gi'),
    title = REGEXP_REPLACE(title, 'su pham ky thuat', 'Công nghệ Kỹ thuật', 'gi'),
    "publishedBy" = REGEXP_REPLACE("publishedBy", 'su pham ky thuat', 'Công nghệ Kỹ thuật', 'gi')
WHERE content ~* 'su pham ky thuat'
   OR title ~* 'su pham ky thuat'
   OR "publishedBy" ~* 'su pham ky thuat';

-- 5. Gallery captions carry the same institutional name.
UPDATE engagement."ArticleMediaGallery"
SET "captionVi" = REGEXP_REPLACE("captionVi", 'sư phạm kỹ thuật', 'Công nghệ Kỹ thuật', 'gi')
WHERE "captionVi" ~* 'sư phạm kỹ thuật';

UPDATE engagement."ArticleMediaGallery"
SET "captionEn" = REGEXP_REPLACE("captionEn", 'su pham ky thuat', 'CONG NGHE KY THUAT', 'gi')
WHERE "captionEn" ~* 'su pham ky thuat';
