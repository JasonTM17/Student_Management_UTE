-- V98: align the certificate knowledge copy with the actual portal (follow-up
-- to V96's factual recovery, found by the final audit round). The live copy
-- told students to "not direct them to a nonexistent Student Certificates
-- page" — but the portal HAS that page (/dashboard/certificates, "Giấy xác
-- nhận điện tử"): it generates and prints documents online, it just does not
-- accept or track official requests. Also fixes three missing spaces in the
-- English attendance copy that the lexical fast path serves verbatim.
--
-- Protocol mirrors V96: md5(title || '\n' || content) hash-guard so a
-- hand-edited document is never clobbered, archive the superseded PUBLISHED
-- revision, append a reviewed revision, publish the local release …0098 and
-- flip the runtime state. Re-runnable: hash guards and NOT EXISTS everywhere.

CREATE TEMP TABLE v98_copy_fix (slug text PRIMARY KEY, old_hash text,
                               title text, content text) ON COMMIT DROP;
INSERT INTO v98_copy_fix (slug, old_hash, title, content) VALUES
    ('faq-student-certificates-vi', 'ab10a647ec671fd52110e994739f14dd',
     'Giấy xác nhận sinh viên: bản in trên cổng và bản có dấu của trường',
     E'## Giấy xác nhận sinh viên\n
\n- **Trên cổng CampusUTE**: trang Giấy xác nhận điện tử (mục Chứng thực sinh viên) tạo và in ngay các loại giấy xác nhận phổ biến — bản in này phục vụ đối chiếu, không thay thế bản có dấu của trường.\n
- **Giấy tờ cần đơn vị xác nhận chính thức**: loại giấy mang giá trị pháp lý (xác nhận mục đích vay vốn, bảng điểm có xác nhận…) liên hệ Phòng Đào tạo hoặc bộ phận một cửa; thời gian cấp theo quy định hiện hành, cổng không cam kết thời gian thay đơn vị phát hành.\n
- **Bị từ chối hoặc sai thông tin**: đối chiếu lại dữ liệu học vụ trên cổng (môn học, điểm, trạng thái) rồi phản hồi với bộ phận tiếp nhận kèm bản in từ cổng.'),
    ('faq-student-certificates-en', 'c3636f9405f59fb073d1692cc63e6311',
     'Student certificates: portal printing and official issuance',
     E'## Student certificates\n
\n- **On the CampusUTE portal**: the Student Certificates page (e-certificates) generates and prints common confirmation documents instantly — these printouts are for reference and do not replace the university''s stamped originals.\n
- **Documents needing official certification**: legally binding types (loan-purpose confirmation, certified transcripts) go to the Academic Affairs office or the one-stop service desk; issuance time follows current regulations and the portal does not commit a time on behalf of the issuing office.\n
- **Rejections or wrong details**: re-check your academic data in the portal (courses, grades, status) and follow up with the receiving office, attaching the portal printout.'),
    ('catalog-attendance-policy-en', 'd6bfaa8333f24bfdb5640074177d4ecf',
     'Attendance and the 80% exam-forfeiture threshold',
     E'## Attendance and participation\n
\n- **Recording**: lecturers take attendance for each session directly in the portal (the Attendance page); students see their per-course attendance status in the class detail view.\n
- **Weight**: the participation score is part of the continuous assessment, typically around 10% alongside the midterm and assignments — the exact weight is published by the lecturer in the course syllabus.\n
- **Forfeiture threshold**: a student absent from more than 20% of a course''s theory sessions is barred from the final exam (at least 80% attendance is required); practical courses require higher attendance (usually 100% of practical sessions).\n
- **Exemptions and appeals**: justified absences (illness, university duties) are handled by an exemption request with evidence; report attendance errors to the lecturer within two weeks of the session.')
ON CONFLICT (slug) DO NOTHING;

-- 1. Hash-guarded in-place copy fix (never clobbers a hand-edited document,
--    never touches a document that already received a later revision).
CREATE TEMP TABLE v98_fixed_document (id uuid PRIMARY KEY, slug text) ON COMMIT DROP;

WITH corrected AS (
    UPDATE assistant.knowledge_document d
       SET title = fix.title,
           content = fix.content,
           updated_at = CURRENT_TIMESTAMP
      FROM v98_copy_fix fix
     WHERE d.slug = fix.slug
       AND md5(d.title || E'\n' || d.content) = fix.old_hash
    RETURNING d.id, d.slug
)
INSERT INTO v98_fixed_document (id, slug) SELECT id, slug FROM corrected;

-- 2. Archive the superseded PUBLISHED revisions of the fixed documents.
UPDATE assistant.knowledge_document_revision r
   SET state = 'ARCHIVED'
  FROM v98_fixed_document f
 WHERE r.document_id = f.id AND r.state = 'PUBLISHED';

-- 3. Append the reviewed revision (version = max + 1 per fixed document).
INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority,
     created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-' || (COALESCE(r.max_version, 0) + 1)::text || '-v98')::uuid,
       d.id, COALESCE(r.max_version, 0) + 1, 'PUBLISHED', d.locale, d.slug,
       cf.title, cf.content, d.source, d.priority,
       'system-migration', 'system-migration', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
JOIN v98_fixed_document f ON f.id = d.id
JOIN v98_copy_fix cf ON cf.slug = f.slug
LEFT JOIN (SELECT document_id, MAX(version) AS max_version
             FROM assistant.knowledge_document_revision
            GROUP BY document_id) r ON r.document_id = d.id;

-- 4. Publish the corrected local corpus as release …0098 and activate it.
WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'THESIS') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(thesis.digest(COALESCE(string_agg(concat_ws('|', source_id, domain, slug, locale, title, content, source, priority::text, version::text), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000098'::uuid, 'local-demo-v98', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v98', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000098'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000098'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000098'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000098'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
