# CampusCore authorization model

Authorization combines route access, current account authority, and ownership of
the requested data. A controller annotation alone is not an authorization audit:
class-level annotations, the filter chain, and service/repository scope all matter.

## Executable owners

- [SecurityConfig](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/config/SecurityConfig.java)
  owns the public route and HTTP-method allowlist, authenticated fallback, token
  validation, and role conversion. Use it instead of maintaining a second route
  list here. Accounts are office-issued; there is no public self-registration.
- [AccountStateFilter](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/filter/AccountStateFilter.java)
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

Enabling or disabling two-factor authentication is the same credential
boundary: confirming either challenge advances the credential revision, so
once the transaction commits every access token issued before the
confirmation stops authorizing business requests, and refresh sessions other
than the caller's are deleted. Two narrow exceptions apply: the signed,
unexpired stale token can still authenticate the session-cleanup endpoints
that deliberately skip revision matching (`/api/v1/auth/logout` and the
login/refresh/recovery endpoints that establish or discard session state),
and a request already authorized before the commit may finish — this
boundary does not cancel in-flight work. The caller's current browser
refresh session is the one explicit session exception — the confirm pins it
to its stable `Session.id` (which survives refresh rotation) *before* taking
the credential lock, so a rotation committed mid-flow still preserves the
same row, kept so the user is not signed out mid-flow, and its next rotation
issues an access token carrying the new revision. When the request's session
can no longer be identified — no cookie, or the row was already rotated away
or revoked — every session is revoked instead. The same pinning applies to
`logout`: the pinned row is deleted under the lock even if its hash rotated,
and an unidentifiable presented session escalates to revoking every session
of the account. A rejected confirmation code does not alter the revision,
the two-factor flag, or any session — the challenge's failed-attempt
bookkeeping (the five-strikes lock) still persists. See `TwoFactorService`
and `TwoFactorLoginPersistenceTest` for the executable cases; the raced
rotation schedules live in `CredentialSessionLockOrderPostgresIT`.

Browser refresh sessions rotate on every exchange: the `Session` row carries
the current refresh-token hash plus the hash it replaced
(`previousRefreshToken`) and the rotation instant (`rotatedAt`, written from
the application clock in UTC). A signed refresh token that matches no live
row is the stolen-token signal and revokes the whole session family — with
one bounded exception, scoped to exactly one ordering.

The covered ordering is the lost-response case: a document's refresh commits
a rotation but its response never reaches the shared cookie jar (navigation
mid-flight, transport timeout, dropped network answer), so the jar — and any
sibling document — still holds the immediately-previous token. Replaying
that token inside the overlap window recovers: the same `Session.id`
rotates forward again, the family survives, and the overlap anchor is not
extended by repeated replays. The window is 30 seconds from `rotatedAt`,
with a 2-second forward tolerance for cross-instance clock skew, so the
real upper bound on acceptance is 32 seconds after the rotation commit; a
`rotatedAt` further in the future fails closed.

The delayed-duplicate ordering is NOT covered: two requests carrying the
same token, where the second-processed one rotates and delivers its new
token to the jar, then the delayed first request recovers inside the window
and overwrites the row with a token whose response may itself be lost — the
delivered token is then neither current nor previous, so the jar's next
refresh still revokes the family. Stolen-token detection inside the window
is likewise deferred, not removed: an in-window replay yields the replayer a
fresh pair and evicts the legitimate holder, who is signed out at their next
refresh. Accepted recoveries leave a `REFRESH_ROTATION_OVERLAP` warn line
(session id, IP, user agent — never token material); every other mismatch
still revokes every session and leaves a `REFRESH_TOKEN_REUSE` line. The
executable contract lives in `AuthLoginPersistenceTest`; window and
tolerance are code constants, not configuration.

Session-bound refresh tokens additionally carry a `sid` claim naming their
`Session` row, which lets a hash miss distinguish "the row is gone or has
expired" — an administratively revoked, logged-out, or naturally elapsed
session whose replay is a dead-session refresh and fails quietly — from
"the row lives but the token is superseded", the stolen-token signal that
still revokes the family. Refresh tokens minted before `sid` existed carry
no claim and keep the legacy semantics: every signed token that matches no
live row revokes the whole family. That legacy exposure is bounded by one
refresh-token lifetime (`JWT_REFRESH_TOKEN_TTL_SECONDS`, 604800 seconds):
rotation on every exchange means any pre-deploy token is either consumed
or naturally expired within its own lifetime, and the oldest surviving
sid-less token dies with the family it would otherwise have wiped.

The same lost-response boundary applies to session-bound credential changes
(mobile change-password): the rotation commits server-side and re-issues the
kept row, but if the response never reaches the client the stored pair is now
superseded. The client's next 401-triggered refresh presents that stale token,
the reuse detector revokes the family, and the user lands at sign-in — where
the new password works, because the credential write itself already committed.
The mobile client narrows the window by pausing sibling refreshes until the
rotated pair is adopted (`withPausedSessionRefresh`); it cannot remove it.

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
  semester lookup. [AcademicReadController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/controller/AcademicReadController.java)
  owns those reads and distinguishes the student's personal curriculum route.
- **Private academic and inbox data** needs subject/profile ownership as well as
  authentication. Start at [AcademicEnrollmentReadController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/controller/AcademicEnrollmentReadController.java),
  [AcademicAttendanceReadService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicAttendanceReadService.java),
  [ThesisGroupReadController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/controller/ThesisGroupReadController.java),
  and [NotificationReadController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/notification/controller/NotificationReadController.java).
  A supplied record ID is a locator, never proof of access.
- **Student directory data** is relationship-scoped, not role-scoped. Admins
  keep the full directory; students still read only their own record.
  Lecturers — including `TRUONG_BO_MON`/`TRUONG_KHOA` — see only students
  enrolled in sections they teach or reach through the section-access
  department/faculty rules, and students in thesis groups whose topics they
  supervise, review, or sit on a council for. List totals and the status
  filter apply to the scoped set, and an out-of-scope detail id answers the
  same 404 as a missing id. The owner is
  [LecturerDirectoryScope](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/people/model/LecturerDirectoryScope.java).
- **Announcement audiences** must protect detail access and inbox previews as
  well as feeds; a preview can disclose the same private content as the full row.
  The owners are [AnnouncementReadService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementReadService.java)
  and [AnnouncementStudentNotifier](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementStudentNotifier.java).
- **Internal AI calls** use a distinct service credential and owner boundary.
  See [ThesisAssistantInternalController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/controller/ThesisAssistantInternalController.java)
  and [SupabaseKnowledgeInternalController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/controller/SupabaseKnowledgeInternalController.java).
- **Course-withdrawal applications (grade W)** follow the same ownership +
  review split as credit-limit applications. The student routes
  (`/me/registration/withdrawal-applications`, `/withdrawal-window`) require
  `STUDENT` and the service binds every application to the caller's
  `studentId` claim — a supplied enrollment/application id is a locator, never
  proof of access (`403 ENROLLMENT_FORBIDDEN` /
  `WITHDRAWAL_APPLICATION_FORBIDDEN`). Review
  (`/admin/registration/withdrawal-applications`) is `ADMIN`/`SUPER_ADMIN`
  only; lecturers have no approval capability. Owners:
  [WithdrawalApplicationController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/controller/WithdrawalApplicationController.java),
  [WithdrawalApplicationService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/WithdrawalApplicationService.java).
  Unlike a self-service drop inside the add/drop window (which leaves no
  transcript trace), an approved withdrawal is the later regulated path: it
  records `WITHDRAWN` + grade `W` on the transcript, excluded from GPA,
  attempted and earned credits.
- **Grade appeals** bind student submission and cancellation to the caller's
  own enrollment; a grade may be appealed once while its published final grade
  is within `grade.appeal-window-days` (default `7`) from
  `Enrollment.gradePublishedAt`. A pending appeal makes the grade `APPEALED`
  and locks ordinary grade edits and publication. The section's assigned
  lecturer may accept a corrected grade or reject the appeal; `ADMIN` and
  `SUPER_ADMIN` can resolve any appeal. A reviewer cannot be the student who
  owns the appeal. `ADMIN`/`SUPER_ADMIN` alone may unpublish a section's grades
  with a recorded reason; any pending appeal blocks that operation. These
  actions record before/after audit state and notify the affected student or
  lecturer. Owners:
  [GradeAppealController](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/controller/GradeAppealController.java),
  [GradeAppealService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/GradeAppealService.java).
- **Thesis governance actions** run after the dean-scope check
  (`ThesisDeanScope`), so capability gates never leak topic existence or bypass
  faculty ownership. Group approval withdrawal
  (`ThesisMutationService.withdrawGroupApproval`) applies the same discipline:
  `TRUONG_KHOA`/`TRUONG_BO_MON` act only inside their faculty/department scope
  with no supervisor escape, while the topic's own supervisors and global
  admins retain the direct path. Approval by anyone who is not the topic's
  supervisor is a governance override and requires a documented reason
  (`GROUP_APPROVAL_OVERRIDE_REASON_REQUIRED`), recorded on the audit row;
  supervisor self-approval is unchanged. The counter-review waiver is one such action: it stays
  implemented but is off for every shipped undergraduate round type
  (`409 COUNTER_REVIEW_WAIVER_NOT_SUPPORTED`), because those rounds always
  require a counter-reviewer. `ThesisGovernanceCapabilities` owns the decision
  (`RoundType.allowsCounterReviewWaiver()` plus the
  `thesis.governance.counter-review-waiver.enabled` override, default `false`);
  revoke stays ungated so stored waivers can always be cleared.
  Round end-of-life (`POST /api/v1/thesis/rounds/{id}/close` and `/reopen`)
  is narrower still: rounds carry no faculty column, so only
  `ADMIN`/`SUPER_ADMIN` may archive a published round or reopen it — dean and
  department-head scope plays no part. Both demand a documented reason and
  write `THESIS_ROUND_CLOSED`/`THESIS_ROUND_REOPENED` audit rows under the
  round row lock; closing while the results-retract grace window is still
  open is refused (`409 RETRACT_WINDOW_OPEN`) so a lawful retract is never
  silently blocked.
- **Thesis registration eligibility** is an academic precondition, not an
  authorization rule: any authenticated student caller may register only when
  their published transcript meets `thesis.eligibility.min-credits` (110) and
  `thesis.eligibility.min-cpa` (2.0). Refusals answer
  `409 THESIS_ELIGIBILITY_NOT_MET` with numeric `details`; governance roster
  overrides stay reachable to the same privileged roles and audit the
  ineligibility. `ADVISORY` mode reports without blocking.

## Signing configuration

Published development secrets are unsuitable for deployment, and access and
refresh credentials must remain separate token families. The startup policy is
owned by [JwtSecretPolicy](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/token/JwtSecretPolicy.java)
and `SecurityConfig`; deployment configuration is in
[docker-compose.prod.yml](../../docker-compose.prod.yml).
