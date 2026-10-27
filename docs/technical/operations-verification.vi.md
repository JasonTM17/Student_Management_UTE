# 5. Mobile, dữ liệu, vận hành và kiểm chứng

[English](operations-verification.en.md) · [Mục lục](README.vi.md) · [Ảnh/GIF hiện tại](../media-showcase.vi.md)

## T35. Expo mobile dùng chung API và tách preview khỏi xác thực

Ứng dụng mobile dùng Expo, React Native và TypeScript. `src/api/client.ts` quản lý một base URL, Bearer access token và refresh token trong bộ nhớ. Request 401 được thử refresh/retry một lần, loại trừ các endpoint login/refresh/logout để tránh vòng lặp. Refresh/logout gửi refresh token trong body theo hợp đồng Java; không giả định browser cookie tự có trên thiết bị.

Navigator tách `signedOut`, `preview`, `authenticated`, có điều hướng theo vai trò và kiểm transition trực tiếp. Đổi vai trò trong preview chỉ phục vụ xem thiết kế, không phải cấp quyền. Trạng thái bắt đổi mật khẩu phải được biểu diễn riêng thay vì vào business screen như tài khoản bình thường. Các cập nhật mobile còn đồng bộ định nghĩa enrollment active, lịch lớp, ngày giờ notification và kiểm profile.

Assistant native dùng JSON buffered; các màn hình chưa có hợp đồng backend phải nói rõ chưa khả dụng. Atlas/typecheck/unit test không chứng minh app chạy trên máy thật, push notification, offline sync hoặc lưu token bền vững an toàn. Những khả năng đó cần thiết kế/nghiệm thu riêng.

**Nguồn:** [mobile guide](../../mobile/README.md), [API client](../../mobile/src/api/client.ts), [navigator](../../mobile/src/navigation/MobileNavigator.tsx), [manifest](../../mobile/package.json).

## T36. Flyway và mô hình dữ liệu theo miền

PostgreSQL có các schema sở hữu dữ liệu theo miền. Flyway áp migration phiên bản hóa; history/checksum phát hiện sửa migration đã áp. Entity mapping phục vụ kiểm tra schema không thay thế migration. Môi trường mới cần đi qua cùng thứ tự migration trước khi app nhận traffic; thay đổi dữ liệu seed và thay đổi schema cần đọc như hai loại tác động khác nhau.

Trong Compose hiện tại, sidecar RAG bật Flyway; REST API tắt để không có hai bên cùng làm chủ boot migration theo cách ngầm định. Bước provision role assistant chạy trước để role runtime có thể đăng nhập đúng và giữ chính sách RLS. Database image chỉ đóng gói runtime PostgreSQL, không nhúng một bản schema cạnh tranh với Flyway.

Foreign key, unique/partial index, check/trigger và transaction cùng giữ invariant. H2 giúp test nhanh nhưng có nhánh SQL khác PostgreSQL; mapping, RLS, `ON CONFLICT` và race phải được kiểm trên PostgreSQL. Không sửa một migration đã phát hành để làm máy local hết lỗi; thêm migration hoặc dùng quy trình repair được phép và backup đúng đối tượng.

**Nguồn:** [migration policy](../../java-services/restful-api/src/main/resources/db/migration/README.md), [migration files](../../java-services/restful-api/src/main/resources/db/migration/), [database image](../../database/Dockerfile), [role provisioner](../../scripts/provision-assistant-runtime.mjs), [Compose](../../docker-compose.yml).

## T37. Storage provider và backup đủ cả metadata lẫn byte

Report storage có interface với các provider `local`, `database`, `supabase`. Local lưu byte ở volume; database dùng cột binary; Supabase là bucket riêng qua server. Client không nhận service-role key và không ghi trực tiếp bằng credential đặc quyền.

Local phù hợp bản chạy có volume bền vững nhưng database backup riêng không chứa file. Database provider giữ byte trong cùng dữ liệu backup nhưng tăng kích thước DB. Object storage tách tải byte khỏi DB, đổi lại phải backup/đối soát object và row tham chiếu. Provider/bucket được ghi theo row, vì vậy đổi biến môi trường không tự phục hồi file cũ.

Backup PostgreSQL cần lịch lưu giữ, kiểm file và diễn tập restore vào DB cô lập; local volume/bucket phải có đường phục hồi riêng. Thử restore có kiểm app đọc lại dữ liệu/file mới chứng minh khả năng phục hồi. Một file dump có mặt hoặc script exit 0 chưa chứng minh toàn hệ thống đã phục hồi.

**Nguồn:** [storage configuration](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/config/ThesisReportStorageConfiguration.java), [storage contract](../integrations/thesis-report-storage.md), [backup operations](../../ops/backup/README.md), [production runbook](../PRODUCTION_RUNBOOK.md).

## T38. Docker nhiều stage, cấu hình và resource boundary

Frontend build bằng Node trong stage riêng rồi chạy Next standalone bằng user không phải root; Java build bằng Maven/JDK rồi chạy trên JRE bằng user riêng. Base image có digest pin trong Dockerfile. Tách build/runtime giảm công cụ không cần trong runtime; digest pin giúp nhận diện input, không tự chứng minh image không có lỗ hổng.

Compose xác định health dependency, network nội bộ, giới hạn bộ nhớ và volume. Giá trị cổng host có thể ghi đè; cổng mặc định không phải mọi máy đều dùng. Web dùng `NEXT_PUBLIC_API_URL=/api/v1` ở build time và `JAVA_API_ORIGIN` ở server để giữ cùng origin. Trên Windows/Git Bash cần tránh MSYS biến `/api/v1` thành đường dẫn cài Git; dùng PowerShell env hoặc tắt conversion theo hướng dẫn repo.

Demo credential hiển thị trên client là opt-in build; tài khoản demo chỉ mở trong môi trường đánh giá được phép. Secret thật được cấp qua runtime/secret file, không đưa vào `NEXT_PUBLIC_*`, ảnh chụp hoặc committed log. Môi trường chụp tài liệu dùng project/image/port riêng, không tái tạo container của bộ local đang làm việc.

**Nguồn:** [frontend Dockerfile](../../frontend/Dockerfile), [API Dockerfile](../../java-services/restful-api/Dockerfile), [local Compose](../../docker-compose.yml), [production Compose](../../docker-compose.prod.yml), [secret provisioning](../../ops/secrets/README.md), [deployment guide](../deployment.md).

## T39. Liveness, readiness, timeout và khả năng suy giảm

Liveness trả lời tiến trình/đường đi API còn hoạt động; readiness kiểm phụ thuộc cần thiết trước khi nhận việc, có health key theo cấu hình. Proxy health phải trả lỗi khi backend không kết nối được; HTTP 200 ở frontend không chứng minh PostgreSQL hoặc assistant sẵn sàng.

Database readiness, connection/validation timeout và availability tracking giúp tránh chờ vô hạn khi DB bị ngắt hoặc ngủ. Keepalive/probe có ngân sách riêng; không được biến DB down thành danh sách rỗng thành công. Sidecar/provider lỗi có thể suy giảm trợ lý có căn cứ, nhưng lỗi kiểm authority không được suy giảm thành truy cập không xác thực.

Site appearance là cấu hình giao diện quản trị; validation body/CSRF lỗi phải từ chối, không lưu defaults thay cấu hình hợp lệ. Dashboard số liệu và theme/logo phục vụ trình bày; chúng không thay tín hiệu readiness hoặc bằng chứng deployment.

**Nguồn:** [health controller](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/system/controller/HealthController.java), [database readiness](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/system/service/DatabaseReadinessService.java), [frontend health](../../frontend/src/app/health/route.ts), [site appearance](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/site/), [health regression](../../frontend/tests/health-route.test.js).

## T40. Các tầng kiểm thử và giới hạn bằng chứng

| Tầng | Câu hỏi trả lời | Không tự chứng minh |
|---|---|---|
| Helper/unit | Công thức, parser, reducer, policy cho input xác định đúng không? | Transaction/engine thật, browser hoặc deployment |
| Contract/static | Route, thông điệp, dependency, boundary có đúng cấu trúc mong muốn? | Hành vi runtime toàn phần |
| Persistence/H2 | Service và SQL fixture cùng giữ luật không? | Mọi ngữ nghĩa khóa/RLS PostgreSQL |
| PostgreSQL IT | Lịch tranh chấp, khóa, constraint, RLS, migration thực có đúng? | Hosted DB hay provider bên ngoài |
| Playwright/browser | Người dùng thật thấy/gọi API theo flow nào? | Tất cả trường hợp cạnh tranh hoặc tải production |
| SMTP/provider/storage live | Tích hợp đã cấu hình cụ thể có hoạt động? | Mọi cấu hình, môi trường hoặc thời điểm khác |

Các lệnh gốc nằm trong manifest/workflow, tránh giữ một danh sách test count nhanh lỗi thời. Các điểm vào chính:

```powershell
npm test --prefix frontend
npm run typecheck --prefix frontend
npm test --prefix mobile
npm run typecheck --prefix mobile
mvn -f java-services/pom.xml verify
node scripts/check-doc-hygiene.mjs
git diff --check
```

PostgreSQL IT cần profile/biến môi trường và database tạm theo từng suite; đọc workflow và test trước khi chạy. Không trỏ suite mutation vào database production. Build Docker Java dùng skip-tests để tạo runtime chụp, nên build này không đại diện một lần `verify` đã pass.

**Nguồn:** [frontend scripts](../../frontend/package.json), [mobile scripts](../../mobile/package.json), [Maven profiles](../../java-services/restful-api/pom.xml), [CI workflow](../../.github/workflows/ci.yml), [E2E runner](../../scripts/run-course-e2e.mjs).

## T41. CI, container publication và danh tính release

Workflow CI có các lane Java, PostgreSQL và client theo hợp đồng trong file. Workflow tồn tại không chứng minh run hiện tại đã thành công. Publish image là quy trình riêng: nhận SHA main cụ thể, xác nhận review độc lập và kiểm CI trước build/push. Phân biệt rõ source SHA, image ID/digest, frontend build ID và deployment nhận image nào.

Rollback cần biết image cũ và tính tương thích schema, cùng backup/restore khi dữ liệu đã đổi. Đổi tag về image cũ không đảo migration hay khôi phục file. PR merged, CI pass, image published và production verified là các mốc khác nhau.

**Nguồn:** [CI](../../.github/workflows/ci.yml), [image publication gate](../../.github/workflows/publish-ghcr.yml), [release checklist](../RELEASE.md), [container guide](../docker-desktop-hub.md).

## T42. Tái tạo ảnh/GIF và kiểm tra hình ảnh trung thực

[Showcase](../media-showcase.vi.md) ghi rõ nguồn local, source/build/API identity và các giới hạn dữ liệu. PNG là ảnh render thật; GIF là chuỗi ảnh thật theo thứ tự màn hình, không phải video thao tác liên tục. Ảnh hiển thị nhãn CampusUTE; CampusCore là tên dự án/codebase.

Sau khi dựng bản demo riêng và bật tài khoản demo có chủ ý:

```powershell
$env:DOCS_CAPTURE_BASE_URL='http://127.0.0.1:3210'
$env:DOCS_CAPTURE_DEMO_PASSWORD='<mat-khau-demo-local>'
node scripts/capture-docs-media.mjs
node scripts/encode-docs-tours.mjs
```

Script capture dùng Playwright cài trong frontend; encoder cần FFmpeg trong PATH. Mỗi vai trò dùng browser context riêng, đăng nhập thật và kiểm `/auth/me`; chụp VI/EN sau hydration/font/data load. Manifest lưu route, HTTP status và lỗi API quan sát, không lưu token/password. Có thể giới hạn `DOCS_CAPTURE_ROLES` và `DOCS_CAPTURE_SHOTS` để chụp lại phần bị ảnh hưởng; `DOCS_CAPTURE_OUTPUT` đổi thư mục output.

Kiểm link/ảnh không đủ: phải decode GIF, kiểm thời lượng/kích thước, xem contact sheet và mở ảnh gốc để phát hiện loading, lỗi, chữ nhỏ hoặc ảnh khác role. Hạn chế seed phải được ghi lại; không sửa dữ liệu nghiệp vụ hoặc giả nội dung chỉ để ảnh đẹp. Tên asset mới tránh cache của link ảnh cũ trên nền tảng đọc repo.

**Nguồn:** [capture script](../../scripts/capture-docs-media.mjs), [encoder](../../scripts/encode-docs-tours.mjs), [media provenance](../assets/showcase-2026-10/provenance.json).
