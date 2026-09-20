-- V72: reconcile the remaining thesis group-size statements in the SERVED
-- assistant corpus, and publish a visible correction notice for the superseded
-- 1-3 phrasing.
--
-- AUTHORITY
--   The faculty has authoritatively confirmed the thesis/capstone group rule:
--   a group has 3 to 4 members, with exactly one group leader.
--
-- WHY THE DOCUMENTS AND NOT THE CODE
--   The code already implements the ruling and is correct:
--   ThesisMutationService pins MIN_GROUP_MEMBERS = 3 / MAX_GROUP_MEMBERS = 4.
--   The defect was user-facing copy. A student asking the assistant
--   "một nhóm tối đa mấy thành viên?" could be answered from a document that
--   stated a competing 1-3 rule, while the UI rendered 3-4 and the API accepted
--   a fourth member. Reconciling the documents removes that self-contradiction;
--   changing the code would have broken the ruling instead.
--
-- WHY A NEW MIGRATION INSTEAD OF EDITING V38 / V43 / V57 / V67 / V68
--   Every migration in this directory is already applied on the deployed system
--   and Flyway validates checksums on each boot, so editing an applied file would
--   break the deployment and erase the provenance of what was actually executed.
--   V38 / V43 / V57 are immutable history. V67 aligned four corpus slugs but did
--   not cover the portal service directory, and V68 seeds a published
--   announcement whose body says "Mỗi nhóm từ 1 đến tối đa 3 sinh viên" in any
--   environment built from this repository. Additive reconciliation is the only
--   safe route, so this migration only appends.
--
-- WHAT WAS ACTUALLY WRONG (verified against the live database, not inferred from
-- the migration files: every other slug had already been corrected by V67)
--   assistant.knowledge_document.campus-portal-comprehensive-service-directory-en
--       "...student group formation (min 2 members)..."
--   assistant.knowledge_document.campus-portal-comprehensive-service-directory-vi
--       "...đăng ký nhóm sinh viên (tối thiểu 2 người)..."
--   Both statements understated the floor, and the English one also omitted the
--   one-leader rule. These two documents are the only corpus rows that still
--   disagreed with the ruling, and both were the live PUBLISHED revision served
--   through assistant.knowledge_runtime_state.active_release_id.
--
-- WHAT THIS MIGRATION DOES
--   1. Corrects those two documents to the 3-4 rule and states the one-leader
--      rule in the same sentence.
--   2. Archives their superseded PUBLISHED revision and publishes a new immutable
--      revision per document (md5-derived id, mirroring V67).
--   3. Rebuilds and activates a fresh immutable release (thesis-group-size-v72)
--      so the correction is genuinely served, not merely stored.
--   4. Publishes a NEW correction announcement instead of rewriting the
--      historical body. engagement."Announcement" is under governance
--      (V15 announcement audit, V60 admin audit trail) and
--      AnnouncementHtmlSanitizer deliberately applies on writes only, leaving
--      historical rows untouched because "no undo exists". Editing published
--      institutional content in place would destroy that audit trail, so the
--      correction is appended as its own published notice.
--
-- Idempotent: every statement is guarded, revision ids are md5-derived, and the
-- announcement/audit inserts use NOT EXISTS / ON CONFLICT so a re-run cannot
-- duplicate rows or clobber a later administrator edit.

-- 1. Correct the two remaining divergent corpus documents. The nested REPLACE
--    handles the full parenthetical first and the bare fragment second, so a
--    differently punctuated variant is still caught. The WHERE guard makes a
--    re-run a no-op.
UPDATE assistant.knowledge_document
SET content = REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(
                    content,
                    'student group formation (min 2 members)',
                    'student group formation (groups of 3 to 4 members with exactly one group leader)'
                ),
                'đăng ký nhóm sinh viên (tối thiểu 2 người)',
                'đăng ký nhóm sinh viên (nhóm từ 3 đến 4 thành viên, có đúng một nhóm trưởng)'
            ),
            'min 2 members',
            '3 to 4 members with exactly one group leader'
        ),
        'tối thiểu 2 người',
        'từ 3 đến 4 thành viên với đúng một nhóm trưởng'
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE slug IN (
          'campus-portal-comprehensive-service-directory-en',
          'campus-portal-comprehensive-service-directory-vi'
      )
  AND (
      content LIKE '%min 2 members%'
      OR content LIKE '%tối thiểu 2 người%'
  );

-- 2. Retire the superseded PUBLISHED revision of those documents. The content
--    filter keeps a re-run a no-op and avoids archiving the corrected revision
--    that step 3 inserts.
UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (
      SELECT d.id
      FROM assistant.knowledge_document d
      WHERE d.slug IN (
          'campus-portal-comprehensive-service-directory-en',
          'campus-portal-comprehensive-service-directory-vi'
      )
  )
  AND (
      r.content LIKE '%min 2 members%'
      OR r.content LIKE '%tối thiểu 2 người%'
  );

-- 3. Publish the corrected revision. Mirrors V67: version = max + 1, id derived
--    from (document id, version) so the row is stable across re-runs, and the
--    insert is skipped when a PUBLISHED revision already carries this content.
WITH target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    WHERE d.active = TRUE
      AND d.visibility = 'PUBLIC'
      AND d.slug IN (
          'campus-portal-comprehensive-service-directory-en',
          'campus-portal-comprehensive-service-directory-vi'
      )
    GROUP BY d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content
)
INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority,
     created_by, reviewed_by, published_at, domain)
SELECT md5(t.id::text || '-revision-' || t.next_version::text)::uuid,
       t.id, t.next_version, 'PUBLISHED', t.locale, t.slug, t.title, t.content,
       t.source, t.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP, t.domain
FROM target t
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_document_revision existing
    WHERE existing.document_id = t.id
      AND existing.state = 'PUBLISHED'
      AND existing.content = t.content
      AND existing.title = t.title
);

-- 4. Build the next immutable corpus snapshot over the corrected documents.
WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'THESIS') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r
      ON r.document_id = d.id AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
), summary AS (
    -- Qualified deliberately. `pgcrypto` is not in `public` on every deployment
    -- (the local stack installs it into the `thesis` schema, where a bare
    -- `digest(...)` does not resolve because `thesis` is not on the search_path).
    -- `thesis.digest` is this project's own wrapper and is present in both
    -- environments, matching the convention used by V63 and V71.
    SELECT encode(thesis.digest(COALESCE(string_agg(
        concat_ws('|', source_id, domain, slug, locale, title, content, source,
                  priority::text, version::text), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(jsonb_agg(jsonb_build_object(
               'sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale
           ) ORDER BY source_id), '[]'::jsonb) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by,
     activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000072'::uuid,
       'thesis-group-size-v72', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'thesis-group-size-v72',
                          'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (
    SELECT 1 FROM assistant.knowledge_release
    WHERE id = '00000000-0000-0000-0000-000000000072'::uuid
);

-- 5. Project the corrected corpus into the new release's runtime documents.
INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content,
     source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000072'::uuid,
       d.id::text, r.id, r.version, COALESCE(r.domain, d.domain, 'THESIS'), d.slug,
       d.locale, d.title, d.content, d.source, d.priority, TRUE, 'PUBLIC',
       COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r
  ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND EXISTS (
      SELECT 1 FROM assistant.knowledge_release
      WHERE id = '00000000-0000-0000-0000-000000000072'::uuid
  )
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document existing
      WHERE existing.release_id = '00000000-0000-0000-0000-000000000072'::uuid
        AND existing.source_id = d.id::text
  );

-- 6. Switch the served release pointer. Guarded on the release existing so a
--    skipped step 4 leaves the previous release active rather than dangling.
INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
SELECT TRUE, '00000000-0000-0000-0000-000000000072'::uuid
WHERE EXISTS (
    SELECT 1 FROM assistant.knowledge_release
    WHERE id = '00000000-0000-0000-0000-000000000072'::uuid
)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id,
    updated_at = CURRENT_TIMESTAMP;

-- 7. Publish the correction notice. The historical announcement body is NOT
--    rewritten; this is a new, separately identifiable institutional statement
--    that names the superseded 1-3 rule in order to withdraw it.
--    Audience/publish fields satisfy the public feed predicate in
--    AnnouncementReadRepository.publicConditions (PUBLISHED + isGlobal TRUE +
--    publishAt <= now + expiresAt NULL + archivedAt NULL) and the role feed
--    predicate (STUDENT / LECTURER in targetRoles, empty targetYears so every
--    cohort matches, sectionId NULL so no enrollment join is required).
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount", "featuredOrder",
    "displayOrder", "isGlobal", "targetRoles", "targetYears", "publishAt", "expiresAt", "publishedBy",
    "semesterId", "semesterName", "createdAt", "updatedAt", "version"
)
SELECT 'announcement-thesis-group-size-correction-v72',
       'Đính chính quy định số lượng thành viên nhóm Khóa luận tốt nghiệp (KLTN): từ 3 đến 4 sinh viên',
       'dinh-chinh-quy-dinh-so-luong-thanh-vien-nhom-kltn',
       'Quy định chính thức: mỗi nhóm Khóa luận tốt nghiệp / Đồ án tốt nghiệp gồm từ 3 đến 4 sinh viên với đúng một nhóm trưởng. Thông báo này bãi bỏ mọi nội dung trước đây nêu giới hạn tối đa 03 sinh viên cho một nhóm.',
       '<p><strong>Thông báo đính chính.</strong> Phòng Đào tạo và Khoa Công nghệ Thông tin thông báo quy định chính thức về số lượng thành viên nhóm Khóa luận tốt nghiệp (KLTN) và Đồ án tốt nghiệp:</p>
<ul>
  <li><strong>Số lượng thành viên:</strong> mỗi nhóm gồm <strong>từ 3 đến 4 sinh viên</strong>.</li>
  <li><strong>Nhóm trưởng:</strong> mỗi nhóm có <strong>đúng một nhóm trưởng</strong>; nhóm trưởng là người khởi tạo đề tài, mời thành viên và nộp báo cáo thay cho nhóm.</li>
</ul>
<p>Quy định này <strong>thay thế và bãi bỏ</strong> mọi nội dung đã công bố trước đây nêu giới hạn <em>tối đa 03 sinh viên</em> hoặc <em>từ 1 đến tối đa 3 sinh viên</em> cho một nhóm đồ án. Cổng thông tin CampusUTE đã áp dụng đúng quy định này: một nhóm không thể có ít hơn 3 thành viên và không thể thêm thành viên thứ năm.</p>
<h3>Correction notice (English)</h3>
<p>Each capstone / graduation-thesis group consists of <strong>3 to 4 students</strong> with <strong>exactly one group leader</strong>. This notice supersedes any earlier statement that capped a group at 03 students, or that described a group as 1 to 3 students.</p>
<p>Mọi thắc mắc về quy định nhóm đề nghị liên hệ Văn phòng Khoa Công nghệ Thông tin hoặc Phòng Đào tạo.</p>',
       'HIGH',
       'PUBLISHED',
       (SELECT "id" FROM engagement."ArticleCategory" WHERE "code" = 'ACADEMIC_AFFAIRS'),
       '/images/news/thesis-defense.jpg',
       2,
       0,
       0,
       NULL,
       NULL,
       TRUE,
       ARRAY['STUDENT', 'LECTURER', 'ADMIN']::text[],
       ARRAY[]::integer[],
       CURRENT_TIMESTAMP,
       NULL,
       'Phòng Đào tạo & Khoa Công nghệ Thông tin',
       'semester-demo',
       'Học kỳ 1 năm học 2026-2027',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       1
WHERE NOT EXISTS (
    SELECT 1 FROM engagement."Announcement"
    WHERE "id" = 'announcement-thesis-group-size-correction-v72'
);

-- 8. Record the publication in the announcement governance trail (V15). The
--    row's own existence is the guard, so a later administrator edit is never
--    overwritten by a replay of this migration.
INSERT INTO engagement."AnnouncementAudit" (
    "id", "announcementId", "action", "actorId", "actorLabel", "reason", "version",
    "beforeState", "afterState", "createdAt"
)
SELECT 'audit-announcement-thesis-group-size-correction-v72',
       'announcement-thesis-group-size-correction-v72',
       'CREATED',
       'system-migration',
       'Hệ thống – Di trú dữ liệu (V72)',
       'Công bố thông báo đính chính quy định số lượng thành viên nhóm KLTN theo kết luận chính thức của Khoa: từ 3 đến 4 sinh viên, đúng một nhóm trưởng. Bản tin lịch sử không bị sửa đổi.',
       1,
       NULL,
       '{"status": "PUBLISHED", "isGlobal": true, "targetRoles": ["STUDENT", "LECTURER", "ADMIN"], "rule": "3-4 members, exactly one leader", "supersedes": "1-3 member statements"}',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM engagement."AnnouncementAudit"
    WHERE "id" = 'audit-announcement-thesis-group-size-correction-v72'
)
  AND EXISTS (
    SELECT 1 FROM engagement."Announcement"
    WHERE "id" = 'announcement-thesis-group-size-correction-v72'
  );

-- 9. Record the corpus reconciliation in the administrative audit trail (V60),
--    matching the V68 seed shape.
INSERT INTO campuscore_audit."AdminAudit" (
    "id", "actorId", "actorLabel", "action", "entityType", "entityId", "summary",
    "beforeState", "afterState", "createdAt"
) VALUES (
    'audit-thesis-group-size-correction-v72',
    'system-migration',
    'Hệ thống – Di trú dữ liệu (V72)',
    'RECONCILE_THESIS_GROUP_SIZE_DOCUMENTS',
    'KnowledgeRelease',
    '00000000-0000-0000-0000-000000000072',
    'Đối soát quy định số lượng thành viên nhóm đồ án trong tri thức trợ lý: sửa 02 tài liệu danh mục cổng thông tin còn ghi tối thiểu 2 thành viên thành từ 3 đến 4 thành viên, công bố release thesis-group-size-v72 và thông báo đính chính.',
    -- The superseded release is read from the new release's own
    -- previous_release_id instead of being hardcoded, so the audit trail
    -- records what was actually active on the database it ran against.
    '{"activeRelease": "'
        || COALESCE((
               SELECT prev.corpus_version
               FROM assistant.knowledge_release prev
               JOIN assistant.knowledge_release cur ON cur.previous_release_id = prev.id
               WHERE cur.id = '00000000-0000-0000-0000-000000000072'::uuid
           ), 'unknown')
        || '", "documents": {"campus-portal-comprehensive-service-directory-en": "min 2 members", "campus-portal-comprehensive-service-directory-vi": "tối thiểu 2 người"}}',
    '{"activeRelease": "thesis-group-size-v72", "rule": "3-4 members, exactly one leader", "correctedDocuments": 2, "correctionAnnouncement": "announcement-thesis-group-size-correction-v72"}',
    CURRENT_TIMESTAMP
)
ON CONFLICT ("id") DO NOTHING;
