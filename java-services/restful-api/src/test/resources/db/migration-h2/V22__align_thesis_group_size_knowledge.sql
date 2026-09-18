-- H2 parity for the live thesis guidance. H2 keeps the compact knowledge schema
-- and does not model PostgreSQL's immutable revision/release projection.
UPDATE assistant.knowledge_document
SET content = CASE slug
    WHEN 'vi-group-registration' THEN
        'Nhóm luận văn phải có từ 3 đến 4 sinh viên, gồm đúng một nhóm trưởng đại diện. Mỗi sinh viên chỉ tham gia một nhóm và mỗi nhóm đăng ký đúng một đề tài đã công bố.'
    WHEN 'en-group-registration' THEN
        'A thesis group must have 3 to 4 students with exactly one group leader. Each student may join only one group and each group registers exactly one published topic.'
    ELSE content
END
WHERE slug IN ('vi-group-registration', 'en-group-registration');
