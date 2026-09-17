-- Standard term registration is capped at 28 credits. A student-specific
-- exception may reach 30 only through an application approved by Academic
-- Affairs. Keep this forward-only: never edit the historical V40 policy seed.

UPDATE academic."RegistrationRound"
SET "creditLimit" = 28,
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "creditLimit" > 28;

ALTER TABLE academic."RegistrationRound"
    DROP CONSTRAINT IF EXISTS registration_round_credit_limit_max;
ALTER TABLE academic."RegistrationRound"
    ADD CONSTRAINT registration_round_credit_limit_max
    CHECK ("creditLimit" BETWEEN 1 AND 28);

CREATE TABLE IF NOT EXISTS academic."CreditLimitApplication" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id") ON DELETE CASCADE,
    "semesterId" VARCHAR(120) NOT NULL REFERENCES academic."Semester" ("id") ON DELETE CASCADE,
    "roundId" VARCHAR(120) NOT NULL REFERENCES academic."RegistrationRound" ("id") ON DELETE CASCADE,
    "requestedLimit" INTEGER NOT NULL DEFAULT 30,
    "reason" VARCHAR(1000) NOT NULL,
    "status" VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    "reviewedBy" VARCHAR(120),
    "reviewedAt" TIMESTAMPTZ,
    "reviewerNote" VARCHAR(1000),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT credit_limit_application_requested_limit_valid CHECK ("requestedLimit" = 30),
    CONSTRAINT credit_limit_application_reason_valid CHECK (char_length(btrim("reason")) BETWEEN 20 AND 1000),
    CONSTRAINT credit_limit_application_status_valid CHECK ("status" IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT credit_limit_application_review_fields_valid CHECK (
        ("status" = 'PENDING' AND "reviewedBy" IS NULL AND "reviewedAt" IS NULL)
        OR ("status" IN ('APPROVED', 'REJECTED') AND "reviewedBy" IS NOT NULL AND "reviewedAt" IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS credit_limit_application_student_round_idx
    ON academic."CreditLimitApplication" ("studentId", "roundId", "status");

CREATE UNIQUE INDEX IF NOT EXISTS credit_limit_application_active_student_round_uq
    ON academic."CreditLimitApplication" ("studentId", "roundId")
    WHERE "status" IN ('PENDING', 'APPROVED');

WITH policy(slug, locale, content) AS (
    VALUES
        (
            'withdrawal-credit-limits-vi',
            'vi',
            'Quy chế về rút môn học và giới hạn khối lượng học tập: 1. Rút học phần: Sinh viên được nộp đơn rút môn học trong vòng 2 tuần đầu của học kỳ chính. Học phần được chấp thuận rút sẽ ghi nhận điểm chữ W (Withdrawn) trên bảng điểm và không tính vào điểm trung bình GPA. Rút môn sau thời hạn quy định sẽ không được xem xét và không được hoàn phí. 2. Giới hạn tín chỉ: Mỗi học kỳ chính, sinh viên được đăng ký tối thiểu 14 tín chỉ (trừ học kỳ cuối) và tối đa 28 tín chỉ. Chỉ khi có đơn được Phòng Đào tạo phê duyệt, sinh viên mới được đăng ký vượt mức chuẩn, nhưng tổng số không quá 30 tín chỉ. Học kỳ phụ (học kỳ hè) được đăng ký tối đa 8 đến 10 tín chỉ.'
        ),
        (
            'withdrawal-credit-limits-en',
            'en',
            'Rules on course withdrawal and credit workload: 1. Course withdrawal: Students may request course withdrawal during the first 2 weeks of a regular semester. Approved courses receive grade W and are excluded from GPA calculation. Late withdrawal requests are rejected with no refund. 2. Credit limits: Each regular semester requires a minimum of 14 credits (except final semester) and a standard maximum of 28 credits. Students may exceed the standard limit only with an application approved by Academic Affairs, capped at 30 credits. Summer terms allow a maximum of 8 to 10 credits.'
        )
), updated_documents AS (
    UPDATE assistant.knowledge_document d
    SET content = p.content
    FROM policy p
    WHERE d.slug = p.slug
      AND d.locale = p.locale
    RETURNING d.id
)
UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (SELECT id FROM updated_documents);

WITH policy(slug, locale, content) AS (
    VALUES
        (
            'withdrawal-credit-limits-vi',
            'vi',
            'Quy chế về rút môn học và giới hạn khối lượng học tập: 1. Rút học phần: Sinh viên được nộp đơn rút môn học trong vòng 2 tuần đầu của học kỳ chính. Học phần được chấp thuận rút sẽ ghi nhận điểm chữ W (Withdrawn) trên bảng điểm và không tính vào điểm trung bình GPA. Rút môn sau thời hạn quy định sẽ không được xem xét và không được hoàn phí. 2. Giới hạn tín chỉ: Mỗi học kỳ chính, sinh viên được đăng ký tối thiểu 14 tín chỉ (trừ học kỳ cuối) và tối đa 28 tín chỉ. Chỉ khi có đơn được Phòng Đào tạo phê duyệt, sinh viên mới được đăng ký vượt mức chuẩn, nhưng tổng số không quá 30 tín chỉ. Học kỳ phụ (học kỳ hè) được đăng ký tối đa 8 đến 10 tín chỉ.'
        ),
        (
            'withdrawal-credit-limits-en',
            'en',
            'Rules on course withdrawal and credit workload: 1. Course withdrawal: Students may request course withdrawal during the first 2 weeks of a regular semester. Approved courses receive grade W and are excluded from GPA calculation. Late withdrawal requests are rejected with no refund. 2. Credit limits: Each regular semester requires a minimum of 14 credits (except final semester) and a standard maximum of 28 credits. Students may exceed the standard limit only with an application approved by Academic Affairs, capped at 30 credits. Summer terms allow a maximum of 8 to 10 credits.'
        )
), target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, p.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    JOIN policy p ON p.slug = d.slug AND p.locale = d.locale
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    GROUP BY d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, p.content
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
);

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
SELECT '00000000-0000-0000-0000-000000000049'::uuid,
       'local-demo-v49', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object(
           'schemaVersion', 1,
           'corpusVersion', 'local-demo-v49',
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
    WHERE id = '00000000-0000-0000-0000-000000000049'::uuid
);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source,
     priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000049'::uuid,
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
      WHERE existing.release_id = '00000000-0000-0000-0000-000000000049'::uuid
        AND existing.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000049'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id,
    updated_at = CURRENT_TIMESTAMP;
