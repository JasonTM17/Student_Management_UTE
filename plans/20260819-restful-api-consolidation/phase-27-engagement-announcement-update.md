# Phase 27 — Engagement announcement update candidate

## Outcome

Add the Java REST API monolith's feature-default-off announcement update
candidate while preserving the legacy engagement service as the public
announcement writer, event publisher and rollback target.

## Boundary and authority

- Candidate route: `PUT /api/v1/announcements/{id}`.
- The route exists only with the `persistence` profile and
  `migration.engagement-write.enabled=true`; `ENGAGEMENT_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java probes the Prisma-owned `"engagement"."Announcement"` row before update,
  mutates only provided request fields plus `"updatedAt"`, and returns the
  shared announcement response shape.
- This phase does not add deletion, RabbitMQ publication, notification fan-out,
  nginx routing, public ownership or production cutover.

## Compatibility contract

- Preserve the legacy path, method and admin-only authorization.
- Preserve the update DTO fields: `title`, `content`, `priority`,
  `targetRoles`, `targetYears`, `isGlobal`, `publishAt`, `expiresAt`,
  `semesterId`, `sectionId` and `lecturerId`.
- Preserve partial-update behavior: omitted fields remain unchanged, while
  provided scalar, array and timestamp fields are written. Explicit JSON `null`
  clears nullable timestamp and academic pointer fields.
- Reject unknown body properties instead of silently ignoring typos, matching
  the legacy Nest validation-pipe posture.
- Preserve existing `publishedBy` and `createdAt`; Java does not invent
  academic names or lecturer display names when only IDs are supplied.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.
- Treat RabbitMQ/event and notification behavior as still legacy-owned until a
  separate parity gate exists.

## Verification

- Feature-on H2 tests cover:
  - admin partial update with strings, priority, arrays, global flag, dates and
    academic pointers;
  - explicit-null clearing for `publishAt`, `expiresAt`, `semesterId`,
    `sectionId` and `lecturerId`;
  - preservation of `publishedBy` and `createdAt`;
  - no invented semester/course/lecturer display data;
  - student access denied, missing announcement, unknown body property, invalid
    priority, invalid target role type and invalid target year failure
    envelopes.
- The monolith shell contract covers feature-default-off behavior for
  `PUT /api/v1/announcements/{id}`.

## Verification observed

- Focused H2/source gate passed on 2026-08-20:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.engagement.AnnouncementWritePersistenceTest,io.campuscore.restfulapi.engagement.AnnouncementReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 35 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-20:
  `mvn -q -f java-services/pom.xml test`.
- Observed reactor result: 115 tests, 0 failures, 0 errors, 0 skipped.
- Repository guards passed: `node scripts/check-doc-hygiene.mjs`,
  `node scripts/check-architecture.mjs` and `git diff --check`.
- Engagement SQL scan shows the intended announcement `INSERT`/`UPDATE` writer
  plus the existing support-ticket write mutations only.
- During the first focused run, the update test caught stale snapshot behavior:
  changing `semesterId`, `sectionId` or `lecturerId` could leave the prior
  semester/course/lecturer display snapshot attached. The repository now clears
  those stale snapshot fields when the owning IDs are updated.
- Independent exact-head review at `0a931d9` then found that omitted and
  explicit-null update fields were indistinguishable. The DTO/repository now use
  presence-aware patch values so explicit nulls clear nullable fields while
  omitted fields remain unchanged.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL write parity, RabbitMQ/event
parity, notification fan-out, route canary, rollback rehearsal, public writer
handoff and independent Advisor/Kongming/Wukong exact-head review remain
`NOT_RUN`.
