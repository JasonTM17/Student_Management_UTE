-- V54: Update Chatbot Assistant Knowledge to CampusUTE branding and reinforce credit policy (28 standard / 30 max exception)

-- 1. Replace 'CampusCore' with 'CampusUTE' across all knowledge documents
UPDATE assistant.knowledge_document
SET title = REPLACE(title, 'CampusCore', 'CampusUTE'),
    content = REPLACE(content, 'CampusCore', 'CampusUTE')
WHERE title LIKE '%CampusCore%' OR content LIKE '%CampusCore%';

-- 2. Ensure withdrawal-credit-limits documents reinforce the 28 standard / 30 max policy and CampusUTE branding
UPDATE assistant.knowledge_document
SET content = 'Quy chế về rút môn học và giới hạn khối lượng học tập tại CampusUTE: 1. Rút học phần: Sinh viên được nộp đơn rút môn học trong vòng 2 tuần đầu của học kỳ chính. Học phần được chấp thuận rút sẽ ghi nhận điểm chữ W (Withdrawn) trên bảng điểm và không tính vào điểm trung bình GPA. Rút môn sau thời hạn quy định sẽ không được xem xét và không được hoàn phí. 2. Giới hạn tín chỉ: Mỗi học kỳ chính, sinh viên được đăng ký tối thiểu 14 tín chỉ (trừ học kỳ cuối) và tối đa tiêu chuẩn 28 tín chỉ. Chỉ khi có đơn xin nâng hạn mức được Phòng Đào tạo phê duyệt, sinh viên mới được đăng ký vượt mức chuẩn, nhưng tổng số không quá 30 tín chỉ. Học kỳ phụ (học kỳ hè) được đăng ký tối đa 8 đến 10 tín chỉ.'
WHERE slug = 'withdrawal-credit-limits-vi' AND locale = 'vi';

UPDATE assistant.knowledge_document
SET content = 'Rules on course withdrawal and credit workload at CampusUTE: 1. Course withdrawal: Students may request course withdrawal during the first 2 weeks of a regular semester. Approved courses receive grade W and are excluded from GPA calculation. Late withdrawal requests are rejected with no refund. 2. Credit limits: Each regular semester requires a minimum of 14 credits (except final semester) and a standard maximum of 28 credits. Students may exceed the standard limit only with an application approved by Academic Affairs, capped at 30 credits. Summer terms allow a maximum of 8 to 10 credits.'
WHERE slug = 'withdrawal-credit-limits-en' AND locale = 'en';

-- 3. Also update any announcements or notifications mentioning CampusCore
UPDATE academic."Announcement"
SET content = REPLACE(content, 'CampusCore', 'CampusUTE'),
    title = REPLACE(title, 'CampusCore', 'CampusUTE')
WHERE title LIKE '%CampusCore%' OR content LIKE '%CampusCore%';

UPDATE academic."Notification"
SET content = REPLACE(content, 'CampusCore', 'CampusUTE'),
    title = REPLACE(title, 'CampusCore', 'CampusUTE')
WHERE title LIKE '%CampusCore%' OR content LIKE '%CampusCore%';

-- 4. Archive currently published revisions of updated documents
UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (
      SELECT d.id FROM assistant.knowledge_document d
      WHERE d.title LIKE '%CampusUTE%' OR d.content LIKE '%CampusUTE%'
  );

-- 5. Insert new PUBLISHED revisions for all updated documents
WITH target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
    GROUP BY d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content
)
INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority,
     created_by, reviewed_by, published_at, domain)
SELECT md5(t.id::text || '-revision-' || t.next_version::text)::uuid,
       t.id, t.next_version, 'PUBLISHED', t.locale, t.slug, t.title, t.content, t.source,
       t.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP, t.domain
FROM target t
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_document_revision existing
    WHERE existing.document_id = t.id
      AND existing.state = 'PUBLISHED'
      AND existing.content = t.content
      AND existing.title = t.title
);

-- 6. Canonical summary and new knowledge release
WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'POLICY') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r
      ON r.document_id = d.id
     AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE
      AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(
               digest(
                   COALESCE(
                       string_agg(
                           concat_ws('|', source_id, domain, slug, locale, title, content, source,
                                     priority::text, version::text),
                           E'\n' ORDER BY source_id
                       ),
                       ''
                   ),
                   'sha256'
               ),
               'hex'
           ) AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(
               jsonb_agg(
                   jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale)
                   ORDER BY source_id
               ),
               '[]'::jsonb
           ) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000054'::uuid,
       'campusute-v54', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object(
           'schemaVersion', 1,
           'corpusVersion', 'campusute-v54',
           'rowCount', row_count,
           'sha256', corpus_hash,
           'documents', documents
       ),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id
        FROM assistant.knowledge_runtime_state
        WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_release
    WHERE id = '00000000-0000-0000-0000-000000000054'::uuid
);

-- 7. Populate runtime documents for the new release
INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source,
     priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000054'::uuid,
       d.id::text, r.id, r.version, COALESCE(r.domain, d.domain, 'POLICY'), d.slug, d.locale,
       d.title, d.content, d.source, d.priority, TRUE, 'PUBLIC',
       COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r
  ON r.document_id = d.id
 AND r.state = 'PUBLISHED'
WHERE d.active = TRUE
  AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1
      FROM assistant.knowledge_runtime_document existing
      WHERE existing.release_id = '00000000-0000-0000-0000-000000000054'::uuid
        AND existing.source_id = d.id::text
  );

-- 8. Switch active runtime release to v54
INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000054'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id,
    updated_at = CURRENT_TIMESTAMP;
