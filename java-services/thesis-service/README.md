# CampusCore Thesis Service

This is the first Spring Boot boundary in the NestJS-to-Java strangler
migration. It is intentionally isolated from the existing `academic-service`
until API, data ownership, authorization, and rollback gates pass.

## Runtime contract

- Java 21 and Spring Boot 3.4.
- Public prefix: `/api/v1`.
- Database schema: `thesis` (owned by the academic bounded context).
- Existing access-token cookie: `cc_access_token`.
- Cookie-authenticated writes require `X-CSRF-Token` to match `cc_csrf`.
- Flyway owns new migrations; Hibernate never creates or mutates schema.
- Actuator endpoints are operational surfaces and must stay off the public edge.

The service does not contain provider credentials or a fallback JWT secret.
Set `JWT_SECRET` and database credentials through the runtime secret store.
