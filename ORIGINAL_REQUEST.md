# Original User Request

## 2026-09-14T08:44:30Z

Tối ưu hóa toàn diện hệ thống RAG LLM Chatbot (DeepSeek V4 Flash & Supabase / Render) cho cổng học vụ CampusCore, khắc phục triệt để lỗi chatbot trả lời không chuẩn, câu hỏi khó không gọi DeepSeek hoặc trả về ngữ cảnh rỗng.

Working directory: d:\Student_Management

## Requirements

### R1. Loại bỏ Client-Side Regex Interception (Frontend Bypass)
Xóa bỏ cơ chế chặn câu hỏi bằng Regex tĩnh tại frontend/src/lib/assistant-student-resolver.ts khiến các câu hỏi quy chế học vụ, học phí, học phần tiên quyết, học lại bị trả lời bằng template giả lập client (CampusCore Student Assistant), đưa 100% câu hỏi học vụ về API Gateway backend (/api/v1/assistant/chat và /stream).

### R2. Đại tu thuật toán truy hồi RAG Backend (Rank-based Weighted Retrieval)
Khắc phục thuật toán SQL tại ThesisAssistantKnowledgeRepository.java:
- Thay thế cơ chế ORDER BY p.priority ASC cứng nhắc (vốn luôn ưu tiên 5 tài liệu đồ án tốt nghiệp cũ chèn hết TOP_K) bằng cơ chế xếp hạng độ tương quan (Relevance Scoring / Term Frequency Matching / Tsvector Full-Text Search).
- Đảm bảo tài liệu khớp nhiều từ khóa chính xác nhất sẽ đứng đầu bảng kết quả ngữ cảnh nạp cho LLM.

### R3. Bổ sung toàn diện Corpus Tri thức Học vụ chuẩn (Academic Regulation Knowledge Base)
Bổ sung đầy đủ các văn bản quy chế học vụ thiết yếu vào schema assistant.knowledge_document và phát hành release mới qua Flyway / Supabase:
- Quy định phân biệt học phần tiên quyết (prerequisite) vs học phần học trước (prior course) vs học phần song hành (corequisite).
- Quy chế xử lý điểm F, học lại, học cải thiện điểm, cảnh báo học vụ các mức 1, 2 và điều kiện làm đồ án tốt nghiệp.
- Quy định về thời hạn, cách thức nộp học phí, gia hạn học phí và công nợ.
- Quy chế chuẩn đầu ra, điều kiện tốt nghiệp và học bổng khuyến khích học tập.

### R4. Tối ưu hóa DeepSeek Synthesis Router & Context Injection
Điều chỉnh AssistantDifficultyRouter.java và ThesisAssistantService.java:
- Tinh chỉnh ngưỡng nhận diện câu hỏi suy luận / phân tích đa chiều để kích hoạt deepseek-v4-flash.
- Format context truyền vào DeepSeek mạch lạc, trích dẫn chính xác và phản hồi tự nhiên, đầy đủ bằng tiếng Việt theo phong cách học vụ chuẩn mực.

## Acceptance Criteria

### Retrieval Accuracy & Precision
- [ ] Câu hỏi về "học phần tiên quyết", "học phí", "điểm F", "cảnh báo học vụ" trả về đúng văn bản quy chế tương ứng trong citations, không bị chèn bởi các tài liệu đồ án tốt nghiệp cũ.
- [ ] Thuật toán truy hồi backend sắp xếp theo mức độ phù hợp của từ khóa (relevance score) thay vì ưu tiên priority ASC cố định.

### LLM Synthesis & DeepSeek Trigger
- [ ] Câu hỏi phức tạp, so sánh hoặc suy luận đa bước được route thành công sang mô hình deepseek-v4-flash, trả về phân tích sâu sắc có căn cứ trích dẫn rõ ràng thay vì câu từ chối "Ngữ cảnh không đề cập".
- [ ] Không có query nào bị nuốt bởi regex cứng ở frontend client (CampusCore Student Assistant).

## 2026-09-19T15:35:52Z

Khắc phục triệt để 3 điểm dữ liệu mẫu và mock còn tồn tại trên giao diện CampusUTE:
1. Xây dựng Backend API thống kê cho Admin Analytics và đồng bộ biểu đồ `AdminAnalyticsCharts.tsx` với dữ liệu thực tế từ database.
2. Xóa bỏ 18 bài tin tức giả lập tĩnh trong `HomeNewsSection.tsx`, nạp 100% tin tức và bài viết thông báo thực tế từ API `/api/v1/announcements`.
3. Tái cấu trúc cơ chế Trợ lý AI học vụ (`assistant-student-resolver.ts`), chuyển tiếp toàn bộ câu hỏi quy chế học vụ lên Backend RAG AI Service (`/api/v1/thesis/assistant/stream` và `/chat`) thay vì trả lời bằng mẫu văn bản cứng phía client.

Working directory: d:/Student_Management
Integrity mode: demo

## Requirements

### R1. Backend Analytics API & Dynamic Admin Charts
- Thiết kế endpoint RESTful API tại backend Spring Boot: `GET /api/v1/admin/analytics/distribution` (hoặc tương đương) trả về số liệu tổng hợp thực tế theo Khoa (sinh viên, học phần, lớp học phần) và xu hướng học kỳ từ cơ sở dữ liệu PostgreSQL.
- Cập nhật `AdminAnalyticsCharts.tsx` để tiêu thụ dữ liệu thực tế từ API này, loại bỏ các mảng dữ liệu mẫu `DEPARTMENTS` và `SEMESTER_TRENDS` bị fix cứng; hiển thị trạng thái loading và empty state thanh lịch khi dữ liệu đang nạp.

### R2. Real Homepage Announcements & Elimination of Static Stories
- Xóa bỏ mảng `HOMEPAGE_FALLBACK_NEWS` gồm 18 bài tin giả lập trong `HomeNewsSection.tsx`.
- Đồng bộ trực tiếp với endpoint `/api/v1/announcements` để hiển thị các thông báo, sự kiện và tin tức học thuật thực tế được phát hành từ cơ sở dữ liệu.
- Giữ nguyên thiết kế Bento Grid và typography trang trọng, hỗ trợ phân loại theo danh mục thực tế và phân trang mượt mà.

### R3. Chatbot Backend-First RAG Delegation
- Tái cấu trúc bộ giải quyết câu hỏi trong `assistant-student-resolver.ts`: Ưu tiên tuyệt đối việc ủy quyền và gửi câu hỏi của sinh viên lên Backend RAG AI Service (`/api/v1/thesis/assistant/stream` và `/chat`).
- Loại bỏ các câu trả lời tĩnh mẫu cứng (hardcoded responses) cho các quy chế đào tạo, học phí, điểm số; đảm bảo mọi câu trả lời được sinh ra từ tri thức học thuật thực tế của hệ thống RAG backend kèm trích dẫn (citations) chính xác.

## Acceptance Criteria

### Data & Code Integrity
- [x] Mảng 18 tin giả lập `HOMEPAGE_FALLBACK_NEWS` đã được xóa bỏ hoàn toàn khỏi `HomeNewsSection.tsx`; giao diện trang chủ nạp dữ liệu động từ API `/api/v1/announcements`.
- [x] Các mảng dữ liệu tĩnh `DEPARTMENTS` và `SEMESTER_TRENDS` trong `AdminAnalyticsCharts.tsx` đã được thay thế bằng dữ liệu gọi từ API backend.
- [x] Chatbot không còn sử dụng câu trả lời văn bản mẫu cứng phía client cho các câu hỏi nghiệp vụ đào tạo, mà gọi trực tiếp đến Backend RAG Service.
- [x] Endpoint backend mới được bổ sung đầy đủ Swagger OpenAPI 3.0 annotations (`@Tag`, `@Operation`, `@ApiResponse`).

### Automated Verification
- [x] Backend: `mvn test` vượt qua 100% (tất cả các unit và integration test cases xanh).
- [x] Frontend: `npm run lint` đạt 0 warnings, 0 errors (`--max-warnings=0`).
- [x] Frontend: `npm run typecheck` đạt 0 TypeScript errors.
- [x] Frontend: `npm test` vượt qua 100% tất cả các test suites.
