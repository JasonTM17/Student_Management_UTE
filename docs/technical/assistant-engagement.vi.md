# 4. AI và truyền thông học vụ

[English](assistant-engagement.en.md) · [Mục lục](README.vi.md) · [Tiếp: vận hành](operations-verification.vi.md)

## T27. RAG lexical có căn cứ và định tuyến độ khó

**Luồng.** Nhận câu hỏi/locale/scope → xác thực và kiểm đầu vào → chọn corpus đã xuất bản → tách/chuẩn hóa từ → truy hồi có xếp hạng → tạo ngữ cảnh giới hạn → trả câu trả lời cùng citation. Repository cộng điểm khi từ xuất hiện trong tiêu đề/nội dung và khi khớp nguyên từ: trọng số tương ứng 3/1/4/2. Đây là weighted lexical ranking do dự án định nghĩa, không phải BM25 chuẩn, embedding hoặc vector similarity.

`topK` giới hạn tập truy hồi; số tài liệu đưa vào prompt được giới hạn riêng. Fast path yêu cầu đủ độ tin cậy lexical; không gọi model chỉ vì UI có tên “AI”. `AssistantDifficultyRouter` dùng quy tắc độ dài, số từ, marker so sánh/giải thích và số miền tài liệu để chọn khi cần synthesis. Các câu hỏi hạn mức tín chỉ có câu trả lời curated quyết định để hạn chế sai lệch diễn giải.

DeepSeek là provider tùy chọn, model/base URL/timeout theo cấu hình. Thiếu cấu hình, timeout hoặc provider lỗi có đường fallback có căn cứ kèm `degraded`/`reasonCode`, thay vì giả vờ model đã trả lời. Không có nguồn phù hợp không được tự tạo citation. Kỹ thuật này ưu tiên khả năng giải thích và chạy local; chất lượng vẫn phụ thuộc corpus, ngôn ngữ và câu hỏi.

**Nguồn:** [knowledge ranking](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/repository/ThesisAssistantKnowledgeRepository.java), [assistant orchestration](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/ThesisAssistantService.java), [difficulty router](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantDifficultyRouter.java), [bounded properties](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/properties/AssistantProperties.java), [provider client](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/client/DeepSeekClient.java).

## T28. Corpus chuyên sâu và ngữ cảnh cá nhân có scope

Trợ lý học vụ và workspace chuyên sâu có mục đích/corpus khác nhau. `scope` được gắn với hội thoại/lượt hỏi; đổi corpus không được âm thầm dùng lịch sử của scope khác. Workspace chuyên sâu phục vụ tri thức kỹ thuật/đồ án; câu hỏi quy chế nên đi qua corpus học vụ phù hợp. Các ví dụ trong showcase dùng câu hỏi đúng miền.

`AssistantPersonalContextAdvisor` lấy lịch, điểm, tín chỉ, đăng ký, điểm danh hoặc nhiệm vụ hướng dẫn qua các service nội bộ đã biết danh tính. Dữ liệu cá nhân được suy ra từ caller đã xác thực, không từ một MSSV tùy ý trong prompt. Câu hỏi về người khác hoặc không xác định được chủ thể phải giữ boundary. Context cá nhân và tri thức công khai là hai nguồn khác nhau; không đưa hồ sơ sinh viên vào corpus công khai để “tăng chất lượng RAG”.

Fallback khi DB/provider lỗi phải nói đúng phần dữ liệu không xác minh được. Context rỗng không có nghĩa điểm bằng 0, không có lớp hay đã hoàn thành toàn bộ tín chỉ.

**Nguồn:** [personal context owner](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantPersonalContextAdvisor.java), [specialized workspace](../../frontend/src/app/dashboard/assistant-specialized/page.tsx), [scope regression](../../frontend/tests/assistant-scope-guard.test.js), [knowledge domains](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantKnowledgeDomains.java).

## T29. Streaming SSE, heartbeat và hủy lượt hỏi

Web dùng SSE để nhận metadata, delta, citation, replacement, heartbeat và trạng thái kết thúc theo hợp đồng stream. Reducer/UI phải chấp nhận dữ liệu đến từng phần, ghép đúng message, đổi nội dung khi có event replace và không giữ spinner sau terminal event. Heartbeat cho biết kết nối còn sống, không phải một phần câu trả lời.

Nút dừng/hủy phải truyền ý định đến transport và registry ở backend; đóng tab hoặc abort request không luôn chứng minh provider đã dừng và không bị tính quota. Executor/lease tách công việc đang chạy khỏi một kết nối browser ngắn hạn. Phản hồi lỗi sau khi stream bắt đầu phải theo schema event, vì không còn tự do đổi status của HTTP response đã gửi.

Proxy phải truyền stream trước EOF. Native mobile dùng JSON buffered để cùng phục vụ assistant mà không nhận mình đã có SSE. Kiểm UI đọc được, focus, scroll và khóa composer là một phần nghiệm thu, khác với chỉ kiểm endpoint trả 200.

**Nguồn:** [web stream hook](../../frontend/src/components/assistant/useAssistantStream.ts), [stream DTOs](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/dto/), [stream executor](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantStreamExecutor.java), [cancellation registry](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantCancellationRegistry.java), [stream lifecycle tests](../../frontend/tests/assistant-stream-lifecycle.test.js).

## T30. Quota và turn ledger: CAS, lease và idempotency

**Bài toán.** Nhiều tab gửi cùng ý định có thể gọi provider hai lần; tiến trình chết giữa giữ quota và lưu câu trả lời có thể để lượt hỏi treo. Bộ đếm trong RAM không đủ để phối hợp instance hoặc phục hồi.

Turn ledger gắn owner, client request ID, request hash, conversation, state và lease generation. Luồng thông thường đi qua `RESERVED → SNAPSHOT_READY → DISPATCHED → COMPLETED`; các đường lỗi/hủy/lease hết hạn có trạng thái riêng trong repository. Thao tác cập nhật có điều kiện trên state, owner, generation và deadline là CAS: chỉ worker còn quyền sở hữu hiện hành được chốt kết quả. Worker cũ không được commit khi lease đã chuyển thế hệ.

Quota user/global được giữ/tăng nguyên tử ở giao dịch ngắn quanh dispatch; registry ghi dấu dispatch để điều phối idempotency. Bucket ngày theo `Asia/Ho_Chi_Minh`, không theo timezone tùy ý của browser hoặc UTC mặc định. Giới hạn clamp hiện tối đa 20/user/ngày và 200 toàn cục/ngày theo `AssistantProperties`; provider dispatch, fast path và một số nhánh standalone có ngữ nghĩa tính/refund khác nhau. Không nên mô tả thành “mọi lần bấm gửi đều trừ đúng một quota”.

Recovery phân biệt chưa dispatch với đã dispatch/kết quả không rõ. Đường refund phải theo trạng thái và ngày charge, không hoàn tiền mù khi client hủy. Ledger hỗ trợ giữ dấu vết và hạn chế duplicate trong hệ thống; không bảo đảm exactly-once thực thi ở nhà cung cấp bên ngoài nếu provider không cung cấp cùng hợp đồng.

**Nguồn:** [turn ledger repository](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/repository/ThesisAssistantTurnRepository.java), [assistant service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/ThesisAssistantService.java), [timezone owner](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantTimezone.java), [retention job](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/job/ThesisAssistantRetentionJob.java).

## T31. RLS và kiểm duyệt tri thức hai người

Assistant dùng DataSource/transaction manager riêng, role runtime giới hạn và PostgreSQL Row-Level Security. Transaction runner bind context danh tính đã tin cậy bằng cấu hình cục bộ giao dịch; quyền không được lấy từ một header tùy ý. Context phải không rò sang request kế tiếp khi connection quay lại pool. Có boundary khác nhau cho owner access và admin governance.

Một probe/cache operation có thể dùng transaction riêng (`REQUIRES_NEW`) để lỗi câu SQL ở PostgreSQL không làm giao dịch caller bị poisoned rồi phá đường fallback. Đây là ngoại lệ cô lập lỗi có chủ đích, khác việc tùy tiện tách transaction cho toàn bộ nghiệp vụ.

Knowledge revision đi `DRAFT → PENDING_REVIEW → PUBLISHED`; người tạo không tự xuất bản revision của mình. Backend kiểm author/reviewer và privacy policy trước promotion release/snapshot. Bản cũ được lưu theo cơ chế version/archive; thay draft không có nghĩa dữ liệu runtime đã đổi. Admin UI minh họa quy trình; khả năng RLS cần kiểm với role runtime thật, không chỉ kết nối superuser hoặc H2.

**Nguồn:** [RLS runner](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/rls/AssistantRlsTransactionRunner.java), [runtime verifier](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/rls/AssistantRlsRuntimeVerifier.java), [knowledge admin service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantKnowledgeAdminService.java), [release promoter](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/SqlKnowledgeReleasePromoter.java), [CI's RLS lane](../../.github/workflows/ci.yml).

## T32. Prompt/output guards và quyền riêng tư

Input guard chuẩn hóa/kiểm các mẫu yêu cầu vượt quyền, thay chỉ dẫn và nội dung không hợp lệ bằng Việt/Anh. Output guard kiểm phần trả về trước khi hiển thị/lưu, cùng audit theo chính sách. Giới hạn message/context/output và timeout tạo ngân sách tài nguyên hữu hạn.

Guard dựa trên mẫu/quy tắc là một lớp phòng thủ, không phải bằng chứng chặn mọi prompt injection. Quyền truy cập dữ liệu phải được service/RLS thực thi độc lập; provider không được trở thành authority cấp quyền. Khi không thể kiểm danh tính hoặc scope, fallback không được dùng để vượt auth. Không ghi token, key, prompt nhạy cảm hoặc lỗi provider thô vào bằng chứng công khai.

**Nguồn:** [input guard](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/guard/AssistantInputGuard.java), [output guard](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/guard/AssistantOutputGuard.java), [guard audit regression](../../frontend/tests/assistant-output-guard-audit.test.js), [assistant auth regression](../../frontend/tests/assistant-auth-gate.test.js).

## T33. TinyMCE, bài viết, thông báo và audit

TinyMCE được self-host trong web; nội dung rich HTML cần giữ cấu trúc bảng/ảnh/định dạng nhưng loại script, event handler, URL nguy hiểm và kiểu CSS không an toàn. Backend Jsoup sanitizer là ranh giới ghi; frontend sanitizer là lớp render bổ sung. Không cho phép `data:` như một navigation scheme chỉ vì ảnh inline có thể dùng data-image được kiểm soát. Editor preview và reader cần round-trip cùng chính sách để không hứa định dạng rồi mất nội dung sau lưu.

Announcement có audience theo vai trò, năm, khoa/bộ môn và lớp; giảng viên chỉ xuất bản trong phạm vi lớp được phép. Publish/expire/archive, version và audit phục vụ thay đổi có kiểm soát. Thông báo tới người nhận được phát sau commit và có job cho lịch publish tương lai; không gửi trước commit khiến sinh viên nhận một bài chưa tồn tại.

Notification là hộp thư sự kiện, có read/unread và deep link. Link cần kiểm đường dẫn/locale, tránh biến dữ liệu thông báo thành open redirect. Audit lưu actor, đối tượng, hành động và before/after phù hợp; việc xem nhật ký cũng cần quyền. Audit chứng minh hệ thống ghi nhận gì, không thay backup hoặc bảo đảm email đã giao.

**Nguồn:** [announcement writes](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementWriteService.java), [HTML sanitizer](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementHtmlSanitizer.java), [scheduled notifier](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/job/ScheduledAnnouncementNotifyJob.java), [notification link policy](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/notification/service/NotificationLinkPolicy.java), [audit recorder](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/audit/service/AdminAuditRecorder.java), [editor tests](../../frontend/tests/editor-integrity.test.js).

## T34. Email học vụ: scope người nhận, validation và SMTP

Workspace email có thông báo học vụ, xác nhận đăng ký và cảnh báo điểm. UI tính tổng tín chỉ/GPA theo dữ liệu nhập, kiểm số hữu hạn, số nguyên, miền điểm và giới hạn dòng/chuỗi trước request; nút gửi vẫn cho chạy validation để chỉ ra trường cần sửa. Java dùng DTO validation và filter body-size làm authority cuối.

Giảng viên chỉ gửi tới sinh viên thuộc lớp mình phụ trách; server tra lại người nhận và tên/MSSV theo dữ liệu quản lý, không tin danh tính tùy ý trong payload. Admin cũng phải chọn tài khoản hiện có/active theo hợp đồng; endpoint kiểm thử gửi về email tài khoản đang đăng nhập thay địa chỉ tự khai. Có quyền gửi email không đồng nghĩa relay tùy ý ra mọi địa chỉ internet.

Thymeleaf tạo HTML và plain-text alternative UTF-8; preview render không chứng minh SMTP đã gửi. `MAIL_ENABLED=false` khiến service bỏ qua dispatch; ngay cả response thành công của controller cũng không chứng minh thư tới hộp thư. Với môi trường thật, xác nhận cấu hình, kết quả SMTP và quan sát nhận thư riêng. Luồng async/post-commit giữ lỗi thư độc lập với giao dịch điểm/khóa luận đã hoàn tất.

**Nguồn:** [mail compose helpers](../../frontend/src/lib/mail-compose.ts), [workspace](../../frontend/src/app/dashboard/lecturer/mail/page.tsx), [controller and scope checks](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/controller/MailController.java), [recipient repository](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/repository/MailRecipientScopeRepository.java), [DTO limits](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/dto/MailDtos.java), [email implementation](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/service/EmailServiceImpl.java), [compose tests](../../frontend/tests/mail-compose.test.js).
