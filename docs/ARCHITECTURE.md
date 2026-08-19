# Architecture

CampusCore hiện chạy như một stack microservices production-like với một
`core-api`, một `auth-service`, sáu domain service, một `frontend`, và một
`nginx gateway`. `java-services/thesis-service` là boundary migration pilot
opt-in, không nằm trong canonical nine-image runtime.

## Runtime boundary

| Thành phần             | Ownership chính                                                                                  |
| ---------------------- | ------------------------------------------------------------------------------------------------ |
| `core-api`             | audit logs, finance-context, compatibility shadow, public `/health`                             |
| `auth-service`         | auth, sessions, users, roles, permissions, JWT cookie + CSRF contract                            |
| `notification-service` | notification inbox, unread count, realtime `/notifications`                                      |
| `finance-service`      | invoices, payments, scholarships, billing events                                                 |
| `academic-service`     | faculties, departments, semesters, courses, sections, enrollments, grades, attendance, schedules |
| `engagement-service`   | announcements, support tickets                                                                   |
| `people-service`       | `students`, `lecturers`, shadow-sync outbound events                                             |
| `analytics-service`    | `/api/v1/analytics/*`, dashboards, lecturer reporting, finance-academic reporting                |
| `java-services/thesis-service` | opt-in `/api/v1/thesis/*` pilot trong `k8s/overlays/thesis-pilot`; chưa là public owner chung |
| `frontend`             | Next.js standalone web app                                                                       |
| `nginx`                | single public edge and path router                                                               |

## Runtime surfaces

CampusCore has several runtime surfaces that intentionally serve different
operator needs:

| Surface | Purpose | Public exposure |
| --- | --- | --- |
| Docker Compose | Local development, image smoke, observability smoke, and fast runtime inspection. | Local machine only. `nginx` may expose the app on localhost; Grafana/Prometheus stay operator-only. |
| Docker Desktop Kubernetes | Production-like local handoff for Kustomize manifests, namespace isolation, and edge contract validation. | Local machine only unless explicitly routed through Cloudflare Tunnel. |
| Cloudflare Tunnel | Demo/staging exposure for a local or private origin when there is no public LoadBalancer. | Only the configured public hostname is exposed; tunnel tokens stay private. |
| Cloudflare DNS/WAF/CDN + Ingress | Preferred production-style path when a real cloud Kubernetes ingress has a public address. | Browser traffic enters Cloudflare first, then reaches the ingress origin. |
| Observability stack | Prometheus metrics, Loki logs, Tempo traces, and Grafana dashboards for operators. | Internal/operator-only. `/metrics`, logs, and traces are not routed through the public edge. |

Use `node scripts/run-container-inventory.mjs` to read the local Docker/K8s
runtime state without deleting or restarting anything.

## Data layout

- PostgreSQL dùng một cluster chung nhưng tách schema theo service.
- `core-api` dùng `public`.
- `auth-service` dùng `auth`.
- `notification-service` dùng `notifications`.
- `finance-service` dùng `finance`.
- `academic-service` dùng `academic`.
- `engagement-service` dùng `engagement`.
- `people-service` dùng `people`.
- `analytics-service` hiện đọc từ `public` theo hướng low-risk.
- `java-services/thesis-service` dùng schema `thesis`; Flyway sở hữu migration
  của pilot, còn Node/Prisma không được `db push` vào schema này.
- Shared auth contract được gom về `packages/platform-auth` để giảm lặp lại cookie/JWT/CSRF logic giữa các service.
- Kustomize manifests cho canonical topology nằm tại `k8s/base`; overlay
  `k8s/overlays/thesis-pilot` là môi trường cô lập để kiểm tra Java boundary,
  không thay đổi public release manifests.

## Public routing

- `/api/v1/auth/*`, `/api/v1/users/*`, `/api/v1/roles/*`, `/api/v1/permissions/*` -> `auth-service`
- `/health` -> `core-api`
- `/api/v1/notifications/*`, `/socket.io/*` -> `notification-service`
- `/api/v1/finance/*` -> `finance-service`
- public academic routes -> `academic-service`
- `/api/v1/announcements/*`, `/api/v1/support-tickets/*` -> `engagement-service`
- `/api/v1/students/*`, `/api/v1/lecturers/*` -> `people-service`
- `/api/v1/analytics/*` -> `analytics-service`
- `/api/v1/thesis/*` -> `java-services/thesis-service` **chỉ trong
  `k8s/overlays/thesis-pilot`**; base/generic overlays chưa bật cutover này.

## Internal contracts

Canonical internal paths:

- `/api/v1/internal/academic-context/*`
- `/api/v1/internal/auth-context/*`
- `/api/v1/internal/finance-context/*`

Các path này không public qua `nginx` và yêu cầu `X-Service-Token`.

## Transitional constraints

- `auth-service` đã là public owner của auth và identity platform.
- `core-api` chỉ giữ shadow compatibility cho IAM trong một release chuyển tiếp.
- `people-service` dùng mô hình hybrid một release với shadow sync để giữ JWT claims `studentId` và `lecturerId`.
- `analytics-service` chưa có schema riêng để tránh refactor ownership sâu hơn trong cùng đợt hardening này.
- Java thesis pilot chưa có image public, differential contract report, canary,
  reconciliation/restore evidence hoặc rollback approval; không được gọi là
  production cutover.
