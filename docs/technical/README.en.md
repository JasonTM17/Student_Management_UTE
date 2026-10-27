# CampusCore technical guide

[Tiếng Việt](README.vi.md) · [Project overview](../../README.en.md) · [Documentation index](../README.md)

This guide explains techniques implemented in the repository: the problem, execution flow, data, protection boundaries, trade-offs and verification. Each chapter has a matching Vietnamese edition and links to the class or test that owns the behavior. Values come from the linked source/configuration and may differ between environments.

| Chapter | Coverage |
|---|---|
| [1. Architecture, frontend and security](architecture-security.en.md) | Modular monolith, JDBC/JPA, BFF, caching, i18n, JWT, CSRF, refresh rotation, 2FA, authorization and rate limiting |
| [2. Academic workflows](academic.en.md) | Concurrent registration, idempotency, curriculum plans, credit limits, withdrawal, transcripts, appeals, attendance, conduct and exports |
| [3. Projects and theses](thesis.en.md) | Service extraction preserving transactions, round lifecycle, faculty/department scope, groups, eligibility, reports, counter-review, councils and publication |
| [4. AI and academic communication](assistant-engagement.en.md) | Lexical RAG, personal context, SSE, quota/leases/CAS, RLS, two-person publishing, TinyMCE, announcements, email and audit |
| [5. Mobile, data and operations](operations-verification.en.md) | Expo/Bearer, Flyway, storage, Docker, readiness, backup, CI, verification levels and reproducing screenshots/GIFs |

## Reading route

Start with chapter 1 to identify responsibility for each boundary. Then choose a business chapter. Trace writes through **UI → proxy → controller → service → repository/SQL → commit → notification**. For multi-role operations, inspect both route authorization and the service's data scope.

This is a guide to repository implementation. A test's existence does not prove execution; local screenshots do not prove production; configuration for DeepSeek, SMTP or Supabase does not establish live-service acceptance. The [current media showcase](../media-showcase.en.md) states capture provenance and limitations.
