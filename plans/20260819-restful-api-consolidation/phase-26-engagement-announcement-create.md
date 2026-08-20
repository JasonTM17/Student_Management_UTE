# Phase 26 — Engagement announcement create candidate

## Outcome

Add the Java REST API monolith's feature-default-off announcement creation
candidate while preserving the legacy engagement service as the public
announcement writer, RabbitMQ publisher and rollback target.

## Boundary and authority

- Candidate route: `POST /api/v1/announcements`.
- The route exists only with the `persistence` profile and
  `migration.engagement-write.enabled=true`; `ENGAGEMENT_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java inserts one row into the Prisma-owned
  `"engagement"."Announcement"` table and returns the shared announcement
  response shape used by the read candidate.
- Java sets the legacy defaults: `priority=NORMAL`, empty `targetRoles`, empty
  `targetYears`, `isGlobal=false`, and `publishedBy` from the raw JWT `sub`.
- This phase does not add update/delete, RabbitMQ publication, notification
  fan-out, nginx routing, public ownership or production cutover.

## Compatibility contract

- Preserve the legacy path, method and admin-only authorization.
- Preserve the create DTO fields: `title`, `content`, `priority`,
  `targetRoles`, `targetYears`, `isGlobal`, `publishAt`, `expiresAt`,
  `semesterId`, `sectionId` and `lecturerId`.
- Preserve optional academic-pointer behavior: Java stores the IDs that legacy
  creation stores, and does not invent names for `semesterName`, `sectionNumber`,
  `courseCode`, `courseName` or `lecturerDisplayName`.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.
- Treat RabbitMQ `ANNOUNCEMENT_CREATED` publication as still legacy-owned until
  a separate event/notification parity gate exists.

## Verification

- Feature-on H2 tests cover:
  - admin creation with legacy defaults and `publishedBy` from JWT `sub`;
  - targeted creation with roles, years, priority, global flag, publish/expiry
    timestamps and academic pointers;
  - student access denied, missing required body fields, invalid priority and
    invalid target year failure envelopes.
- The monolith shell contract covers feature-default-off behavior for
  `POST /api/v1/announcements`.

## Verification observed

Current focused Java gates passed locally on Windows with Java 24 and Maven temp
directed to the D drive:

```powershell
$env:JAVA_HOME='<java-home>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Djava.io.tmpdir=<d-drive-temp>'
mvn -q -pl restful-api '-Dtest=AnnouncementWritePersistenceTest,SupportTicketWritePersistenceTest,RestfulApiContractTest' test
```

The same working tree also passed the full Java reactor gate:

```powershell
mvn -q -f java-services/pom.xml test
```

Surefire currently records `AnnouncementWritePersistenceTest` 3/3 passed,
`SupportTicketWritePersistenceTest` 17/17 passed, `RestfulApiContractTest`
21/21 passed, and 113 Java tests total across the reactor.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL write parity, RabbitMQ
announcement-created parity, notification fan-out, route canary, rollback
rehearsal, public writer handoff and independent Advisor/Kongming/Wukong
exact-head review remain `NOT_RUN`.
