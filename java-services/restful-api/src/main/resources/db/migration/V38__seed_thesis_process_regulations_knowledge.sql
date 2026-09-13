-- Thesis process regulations for the CampusCore assistant, transcribed from the
-- faculty requirement specification (đợt đăng ký theo giai đoạn, cơ cấu nhóm,
-- phân công hướng dẫn, hội đồng bảo vệ, cách chấm điểm, công bố kết quả).
-- Before this release the corpus only carried generic guidance, so rule
-- questions like "một nhóm tối đa mấy thành viên?" could not be answered.
-- Follows the V20/V23 governance shape: document + published revision +
-- immutable release projection + runtime state switch. Idempotent.

INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-thesis-regulations', seed.priority, 'THESIS'
FROM (VALUES
    ('thesis-round-phases-vi', 'vi', 'Các giai đoạn của đợt đăng ký đồ án',
     'Mỗi đợt đăng ký đề tài được tổ chức theo hai giai đoạn riêng biệt. Giai đoạn một: xây dựng và công bố danh sách đề tài. Giai đoạn hai: mở cho sinh viên đăng ký vào nhóm sinh viên đã thuyết minh và được công bố đề tài. Trưởng khoa tạo đợt đăng ký với các thông tin: tên đợt, loại đợt (học phần NCKH, tiểu luận chuyên ngành TLCN hoặc khóa luận tốt nghiệp KLTN), thời gian bắt đầu và kết thúc, thời gian giảng viên đăng ký bắt đầu và kết thúc cùng thời điểm sinh viên đăng ký, ngày giảng viên hướng dẫn chốt và ngày báo cáo hội đồng (áp dụng chi tiết khi đợt là TLCN hoặc KLTN).',
     15),
    ('thesis-round-phases-en', 'en', 'Phases of a thesis registration round',
     'Each topic registration round runs in two distinct phases. Phase one builds and publishes the topic list. Phase two lets student groups register for the topics that have been announced. The faculty head creates a round with: round name, type (research course NCKH, specialized essay TLCN, or graduation thesis KLTN), start and end dates, lecturer submission window aligned with the student window, supervisor confirmation date, and the council report date (detailed for TLCN and KLTN rounds).',
     15),
    ('thesis-group-members-rules-vi', 'vi', 'Quy định về nhóm và thành viên đồ án',
     'Sinh viên tham gia thực hiện đề tài theo hình thức nhóm. Mỗi nhóm có tối đa 03 thành viên, trong đó có một nhóm trưởng. Mỗi sinh viên chỉ được tham gia duy nhất một nhóm trong suốt quá trình thực hiện đề tài. Mỗi nhóm chỉ được đăng ký duy nhất một đề tài từ danh sách đề tài đã được công bố. Việc nộp báo cáo đề tài do nhóm trưởng thực hiện.',
     15),
    ('thesis-group-members-rules-en', 'en', 'Thesis group composition rules',
     'Students complete the topic as a group. Each group has at most 03 members, including one group leader. Each student may join only one group for the whole project period, and each group may register only one topic from the published list. Report submission is performed by the group leader.',
     15),
    ('thesis-supervision-rules-vi', 'vi', 'Quy định giảng viên hướng dẫn đồ án',
     'Mỗi đề tài thuộc một bộ môn cụ thể, có nội dung và phạm vi chỉ được hướng dẫn bởi ít nhất một giảng viên và tối đa hai giảng viên. Sau khi đăng ký và được chấp thuận, nhóm sinh viên thực hiện đề tài theo sự hướng dẫn của giảng viên hướng dẫn đã phân công.',
     18),
    ('thesis-supervision-rules-en', 'en', 'Thesis supervision rules',
     'Each topic belongs to a specific department and is supervised by at least one lecturer and at most two lecturers. After the registration is approved, the student group works under the guidance of the assigned supervisors.',
     18),
    ('thesis-defense-council-rules-vi', 'vi', 'Cơ cấu hội đồng bảo vệ đồ án',
     'Sau khi hoàn thành đề tài, nhóm sinh viên phải tham gia phản biện theo kế hoạch. Mỗi hội đồng gồm từ 03 đến 05 thành viên, bao gồm một chủ tịch hội đồng và một thư ký hội đồng. Chủ tịch hội đồng có trách nhiệm tổng hợp điểm của các thành viên trong hội đồng để đưa ra kết quả phản biện cuối cùng cho đề tài.',
     15),
    ('thesis-defense-council-rules-en', 'en', 'Defense council composition',
     'After completing the topic, the student group defends it according to the plan. Each council has from 03 to 05 members, including one chair and one secretary. The chair consolidates the members'' scores to produce the final defense result for the topic.',
     15),
    ('thesis-grading-rules-vi', 'vi', 'Cách tính điểm và quy tắc chấm đồ án',
     'Điểm cuối cùng của đề tài được tính bằng trung bình cộng các điểm thành phần. Một giảng viên có thể chấm nhiều đề tài khác nhau, nhưng không được chấm đề tài mà mình đang hướng dẫn. Sau khi quá trình phản biện và chấm điểm hoàn tất, kết quả đánh giá được công bố trên hệ thống.',
     15),
    ('thesis-grading-rules-en', 'en', 'Thesis grading rules',
     'The final topic score is the average of the component scores. A lecturer may grade several topics but must not grade a topic they supervise. Once the defense and grading are complete, the evaluation result is published on the system.',
     15),
    ('thesis-results-viewing-vi', 'vi', 'Xem kết quả đánh giá đồ án',
     'Sau khi quá trình phản biện và chấm điểm hoàn tất, kết quả đánh giá được công bố trên hệ thống. Sinh viên có thể xem điểm và kết quả đánh giá của đề tài mà mình tham gia thực hiện tại mục Đồ án tốt nghiệp trên cổng học vụ.',
     20),
    ('thesis-results-viewing-en', 'en', 'Viewing thesis evaluation results',
     'After the defense and grading process is complete, evaluation results are published on the system. Students can view the score and evaluation result of the topic they participated in under Thesis & Capstone in the student portal.',
     20)
) AS seed(slug, locale, title, content, priority)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-thesis-regulations'
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1);

WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'THESIS') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(digest(COALESCE(string_agg(concat_ws('|', source_id, domain, slug, locale, title, content, source, priority::text, version::text), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000038'::uuid, 'local-demo-v38', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v38', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000038'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000038'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000038'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000038'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
