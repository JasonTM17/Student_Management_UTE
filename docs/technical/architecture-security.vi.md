# 1. Kiến trúc, frontend và bảo mật

[English](architecture-security.en.md) · [Mục lục kỹ thuật](README.vi.md) · [Tiếp: học vụ](academic.vi.md)

## T01. Modular monolith và sidecar có ranh giới rõ ràng

**Bài toán.** Học vụ, định danh và khóa luận cần giao dịch nhất quán; tách mỗi module thành microservice sẽ làm tăng độ phức tạp đồng bộ dữ liệu. CampusCore tổ chức một ứng dụng Java theo các miền `auth`, `academic`, `thesis`, `engagement`, `notification`, `mail`, `audit`, `site`, `people`, `system` và `security`.

**Luồng.** Web Next.js gọi cùng origin `/api/v1`; proxy chuyển tiếp tới Java REST API. Mobile gọi cùng hợp đồng Java qua Bearer token. Java sở hữu nghiệp vụ và PostgreSQL. Sidecar RAG chạy cùng JAR với chế độ riêng, qua `/internal/rag/assistant`, có token dịch vụ; trong Compose chỉ expose cổng nội bộ 4011. Các route trợ lý dành cho người dùng vẫn được bảo vệ ở API công khai.

**Đánh đổi.** Tách RAG cho phép đặt timeout và giới hạn tài nguyên riêng; không biến các miền học vụ thành các dịch vụ độc lập. API và sidecar còn dùng chung schema và artifact Java, vì vậy phải kiểm tra tương thích migration khi nâng phiên bản.

**Nguồn:** [Maven aggregator](../../java-services/pom.xml), [Compose](../../docker-compose.yml), [RAG gateway](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/client/RagAssistantGateway.java).

## T02. Controller–service–repository và giao dịch SQL tường minh

Controller nhận DTO và xác thực hình dạng đầu vào; service quyết định nghiệp vụ, phạm vi truy cập và đơn vị giao dịch; repository đọc/ghi dữ liệu. Phần lớn nghiệp vụ dùng `NamedParameterJdbcTemplate`, SQL tham số hóa và read DTO riêng, giúp nhìn rõ phép join, điều kiện cập nhật và thời điểm lấy khóa.

Repository/entity JPA vẫn tồn tại ở các khu vực được cho phép; có entity không có nghĩa mọi luồng đã chuyển sang Spring Data JPA. Flyway sở hữu DDL. Những câu `FOR UPDATE`, phép tăng sĩ số có điều kiện, RLS context và audit cùng giao dịch không nên được mô tả bằng ORM tự động flush nếu mã nguồn không làm như vậy.

**Lý do giữ SQL.** Với xử lý cạnh tranh, thứ tự đọc/khóa/ghi là một phần hợp đồng. Thay một câu SQL bằng ORM có thể đổi thời điểm ghi và khả năng bắt lỗi constraint. `ArchitectureGuardTest` là chủ sở hữu các giới hạn kiến trúc; kiểm tra guard trước khi đề xuất mở rộng repository JPA.

**Nguồn:** [AcademicMutationService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicMutationService.java), [thesis facade](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationService.java), [architecture guard](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/ArchitectureGuardTest.java).

## T03. Next.js BFF: cùng origin, bảo toàn cookie và SSE

**Đầu vào/đầu ra.** Route bắt toàn bộ `/api/v1/[...path]` nhận method, query, header và body; tạo URL dưới đúng prefix Java; trả status, header và body của upstream. `buildApiProxyUrl` từ chối các segment `.`/`..` trước khi bộ phân tích URL có thể chuẩn hóa chúng ra ngoài prefix.

Proxy thay các header IP do client tự khai bằng metadata runtime hoặc bucket proxy dùng chung; cấu hình token tin cậy giữa proxy/API là một ranh giới riêng. Không thể suy ra địa chỉ người dùng thật chỉ từ một `X-Forwarded-For` gửi từ trình duyệt.

Phản hồi JSON được buffer để cookie phiên được chuyển tiếp ổn định. Mỗi `Set-Cookie` phải được giữ riêng. SSE truyền trực tiếp `response.body`, đặt `no-cache, no-transform` và `X-Accel-Buffering: no` để delta không bị dồn đến cuối. HEAD và status 204/205/304 trả body rỗng đúng hợp đồng Fetch.

Chỉ một tập GET công khai được phép cache chia sẻ; dữ liệu riêng tư không được thêm cache công khai theo mặc định. Đánh đổi của buffer JSON là sử dụng bộ nhớ theo kích thước body, còn SSE cần duy trì kết nối và xử lý hủy.

**Nguồn:** [API proxy](../../frontend/src/app/api/v1/[...path]/route.ts), [URL builder](../../frontend/src/lib/proxy-url.ts), [proxy regression](../../frontend/tests/docs-route-contracts.test.js), [SSE proxy tests](../../frontend/tests/assistant-proxy-stream.test.js).

## T04. React Query, phân tách danh tính và trạng thái giao diện

React Query quản lý cache dữ liệu server, stale time và retry đọc. Trình duyệt dùng một QueryClient theo tab; SSR tạo client mới theo request. Một số query key không chứa user ID, nên `AuthContext` xóa cache khi danh tính thay đổi hoặc phiên kết thúc. Nếu chỉ đổi thông tin tài khoản ở header mà giữ cache, tài khoản đăng nhập tiếp theo có thể nhìn thấy dữ liệu tài khoản trước.

Mutations phải cập nhật hoặc invalidate tập query liên quan; điều kiện hiển thị nút chỉ hỗ trợ UX, backend vẫn kiểm quyền. Giao diện tách trạng thái loading, empty, forbidden và lỗi kết nối, tránh coi request lỗi là danh sách rỗng hợp lệ. Các biểu đồ và tổng số phải dùng dữ liệu API thay vì cộng chuỗi số hoặc giá trị mẫu.

**Nguồn:** [query client](../../frontend/src/lib/query.ts), [AuthContext](../../frontend/src/context/AuthContext.tsx), [identity cache regression](../../frontend/tests/query-cache-identity.test.js), [numeric totals](../../frontend/tests/dashboard-numeric-totals.test.js).

## T05. Song ngữ, đường dẫn theo locale và ngày dân sự

Web có route Việt/Anh với prefix `/vi` và `/en`, bộ thông điệp theo locale và helper thêm/bỏ prefix. Việc đổi ngôn ngữ giữ ý nghĩa route; không nên nối thủ công prefix ở từng nút. Vai trò và trạng thái từ API được ánh xạ sang câu chữ địa phương; mã lỗi máy ổn định được chuyển thành thông báo hiểu được.

Một ngày không có giờ (`YYYY-MM-DD`) là ngày dân sự, khác timestamp có offset. Chuyển mọi chuỗi ngày thành `Date` rồi format theo timezone có thể làm ngày bị lùi. Các trường ngày/giờ của khóa luận còn phải quy đổi giờ hiển thị và instant gửi API nhất quán; giới hạn deadline được kiểm lại ở backend, không lấy nhãn “còn 1 ngày” làm quyền ghi.

**Nguồn:** [i18n](../../frontend/src/i18n/), [thesis date conversion](../../frontend/src/lib/thesis-datetime.ts), [calendar date regression](../../frontend/tests/calendar-date-display.test.js), [24-hour input](../../frontend/src/components/ui/date-time-24-field.tsx), [locale parity](../../frontend/tests/locale-route-parity.test.js).

## T06. JWT và cookie: xác thực không đồng nghĩa quyền hiện tại

**Đăng nhập.** Java kiểm mật khẩu BCrypt và trạng thái tài khoản, cấp access/refresh token hoặc challenge 2FA. Web nhận cookie `cc_access_token`, `cc_refresh_token` HttpOnly và cookie CSRF đọc được bởi client. Mã hiện tại dùng `SameSite=Lax`; `Secure` phụ thuộc cấu hình môi trường. Mobile nhận token ở body. Tài khoản do đơn vị đào tạo cấp; màn hình đăng ký không tạo tài khoản công khai.

**Kiểm từng request.** Decoder yêu cầu token loại `access` và claim roles; refresh token không thể thay access token để vào business API. `AccountStateFilter` so khớp vai trò, quyền, profile và credential revision trong token với dữ liệu tài khoản hiện tại. Tài khoản bị khóa, quyền cũ hoặc mật khẩu đã thay phải dẫn tới từ chối/tái xác thực. Nếu không xác minh được authority vì DB lỗi, không được tự cho qua.

Chữ ký JWT chứng minh nguồn phát hành và tính toàn vẹn, không chứng minh quyền chưa bị thay đổi sau lúc cấp token. Cơ chế này cũng không hủy hồi tố request đã được cho phép trước khi giao dịch đổi quyền commit.

**Nguồn:** [SecurityConfig](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/config/SecurityConfig.java), [AccountStateFilter](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/filter/AccountStateFilter.java), [cookie owner](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/service/SessionCookieService.java), [authorization model](../security/authorization-model.md).

## T07. Refresh rotation, single-flight và đồng bộ nhiều tab

**Vấn đề.** Hai request cùng nhận 401 có thể cùng refresh; token sau ghi đè token trước. Một tab cũ còn có thể trả cookie sau thao tác đăng nhập hoặc đăng xuất của tab khác.

**Phía web.** `refreshSessionSingleFlight` dùng chung một promise; `withSessionTransport` tuần tự hóa các thao tác phiên. Web Locks phối hợp các tab khi có hỗ trợ; hàng đợi trong bộ nhớ là fallback cùng document. Thông điệp `BroadcastChannel`/storage mang revision và sự kiện thay đổi phiên, không mang token. AuthContext dùng generation để bỏ kết quả lỗi thời và xóa cache theo danh tính.

**Phía Java.** Refresh quay vòng hash trong row Session; token có `sid` gắn với row. Token của phiên đã xóa/đã hết hạn được phân biệt với token bị thay thế trong phiên còn sống. Cửa sổ chồng lấn hữu hạn hỗ trợ trường hợp phản hồi rotation bị mất; không phải bảo đảm giải quyết mọi thứ tự request song song. Các trường hợp replay ngoài hợp đồng vẫn kích hoạt xử lý reuse.

**Sửa đổi gần đây.** Khi kiểm tra phiên nhận 401, web thử refresh một lần trước khi kết luận phiên chết; cookie access lỗi thời không được khóa các endpoint login/refresh/recovery công khai. Đọc [mô hình phiên](../security/authorization-model.md) để biết chính xác cửa sổ, ngoại lệ và giới hạn thu hồi.

**Nguồn:** [API transport](../../frontend/src/lib/api.ts), [session sync](../../frontend/src/lib/session-sync.ts), [AuthLoginService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/service/AuthLoginService.java), [transport tests](../../frontend/tests/session-transport.test.js), [poll/refresh tests](../../frontend/tests/session-poll-refresh.test.js).

## T08. CSRF, 2FA và thay đổi credential

Cookie tự được browser gửi nên các thao tác ghi cần CSRF token ngoài cookie phiên; `CsrfCookieFilter` sở hữu quy tắc cho cookie/Bearer và các ngoại lệ. Không nên kết luận hệ thống không bảo vệ CSRF chỉ vì CSRF mặc định của Spring đã tắt: dự án dùng filter riêng.

2FA gửi OTP qua email sau bước kiểm mật khẩu, chưa cấp phiên ở bước đầu. Bật/tắt 2FA và thay mật khẩu phải thay credential revision và xử lý các phiên refresh theo chính sách. Challenge sai có giới hạn số lần thử; không được thay trạng thái bảo mật hoặc thu hồi phiên như thể xác nhận đã thành công.

Khôi phục mật khẩu trả lời chung để tránh lộ tài khoản tồn tại. SMTP là phụ thuộc vận hành: code và template có sẵn không bảo đảm thư đã tới hộp thư thật. Các kiểm thử interleaving PostgreSQL kiểm khóa User/Session/ResetToken và tranh chấp rotation ở đúng engine.

**Nguồn:** [CSRF filter](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/filter/CsrfCookieFilter.java), [TwoFactorService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/service/TwoFactorService.java), [PasswordResetService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/service/PasswordResetService.java), [credential/session race tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/auth/CredentialSessionLockOrderPostgresIT.java).

## T09. RBAC kết hợp phạm vi và quyền sở hữu

`STUDENT`, `LECTURER`, `ADMIN`, `SUPER_ADMIN`, `TRUONG_KHOA`, `TRUONG_BO_MON` là các authority có thể kết hợp. Route guard kiểm vai trò là lớp đầu; service phải kiểm sinh viên sở hữu bản ghi, giảng viên được phân công lớp, GVHD được gán đề tài, hoặc cán bộ quản lý đúng khoa/bộ môn.

Trưởng khoa không mặc nhiên là quản trị toàn trường. Quyền dựa trên profile giảng viên hiện tại, active và gắn đúng subject JWT. Một phân công chuyên môn cụ thể có thể cấp quyền đọc/chấm hợp lệ ngoài khoa mà không mở mọi thao tác quản trị. API có thể trả 404 để không tiết lộ tài nguyên ngoài phạm vi.

**Nguồn:** [authorization model](../security/authorization-model.md), [dean scope](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/scope/ThesisDeanScope.java), [department scope](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/scope/ThesisDepartmentScope.java), [governance details](thesis.vi.md).

## T10. Rate limit, validation và lỗi có cấu trúc

Đầu vào được giới hạn ở DTO, filter kích thước body và kiểm tra nghiệp vụ; validation phía web giúp phản hồi sớm nhưng không thay backend. Rate limiter phân nhóm hành vi như login hoặc trợ lý; client phải tôn trọng `Retry-After`, không vô hiệu limiter để có demo đẹp.

`DomainException` và error response giữ status, mã lỗi ổn định cùng details khi cần. Frontend ánh xạ code sang VI/EN và giữ thông tin để người dùng sửa thao tác, ví dụ hạn đăng ký đóng, thiếu quyền hoặc điểm chưa hợp lệ. Không đưa stack trace, token hoặc lỗi nhà cung cấp thô ra thông báo UI.

**Nguồn:** [rate-limit implementation](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/ratelimit/), [exception boundary](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/exception/), [localized errors](../../frontend/src/lib/campus-error.ts), [mail DTO bounds](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/dto/MailDtos.java).
