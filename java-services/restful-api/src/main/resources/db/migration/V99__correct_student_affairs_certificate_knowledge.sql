-- Forward-only correction of exactly two unchanged V41 demonstration seeds
-- (student-affairs-services-and-certificates-*). Chained after V98's release
-- …098: the overlay projects the live …098 map, publishes …099 and activates
-- it. Renumbered from an earlier V97 draft so the Flyway version stays unique
-- next to V97__activate_course_prerequisites. Existing releases/checksums,
-- human edits and foreign authority remain immutable.
CREATE TEMP TABLE v99_seed_correction (
    id uuid PRIMARY KEY, slug text NOT NULL, locale text NOT NULL,
    old_hash text NOT NULL, content text NOT NULL
) ON COMMIT DROP;

INSERT INTO v99_seed_correction VALUES
('dcd11df4-2cb3-21fb-f9b5-638f12681c6b', 'student-affairs-services-and-certificates-vi', 'vi',
 'e1e32864b5236b0a9546e7ebaed28d32',
 '## Giấy xác nhận sinh viên và dịch vụ hỗ trợ

Mục Giấy xác nhận trên CampusUTE chỉ cho phép xem trước và in bản nháp chưa được cấp chính thức; thao tác đó không gửi yêu cầu, không theo dõi hồ sơ và không cấp giấy xác nhận. Cổng hiện chưa có màn hình nộp và theo dõi yêu cầu giấy xác nhận điện tử. Liên hệ Phòng Đào tạo hoặc bộ phận tiếp nhận có thẩm quyền để xác nhận loại giấy, thông tin cần chuẩn bị, kênh tiếp nhận chính thức, phí và thời gian xử lý. Không có kênh trực tuyến bên ngoài hay thời hạn trả kết quả được xác minh trong cổng này. Bảng điểm xem trên cổng không tự trở thành bản được chứng thực.

Khi mất hoặc hỏng thẻ sinh viên, hỏi bộ phận phát hành hoặc Công tác Sinh viên về thủ tục cấp lại và cách bảo vệ quyền sử dụng thẻ. CampusUTE chưa có màn hình gửi yêu cầu cấp lại thẻ; cần xác nhận nơi nộp, hồ sơ, phí và thời gian nhận từ đơn vị phụ trách.

Về BHYT và hỗ trợ sinh viên, hỏi đơn vị phụ trách để xác nhận thông tin bảo hiểm y tế áp dụng, giấy tờ, mức đóng và thời hạn từ thông báo chính thức. Với miễn giảm học phí, trợ cấp hoặc hỗ trợ khác, xác nhận điều kiện, hồ sơ và thời hạn của chương trình hiện hành với bộ phận có thẩm quyền. Chatbot không quyết định quyền lợi hoặc cam kết kết quả.'),
('51b2dfd2-722d-630c-fe0b-d6ba340e1aa9', 'student-affairs-services-and-certificates-en', 'en',
 'ad38f07cc0466971e34b548cd1e43051',
 '## Student certificates and support services

The CampusUTE Certificates page provides a preview and printable unissued draft. It does not submit an application, does not track an application and does not issue a certificate. The portal currently has no screen for submitting and tracking electronic certificate requests. Ask Academic Affairs or the authorized service desk to confirm the document, required information, official receiving channel, fees and processing time. No external online channel or result deadline has been verified in this portal. A transcript displayed in the portal is not automatically a certified document.

For a lost or damaged student card, ask the issuing office or Student Affairs about replacement and protecting access associated with the card. CampusUTE has no card-reissue request screen; confirm the receiving office, documents, fees and collection time with the responsible unit.

For health insurance and student support, ask the responsible office to confirm applicable insurance information, documents, contributions and deadlines from official notices. For tuition reductions, welfare or other support, confirm the current program conditions, documents and deadline with the authorized office. The assistant does not decide entitlement or guarantee an outcome.');

-- Use the publisher's document/revision-before-singleton lock order. Wait for
-- committed edits before checking eligibility; document locks fence new revisions.
SELECT d.id FROM assistant.knowledge_document d
JOIN v99_seed_correction c ON c.id=d.id
JOIN assistant.knowledge_document_revision r ON r.document_id=d.id AND r.version=1
ORDER BY d.id FOR UPDATE OF d, r;

SELECT active_release_id FROM assistant.knowledge_runtime_state
 WHERE singleton=TRUE FOR UPDATE;

CREATE TEMP TABLE v99_changed_document ON COMMIT DROP AS
SELECT d.id, c.slug, c.locale, c.old_hash, c.content,
       r.id AS old_revision_id, r.published_at AS old_published_at
FROM assistant.knowledge_document d
JOIN v99_seed_correction c ON c.id=d.id AND c.slug=d.slug AND c.locale=d.locale
JOIN assistant.knowledge_document_revision r ON r.document_id=d.id AND r.version=1
WHERE d.id=md5(c.slug || '-document')::uuid
  AND d.domain='POLICY' AND d.priority=25
  AND d.source='campuscore-academic-enrichment'
  AND d.active=TRUE AND d.visibility='PUBLIC'
  AND d.archived_at IS NULL AND d.archived_by IS NULL
  AND d.updated_at=d.created_at
  AND r.id=md5(d.id::text || '-revision-1')::uuid
  AND r.state='PUBLISHED' AND r.domain=d.domain AND r.priority=d.priority
  AND r.slug=d.slug AND r.locale=d.locale AND r.source=d.source
  AND r.created_by='system-seed' AND r.reviewed_by='system-seed'
  AND r.created_at=d.created_at AND r.published_at=r.created_at
  AND md5(d.title || E'\n' || d.content)=c.old_hash
  AND md5(r.title || E'\n' || r.content)=c.old_hash
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision later
                   WHERE later.document_id=d.id AND later.version>1);

UPDATE assistant.knowledge_document d
   SET content=c.content, source='campuscore-student-affairs-corrected-v99',
       updated_at=CURRENT_TIMESTAMP
  FROM v99_changed_document c WHERE c.id=d.id;

UPDATE assistant.knowledge_document_revision r SET state='ARCHIVED'
  FROM v99_changed_document c WHERE r.document_id=c.id AND r.version=1;

INSERT INTO assistant.knowledge_document_revision
    (id,document_id,version,state,domain,locale,slug,title,content,source,priority,
     created_by,reviewed_by,published_at)
SELECT md5(d.id::text || '-student-affairs-correction-v99')::uuid, d.id, 2,
       'PUBLISHED',d.domain,d.locale,d.slug,d.title,d.content,d.source,d.priority,
       'system-migration','system-migration',CURRENT_TIMESTAMP
FROM assistant.knowledge_document d JOIN v99_changed_document c ON c.id=d.id;

-- Preserve the entire immutable098 map. A changed base row is excluded even
-- if authoring was an eligible original; never publish latest unrelated edits.
CREATE TEMP TABLE v99_overlay ON COMMIT DROP AS
SELECT c.id
FROM assistant.knowledge_runtime_document p
JOIN v99_changed_document c ON c.id::text=p.source_id
WHERE p.release_id='00000000-0000-0000-0000-000000000098'::uuid
  AND p.revision_id=c.old_revision_id AND p.version=1
  AND p.domain='POLICY' AND p.priority=25 AND p.slug=c.slug AND p.locale=c.locale
  AND p.source='campuscore-academic-enrichment' AND p.active=TRUE AND p.visibility='PUBLIC'
  AND p.published_at=c.old_published_at
  AND md5(p.title || E'\n' || p.content)=c.old_hash;

CREATE TEMP TABLE v99_projected_runtime ON COMMIT DROP AS
SELECT p.source_id,COALESCE(r.id,p.revision_id) AS revision_id,
       COALESCE(r.version,p.version) AS version,COALESCE(r.domain,p.domain) AS domain,
       COALESCE(r.slug,p.slug) AS slug,COALESCE(r.locale,p.locale) AS locale,
       COALESCE(r.title,p.title) AS title,COALESCE(r.content,p.content) AS content,
       COALESCE(r.source,p.source) AS source,COALESCE(r.priority,p.priority) AS priority,
       p.active,p.visibility,COALESCE(r.published_at,p.published_at) AS published_at
FROM assistant.knowledge_runtime_state s
JOIN assistant.knowledge_release current_release ON current_release.id=s.active_release_id
JOIN assistant.knowledge_runtime_document p ON p.release_id=current_release.id
LEFT JOIN v99_overlay c ON c.id::text=p.source_id
LEFT JOIN assistant.knowledge_document_revision r ON r.document_id=c.id AND r.version=2 AND r.state='PUBLISHED'
WHERE s.singleton=TRUE AND current_release.status='PUBLISHED'
  AND current_release.source IN ('MANUAL','LEGACY')
  AND current_release.id='00000000-0000-0000-0000-000000000098'::uuid
  AND EXISTS (SELECT 1 FROM v99_overlay);

CREATE TEMP TABLE v99_summary ON COMMIT DROP AS
SELECT COUNT(*)::integer AS row_count,
       encode(thesis.digest(COALESCE(string_agg(concat_ws('|',source_id,
         COALESCE(revision_id::text,''),version::text,domain,slug,locale,title,content,
         source,priority::text,active::text,visibility),E'\n' ORDER BY source_id),''),'sha256'),'hex') AS corpus_hash,
       COALESCE(jsonb_agg(jsonb_build_object('sourceId',source_id,'domain',domain,
         'slug',slug,'locale',locale) ORDER BY source_id),'[]'::jsonb) AS documents
FROM v99_projected_runtime;

INSERT INTO assistant.knowledge_release
    (id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by,activated_at,previous_release_id)
SELECT '00000000-0000-0000-0000-000000000099'::uuid,'local-demo-v99',summary.corpus_hash,
       summary.row_count,'MANUAL','PUBLISHED',
       jsonb_build_object('schemaVersion',1,'corpusVersion','local-demo-v99','rowCount',summary.row_count,
         'sha256',summary.corpus_hash,'documents',summary.documents),
       'system-migration',CURRENT_TIMESTAMP,s.active_release_id
FROM v99_summary summary CROSS JOIN assistant.knowledge_runtime_state s
WHERE s.singleton=TRUE AND EXISTS (SELECT 1 FROM v99_projected_runtime);

INSERT INTO assistant.knowledge_runtime_document
    (release_id,source_id,revision_id,version,domain,slug,locale,title,content,source,priority,active,visibility,published_at)
SELECT '00000000-0000-0000-0000-000000000099'::uuid,source_id,revision_id,version,
       domain,slug,locale,title,content,source,priority,active,visibility,published_at
FROM v99_projected_runtime;

UPDATE assistant.knowledge_runtime_state s
   SET active_release_id=next_release.id,updated_at=CURRENT_TIMESTAMP
  FROM assistant.knowledge_release next_release
 WHERE s.singleton=TRUE AND next_release.id='00000000-0000-0000-0000-000000000099'::uuid
   AND s.active_release_id='00000000-0000-0000-0000-000000000098'::uuid
   AND EXISTS (SELECT 1 FROM v99_projected_runtime);
