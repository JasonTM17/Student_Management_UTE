-- V134__correct_unaccented_thesis_report_records.sql
-- Purpose:
--   Two submitted thesis reports in already-published rounds still carry
--   unaccented working titles ("Bao cao TLCN luong that", "Bao cao giai doan 1"),
--   an unaccented note, and one URL with a stray space. Restore proper
--   Vietnamese titles/notes that match each group's topic and repair the URL.
--   Rows are matched by group id AND the old title, so the migration is a no-op
--   on a database where the rows were already corrected or never existed.
--   The submitted file/link itself is left untouched: ck_report_artifact needs a
--   url or stored file, and no real document exists to point at.

UPDATE thesis.thesis_group_report
SET title = 'Báo cáo Tiểu luận Chuyên ngành - Trợ lý ảo tư vấn học vụ và định hướng nghề nghiệp',
    note = 'Bản báo cáo hoàn chỉnh sau khi xây dựng và kiểm thử trợ lý ảo tư vấn học vụ',
    updated_at = CURRENT_TIMESTAMP
WHERE group_id = 'eb781ba1-f652-4ee8-979a-17c424fd5e51'::uuid
  AND title = 'Bao cao TLCN luong that';

UPDATE thesis.thesis_group_report
SET title = 'Báo cáo giai đoạn 1 - Hệ thống thi và đánh giá kết quả học tập trực tuyến',
    note = 'Chủ đề: chấm điểm trực tuyến',
    url = 'https://drive.example.com/kltn-final.pdf',
    updated_at = CURRENT_TIMESTAMP
WHERE group_id = 'c082edd3-a07b-4bf9-b9f7-aa8b147dfd54'::uuid
  AND title = 'Bao cao giai doan 1'
  AND url = 'https://drive.example.com/k ltn-final.pdf';
