# CampusCore authorization model

This document makes the API's authorization rules explicit and reviewable. Before
it existed, the rules could only be recovered by reading every controller — which
led one audit to wrongly report "missing `@PreAuthorize`" on controllers that are
in fact correctly protected.

## The global backstop

`security/SecurityConfig.java` permits only a small public surface and then
requires authentication for everything else:

```java
.requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh").permitAll()
.requestMatchers("/api/v1/contract", "/api/v1/health/**", "/internal/rag/**").permitAll()
.requestMatchers("/error", "/actuator/health/**", "/api/docs/**", "/swagger-ui/**").permitAll()
.anyRequest().authenticated()
```

Consequence: **an endpoint without `@PreAuthorize` is still closed to anonymous
callers.** Absence of an annotation means "any authenticated user", never "public".

## The four tiers

| Tier | Rule | How it is expressed |
|------|------|---------------------|
| **0 — Public** | Reachable anonymously, by design | `permitAll()` in `SecurityConfig` |
| **1 — Role-gated** | Requires a specific role | `@PreAuthorize("hasAnyRole(...)")`, method- or class-level |
| **2 — Self-scoped** | Any authenticated user, but only over *their own* data | No annotation; the handler resolves `subject(jwt)` and never accepts an actor id from the request |
| **3 — Authenticated read** | Any authenticated user; shared reference data | No annotation, protected by the global backstop |
| **4 — Internal token** | Not a user route; service-to-service only | `X-Rag-Service-Token` verified in the handler |

### Tier 1 — role-gated

Mutations are always role-gated. `AdminCatalogMutationController` and
`LecturerMutationController` and `AdminUserMutationController` declare the
annotation **once at class level**, so per-method counts understate their coverage.

`AdminCatalogMutationController`, `LecturerMutationController`,
`AdminUserMutationController`, `AcademicMutationController`,
`RegistrationController`, `ThesisMutationController`, `ThesisCouncilController`,
`ThesisAssistantKnowledgeAdminController`, `AssistantKnowledgeRemoteAdminController`,
`AssistantKnowledgeSyncController`, `AnnouncementWriteController`, and the
non-`my` routes of `NotificationWriteController`.

### Tier 2 — self-scoped

These carry no annotation on purpose: the caller cannot influence whose data is
touched, because the subject is taken from the verified JWT.

- `AuthLoginController`: `GET me`, `PUT profile`, `POST change-password`, `POST logout`
- `IdentityController`: `GET /api/v1/me`
- `NotificationWriteController`: `PATCH my/{id}/read`, `PATCH my/read-all`, `DELETE my/{id}`
- `NotificationReadController`: `GET my`, `GET my/unread-count`
- `AnnouncementReadController`: `GET my`
- `AcademicReadController`: `GET me/curriculum` (additionally `hasRole('STUDENT')`)

### Tier 3 — authenticated reads

Catalogue and schedule reads are shared reference data that every signed-in role
needs (a student must be able to browse courses and read their timetable), so they
are protected by the global backstop rather than by a role check:
`AcademicReadController` (semester/course/curriculum/classroom/faculty/department/
academic-year reads), `AcademicScheduleReadController`, `AcademicAttendanceReadController`,
`AcademicEnrollmentReadController`, `AcademicSectionReadController`,
`AcademicConductController`, `PeopleReadController`, `ThesisTopicController`,
`ThesisRoundReadController`, `ThesisGroupReadController`, `ThesisAssistantController`,
plus the `ADMIN`-gated list routes of `NotificationReadController` and
`AnnouncementReadController`.

### Tier 4 — internal token

`ThesisAssistantInternalController` and `SupabaseKnowledgeInternalController` live
under `/internal/rag/**`, which is `permitAll()` at the filter level because they
are not user routes. Each handler independently verifies
`X-Rag-Service-Token` with a constant-time comparison before doing any work.

## Verifying this document

The classification is reproducible. From the repository root:

```sh
cd java-services/restful-api/src/main/java/io/campuscore/restfulapi
for f in $(find . -name "*Controller.java" | sort); do
  total=$(grep -cE '@(Get|Post|Put|Patch|Delete)Mapping' "$f")
  pre=$(grep -cE '@(org\.springframework\.security\.access\.prepost\.)?PreAuthorize' "$f")
  cls=$(grep -cE '^@(org\.springframework\.security\.access\.prepost\.)?PreAuthorize' "$f")
  printf "%2s ep | %2s preAuth | %s class-level | %s\n" "$total" "$pre" "$cls" "$f"
done
```

Two details matter when reading the output:

1. `AnnouncementWriteController` uses the **fully-qualified** annotation form
   (`@org.springframework.security.access.prepost.PreAuthorize`), which a short-form
   grep misses.
2. A non-zero **class-level** count means every method in that file is covered.

## Signing secrets

See `JwtSecretPolicy`. The API refuses to start with a secret published in this
repository when `SECURITY_JWT_REJECT_KNOWN_DEFAULTS=true` (set by
`docker-compose.prod.yml`); otherwise it logs a warning and continues, which keeps
local development and CI bootable. Detection is an exact-match denylist — not an
entropy heuristic, which provably cannot separate the shipped defaults from a
legitimate `openssl rand -hex 32` secret.
