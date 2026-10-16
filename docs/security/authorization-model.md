# CampusCore authorization model

Authorization combines route access, current account authority, and ownership of
the requested data. A controller annotation alone is not an authorization audit:
class-level annotations, the filter chain, and service/repository scope all matter.

## Executable owners

- [SecurityConfig](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/SecurityConfig.java)
  owns the public route and HTTP-method allowlist, authenticated fallback, token
  validation, and role conversion. Use it instead of maintaining a second route
  list here. Accounts are office-issued; there is no public self-registration.
- [AccountStateFilter](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/AccountStateFilter.java)
  and [AuthUserRepository.findAccountAuthorization](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/repository/AuthUserRepository.java)
  own the current-account gate. A valid signature proves who issued a token; it
  does not make an earlier role or academic profile authoritative forever.
- [AuthLoginService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/service/AuthLoginService.java)
  owns refresh-session validation and reissuance. Refresh and logout have their
  own session rules rather than inheriting business-route authority.

## Current authority and re-authentication

Business access depends on an existing active account and a token matching its
current roles, permissions, student/lecturer identity, and student year. A stale
snapshot requires re-authentication; the request must not silently acquire newer
privileges. Database unavailability fails closed because authority cannot be
established. This applies to authenticated AI routes too: provider fallback is
not permission to bypass account verification.

Office-issued temporary passwords must be rotated before business use. The
rotation exception exists only to let the user inspect the account and change
the password; it does not exempt those requests from current-account checks.
The exact exceptions and error contract belong to `AccountStateFilter`.

Role/profile snapshot equality is a current-state policy, not a permanent
revocation history. Restoring exactly those authorities can make their snapshot
match again. Credential replacement has a separate durable boundary: password
changes must require fresh authentication, even when roles and profiles stay the
same. See the credential revision in `AuthUserRepository`, `AuthTokenService`
and `AccountStateFilter`, and the executable cases in
[AuthLoginPersistenceTest](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/auth/AuthLoginPersistenceTest.java),
[PasswordResetPersistenceTest](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/auth/PasswordResetPersistenceTest.java)
and [AdminUserMutationPersistenceTest](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/auth/AdminUserMutationPersistenceTest.java).

The revision avoids JWT timestamp precision and clock-skew ambiguity; the
[PostgreSQL schedules](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/auth/AuthCredentialRevocationPostgresIT.java)
own the stale-authentication interleavings. A request already authorized before
replacement commits may finish. This boundary does not promise cancellation of
in-flight work, recovery of past side effects, or access-token blacklisting on
single-session logout.

Credential verification and replacement serialize on the account's User row
lock, so login, refresh, reset issuance/redemption, and password writes for the
same account queue rather than interleave. Row-lock contention is per account,
but waiting transactions occupy shared database connections and request
capacity, so sustained contention can also delay other accounts. The
application does not configure a credential-operation `lock_timeout`;
the connection-pool acquisition timeout is a separate limit. A request
carrying a stale credential fails closed even on `permitAll` reads — the
frontend treats that as session death and routes to sign-in rather than
serving anonymous content silently.

Apply the forward
[V121 migration](../../java-services/restful-api/src/main/resources/db/migration/V121__revoke_access_on_credential_rotation.sql)
with enforcing backend code. Do not serve a mixture of enforcing and older
backends while claiming immediate credential revocation. Rolling back the code
can retain the column but reopens the old access window. Revision-zero legacy
compatibility preserves unaffected sessions during rollout; it cannot
retroactively identify password changes made before the revision existed.

## Public, shared, and private data

- **Public content** is an explicit product surface, not any route lacking an
  annotation. Its allowlist belongs to `SecurityConfig`; internal service-token
  routes in that allowlist are not anonymous user features.
- **Shared reference data** supports cross-role navigation, such as course and
  semester lookup. [AcademicReadController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/web/AcademicReadController.java)
  owns those reads and distinguishes the student's personal curriculum route.
- **Private academic and inbox data** needs subject/profile ownership as well as
  authentication. Start at [AcademicEnrollmentReadController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/web/AcademicEnrollmentReadController.java),
  [AcademicAttendanceReadService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicAttendanceReadService.java),
  [ThesisGroupReadController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/web/ThesisGroupReadController.java),
  and [NotificationReadController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/notification/web/NotificationReadController.java).
  A supplied record ID is a locator, never proof of access.
- **Announcement audiences** must protect detail access and inbox previews as
  well as feeds; a preview can disclose the same private content as the full row.
  The owners are [AnnouncementReadService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementReadService.java)
  and [AnnouncementStudentNotifier](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementStudentNotifier.java).
- **Internal AI calls** use a distinct service credential and owner boundary.
  See [ThesisAssistantInternalController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantInternalController.java)
  and [SupabaseKnowledgeInternalController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/SupabaseKnowledgeInternalController.java).

## Signing configuration

Published development secrets are unsuitable for deployment, and access and
refresh credentials must remain separate token families. The startup policy is
owned by [JwtSecretPolicy](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/JwtSecretPolicy.java)
and `SecurityConfig`; deployment configuration is in
[docker-compose.prod.yml](../../docker-compose.prod.yml).
