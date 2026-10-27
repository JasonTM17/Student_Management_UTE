# 1. Architecture, frontend and security

[Tiếng Việt](architecture-security.vi.md) · [Technical index](README.en.md) · [Next: academic workflows](academic.en.md)

## T01. A modular monolith with an explicit sidecar boundary

**Problem.** Identity, enrollment and theses require consistent transactions. Turning every domain into a microservice would introduce distributed coordination. CampusCore organizes one Java application into `auth`, `academic`, `thesis`, `engagement`, `notification`, `mail`, `audit`, `site`, `people`, `system` and `security` domains.

**Flow.** Next.js calls same-origin `/api/v1`; its proxy forwards to the Java REST API. Mobile uses the same Java contract with Bearer authentication. Java owns business rules and PostgreSQL. The RAG sidecar runs the same JAR in a separate mode behind `/internal/rag/assistant`, protected by a service token. Compose exposes port 4011 internally. User-facing assistant routes remain authenticated public API routes.

**Trade-off.** RAG gets its own timeout and resource limits without splitting academic domains into separate services. The API and sidecar still share a schema and Java artifact, so migration compatibility matters during upgrades.

**Owners:** [Maven aggregator](../../java-services/pom.xml), [Compose](../../docker-compose.yml), [RAG gateway](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/client/RagAssistantGateway.java).

## T02. Controller–service–repository and explicit SQL transactions

Controllers bind DTOs and validate input shape. Services own business decisions, data scope and transactional units. Repositories read and write data. Most business paths use `NamedParameterJdbcTemplate`, parameterized SQL and dedicated read DTOs, making joins, conditional updates and lock acquisition visible.

Allowed JPA entities/repositories remain in specific areas; an entity's existence does not mean every path uses Spring Data JPA. Flyway owns DDL. `FOR UPDATE`, conditional capacity increments, RLS context and transactional audit must be understood through their executable SQL owners.

**Why keep SQL here?** During contention, read/lock/write order is part of the contract. Replacing a statement with ORM can change flush timing and constraint-error handling. `ArchitectureGuardTest` owns the architectural restrictions; inspect it before expanding JPA repositories.

**Owners:** [AcademicMutationService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicMutationService.java), [thesis facade](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationService.java), [architecture guard](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/ArchitectureGuardTest.java).

## T03. Next.js BFF: same origin, cookies and SSE preservation

**Input/output.** `/api/v1/[...path]` accepts a method, query, headers and body, constructs a URL under the Java API prefix and returns upstream status, headers and body. `buildApiProxyUrl` rejects `.`/`..` segments before URL normalization could escape that prefix.

The proxy replaces caller-supplied client-IP headers with runtime metadata or a shared proxy bucket. A trusted proxy/API token is a separate boundary. A browser-supplied `X-Forwarded-For` alone cannot establish the real user IP.

JSON responses are buffered so session cookies are forwarded consistently; separate `Set-Cookie` values must remain separate. SSE directly forwards `response.body`, using `no-cache, no-transform` and `X-Accel-Buffering: no` so deltas reach the caller before upstream completion. HEAD and statuses 204/205/304 correctly return no body.

Only an explicit set of public GETs gets shared caching. Private responses must not acquire public caching by default. JSON buffering costs memory proportional to body size; SSE requires an open connection and cancellation handling.

**Owners:** [API proxy](../../frontend/src/app/api/v1/[...path]/route.ts), [URL builder](../../frontend/src/lib/proxy-url.ts), [proxy regressions](../../frontend/tests/docs-route-contracts.test.js), [SSE proxy tests](../../frontend/tests/assistant-proxy-stream.test.js).

## T04. React Query, identity isolation and honest UI states

React Query owns server-data caching, stale windows and read retries. Each browser tab uses one QueryClient; SSR creates a fresh client per request. Some keys omit a user ID, so `AuthContext` clears the cache when identity changes or a session ends. Updating only the header's account label would leave the next user exposed to the previous user's cached data.

Mutations update or invalidate related query sets. Hiding an action is a UX aid; the backend still authorizes it. The UI distinguishes loading, empty, forbidden and connectivity failures instead of presenting failed reads as valid empty lists. Charts and totals use API data rather than string arithmetic or sample placeholders.

**Owners:** [query client](../../frontend/src/lib/query.ts), [AuthContext](../../frontend/src/context/AuthContext.tsx), [identity-cache regression](../../frontend/tests/query-cache-identity.test.js), [numeric totals](../../frontend/tests/dashboard-numeric-totals.test.js).

## T05. Bilingual routing and civil dates

The web has `/vi` and `/en` route prefixes, locale message dictionaries and helpers for adding/removing prefixes. Language changes preserve route meaning; individual buttons should not manually concatenate locale paths. API role/status codes are mapped to local copy; stable machine error codes become understandable messages.

A date-only `YYYY-MM-DD` value is a civil day, unlike a timestamp with an offset. Converting every date to `Date` and formatting in another timezone can move it to the previous day. Thesis date/time fields also require consistent conversion between displayed local time and API instants. The backend rechecks deadlines; a rounded “one day remaining” label does not authorize a write.

**Owners:** [i18n](../../frontend/src/i18n/), [thesis date conversion](../../frontend/src/lib/thesis-datetime.ts), [calendar regressions](../../frontend/tests/calendar-date-display.test.js), [24-hour input](../../frontend/src/components/ui/date-time-24-field.tsx), [locale parity](../../frontend/tests/locale-route-parity.test.js).

## T06. JWT and cookies: authentication versus current authority

**Login.** Java checks BCrypt credentials and account state, then issues access/refresh tokens or a 2FA challenge. Web sessions use HttpOnly `cc_access_token` and `cc_refresh_token` cookies plus a client-readable CSRF cookie. Current code uses `SameSite=Lax`; `Secure` depends on environment configuration. Mobile receives tokens in the body. Academic offices issue accounts; the registration page is not public account creation.

**Every request.** The decoder requires an `access` token and a roles claim; a refresh token cannot substitute for a business access token. `AccountStateFilter` compares token roles, permissions, profile identity and credential revision with current account data. Disabled accounts, stale authority and replaced credentials must be rejected or reauthenticated. An authority lookup outage must not become authorization bypass.

A JWT signature establishes issuer/integrity, not that authority remains unchanged since issuance. This mechanism also does not retroactively cancel work authorized before the authority-changing transaction commits.

**Owners:** [SecurityConfig](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/config/SecurityConfig.java), [AccountStateFilter](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/filter/AccountStateFilter.java), [cookie contract](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/service/SessionCookieService.java), [authorization model](../security/authorization-model.md).

## T07. Refresh rotation, single-flight and multiple tabs

**Problem.** Two simultaneous 401 responses can initiate competing refreshes. An old tab can also return cookies after another tab signs in or out.

**Web side.** `refreshSessionSingleFlight` shares one promise; `withSessionTransport` serializes session mutations. Web Locks coordinates tabs where available; an in-memory queue is the same-document fallback. `BroadcastChannel`/storage messages contain a revision and session-change event, never tokens. AuthContext generations discard stale completions and clear identity-dependent caches.

**Java side.** Rotation updates hashes on a Session row, with `sid` binding the refresh token to that row. A deleted/expired session is distinguished from a superseded token on a live session. A bounded overlap window recovers a lost rotation response; it is not a guarantee for every concurrent ordering. Replays outside that contract still follow reuse detection.

**Recent repairs.** After a 401 session probe, the web tries one refresh before declaring session death. A stale access cookie must not lock anonymous login/refresh/recovery endpoints. The [session model](../security/authorization-model.md) defines the exact window, exceptions and revocation limits.

**Owners:** [API transport](../../frontend/src/lib/api.ts), [session sync](../../frontend/src/lib/session-sync.ts), [AuthLoginService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/service/AuthLoginService.java), [transport tests](../../frontend/tests/session-transport.test.js), [poll/refresh tests](../../frontend/tests/session-poll-refresh.test.js).

## T08. CSRF, 2FA and credential changes

Because browsers send cookies automatically, writes require CSRF protection beyond possession of a session cookie. `CsrfCookieFilter` owns cookie/Bearer rules and exemptions. Disabling Spring's built-in CSRF mechanism does not mean the project has no CSRF protection; it installs its own filter.

2FA emails an OTP after password verification, without issuing a session at step one. Enabling/disabling 2FA and changing passwords advance credential revision and apply the refresh-session policy. Invalid challenges have bounded attempts; they must not change security flags or revoke sessions as though confirmation succeeded.

Password recovery gives a generic response to avoid account enumeration. SMTP remains an operational dependency: templates and code do not prove delivery to a real mailbox. PostgreSQL interleaving tests exercise User/Session/ResetToken locks and refresh races on the actual engine.

**Owners:** [CSRF filter](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/filter/CsrfCookieFilter.java), [TwoFactorService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/service/TwoFactorService.java), [PasswordResetService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/auth/service/PasswordResetService.java), [credential/session races](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/auth/CredentialSessionLockOrderPostgresIT.java).

## T09. RBAC combined with ownership and data scope

`STUDENT`, `LECTURER`, `ADMIN`, `SUPER_ADMIN`, `TRUONG_KHOA` and `TRUONG_BO_MON` are composable authorities. Route-role checks are the first layer. Services also check enrollment ownership, assigned teaching sections, explicit supervisor assignments and faculty/department governance scope.

A faculty head is not automatically an institution-wide administrator. Authority depends on a current active lecturer profile bound to the JWT subject. A concrete academic assignment may grant legitimate cross-faculty read/grading access without granting every governance mutation. An API may return 404 to avoid disclosing out-of-scope resources.

**Owners:** [authorization model](../security/authorization-model.md), [dean scope](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/scope/ThesisDeanScope.java), [department scope](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/scope/ThesisDepartmentScope.java), [thesis governance](thesis.en.md).

## T10. Rate limiting, validation and structured failures

DTO bounds, request-body filters and business validation constrain input. Web validation gives earlier feedback but never replaces backend checks. Rate limiting separates actions such as login and assistant requests. Clients respect `Retry-After`; a showcase must not disable the limiter to appear successful.

`DomainException` and error responses preserve HTTP status, stable codes and useful details. The frontend maps codes to VI/EN copy so users can act on a closed registration window, missing authority or invalid score. Stack traces, tokens and raw provider errors do not belong in product messages.

**Owners:** [rate-limit implementation](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/ratelimit/), [exception boundary](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/exception/), [localized errors](../../frontend/src/lib/campus-error.ts), [mail DTO bounds](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/dto/MailDtos.java).
