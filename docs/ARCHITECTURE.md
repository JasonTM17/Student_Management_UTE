# Architecture

CampusCore hiện là một đồ án monolithic theo hướng course project:

- một Spring Boot REST API ở `java-services/restful-api`
- một Next.js web app ở `frontend`
- một Expo mobile app ở `mobile`
- một PostgreSQL duy nhất

## Runtime boundary

| Thành phần | Vai trò |
| --- | --- |
| `restful-api` | owner của `/api/v1`, auth, academic core, people, notifications, thesis core |
| `frontend` | web client |
| `mobile` | mobile client |
| `postgres` | lưu dữ liệu ứng dụng |

## Data layout

- Một database mới cho đồ án, không dùng lại schema legacy theo service.
- Flyway sở hữu migration.
- Seed data phục vụ demo local và test.
- Các module tài nguyên vẫn có thể được tách package bên trong Java API, nhưng chỉ có
  một runtime deployable.

## Public routing

- `/api/v1/*` -> `restful-api`
- `/api/docs/*` -> `restful-api`
- `/health` và Actuator probes -> `restful-api`
- không còn Nginx gateway trong runtime chuẩn

## Scope notes

- Trong phạm vi: auth, roles, students, lecturers, academic catalog, sections,
  enrollments, grades, announcements, notifications, thesis core.
- Ngoài phạm vi: finance, analytics nâng cao, support ticket, chatbot, realtime,
  Redis, RabbitMQ, MinIO, Kubernetes, observability stack.
