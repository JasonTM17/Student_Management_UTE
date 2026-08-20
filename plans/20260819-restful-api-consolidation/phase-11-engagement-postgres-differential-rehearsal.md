# Phase 11 — Engagement PostgreSQL differential rehearsal

## Outcome

Compare the legacy Nest engagement reads and the Java monolith candidate on one
immutable, isolated PostgreSQL snapshot. This is a private rehearsal only: it
does not enable the public route, move writer ownership, publish RabbitMQ
events, or modify the active CampusCore stack.

## No-go boundary

- Never connect either rehearsal process to the active `campuscore-db` target.
- Use a disposable PostgreSQL instance, volume, network, port, database and
  credentials on D:, restored from an approved scrubbed backup or deterministic
  fixture with a recorded checksum.
- Both runtimes must have Flyway/Prisma migration, `db push`, DDL and seeding
  disabled. Java starts with `ENGAGEMENT_READ_ENABLED=true` and
  `FLYWAY_ENABLED=false` using a role limited to `CONNECT`, schema `USAGE`, and
  table `SELECT`; deny ownership, TEMP, CREATE, DML and superuser capability.
- With the engagement flag on, Java must show that its Flyway rejection strategy
  is installed and Hibernate schema management resolves to `none`; the role must
  not require thesis-schema visibility merely to start the shared deployable.

## Physical schema gate

Before requests, record `information_schema.columns`, PostgreSQL server/timezone
settings and Prisma schema identity for `"engagement"."Announcement"`.
Verify exact quoted identifiers, nullability, text/integer array types, Boolean,
and all `DateTime` columns. The source candidate expects Prisma's PostgreSQL
default `timestamp(3)` representation and converts it to/from UTC explicitly;
any restored physical difference is a HOLD that requires a reviewed adapter,
not an implicit cast.

## Differential corpus

Run the same signed bearer/cookie identity and request corpus against private
legacy and Java endpoints. Normalize JSON object key order only. Compare status,
data, meta, nulls, arrays, timestamps, ordering and relevant headers for:

- global, role-targeted, year-targeted and lecturer-targeted announcements;
- missing student/lecturer profile claims, missing `sub`/`email`, anonymous,
  wrong admin role and multi-role identities;
- null, past, exact-boundary and future publish/expiry timestamps, including
  millisecond precision and non-UTC client inputs;
- pages 1 and N, empty pages, limits 1/20/200/201, totalPages and concurrent
  insert pressure while the legacy writer remains the sole writer;
- all admin filters alone and combined, blank/whitespace values, unknown and
  repeated query parameters, invalid priority and malformed numbers;
- flattened and derived semester, section/course and lecturer response fields;
- the known Nest-versus-monolith error-body difference and the deliberate Java
  fail-closed profile-claim behavior, each requiring an explicit product/API
  decision rather than being hidden as a zero diff.

## Acceptance evidence

- exact Java/legacy commit or image, config and fixture identities;
- successful isolated restore and physical schema/type report;
- read-only privilege proof plus database audit showing no DML/DDL/TEMP;
- Flyway strategy/default evidence and `FLYWAY_ENABLED=false` startup evidence;
- effective Hibernate `hbm2ddl.auto=none` and no thesis-schema privilege proof;
- redacted signed-auth request corpus and explained differential report;
- bounded latency/query-plan evidence for audience arrays and pagination;
- private legacy → Java → legacy route rehearsal with unchanged legacy writer;
- cleanup of only the disposable target and a fresh exact-head review.

Until all evidence exists, this phase is `NOT_RUN`/`HOLD`; H2 tests and source
inspection are not substitutes for PostgreSQL parity or rollback proof.

## Observed rehearsal — 2026-08-20

Verdict: **bounded runtime PASS; production cutover HOLD**.

The exact provenance-bound probe checkpoint was
`f4188d7fe10ec0459c70f4ef4ed0f52e24191c33`. The Java timestamp source commit
was `b6c6e0d863642a6cdbdb89e2e87dd2fbec8d4d57` and no Java production source
changed between those identities. The owned probe exported only the committed
Java module, engagement service and shared auth package from that exact HEAD
with `git archive`, extracted them into an isolated D-hosted snapshot, and ran
offline Maven `clean package` plus a direct TypeScript build there with narrow
environments. It then hashed the resulting Spring Boot and Nest artifacts
before loading either runtime. This binds the observed run to one exact
isolated build; it is not a reproducible-build claim:

- JAR SHA-256:
  `F7051BE0CD7B2398E6EA8C4FF0C3146A877861BB8E836586F1E49EBEFA1EBACD`;
- Java reference artifact SHA-256:
  `42BE39C5BBB87CB5B44E1B7DC5DBBB17259676A40F0C61F89B9BE046135716E3`;
- sequential differential report SHA-256:
  `248D7FB6FB29AA3734C08BA8EEE0768C1A6E703DAB2D528AE717073574D0711F`;
- committed rehearsal source archive SHA-256:
  `CEDC38DE9DAB7C82C41205F306E498FB58BF10B1D3715BB42027FF0B7B713F21`;
- Maven clean-build log SHA-256:
  `CD1D7249DA1315F66DC9872B6DBAB7EE243E12A7E0BBC2DE3EC8C401C67C8625`;
- selected Maven launcher SHA-256:
  `5F9B8EAC523030CD9818DAA0EB337635E06137D0DA95A66A0A5425D668050E12`;
- selected Java executable SHA-256:
  `94DC54724B34717DAD127855DC8228AF8BC2E021C19B233FFF0DF4525F7698B3`;
- owned Java log SHA-256:
  `DA0FC1289A01976139B2CF947919ACC63D6A1A2CF8693BB93159EE45EEDDFF49`;
- selected Git/CMD/TAR/PowerShell executable SHA-256 values:
  `54194A1AF7CFB6730448CE14B8F2C1DDDD9F950B7F995DC351C1ED16EB179249`,
  `8DD1EBB0B969370C70A5EE7F7EE347949AA7046AA5E1A33FCD7B1E9415B21FC3`,
  `9B77D4C912F2EDAE8C241D0ECE1094D2AC068B084269CEAF85D7C7B085D2AE86`,
  and `7600FFE12DA441FE89D035B13801E8E91D064BC544A27B19A5CF49F6AB8B18F5`;
- legacy Nest `dist/src/main.js` SHA-256:
  `DD5B004DC457BA61235704FB4633638907675F1940F747E109EDBB3BF988BD43`;
- legacy TypeScript build log SHA-256 (empty successful stdout):
  `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`;
- selected Node and TypeScript compiler SHA-256 values:
  `2FFE3ACC0458FDDE999F50D11809BBE7C9B7EF204DCF17094E325D26ACE101D8`
  and `8D5FA5BD883FEC0979FC2004F1FE1D99AEF40570155D550EADC0B03B55513BF0`.

The response artifacts are outside Git in the D-hosted Phase 11 recovery root.
They contain response data and source/artifact identities, but no bearer or
cookie token values. The probe derives the Git HEAD and hashes from the checkout,
build tools and files it actually observes; it rejects a tracked-dirty checkout
instead of accepting caller-supplied identity strings.

### Isolated database and startup evidence

- PostgreSQL 18.4 ran on loopback port `56432` with its data, log and temporary
  files on D. This did not connect to or restart the active CampusCore Docker
  stack.
- The rehearsal database contained the quoted
  `"engagement"."Announcement"` table and nine deterministic rows; it did not
  contain the `thesis` schema.
- Java started on `127.0.0.1:56410` with the persistence profile,
  `ENGAGEMENT_READ_ENABLED=true`, `FLYWAY_ENABLED=false`, and all writer
  ownership left with the Nest service. Successful startup without thesis
  schema visibility is runtime evidence for the engagement Hibernate/Flyway
  safety override in this environment.
- The database role had CONNECT, schema USAGE and table SELECT. It lacked TEMP,
  schema CREATE, INSERT, UPDATE and DELETE.
- The fixture checksum remained nine rows and
  `7a3d71488910b1e7f0e84a8819130320` after all Java and Nest requests.
- The PostgreSQL statement log contained no explicit write statement after the
  initial database/role/fixture setup. The intended Java application-name tag
  was not present in connection log lines, so statement attribution by tag is
  not claimed; the role-level deny proof and unchanged checksum are the stronger
  write-safety evidence.
- Local `trust` authentication was used only inside the disposable loopback
  cluster. This proves SQL privilege containment, not production database
  authentication.

### Differential result

The reusable probe is
`engagement-service/test/engagement-read.rehearsal.cjs`. In one process it runs
an exact-commit `git archive` plus isolated offline clean Java and Nest builds,
spawns Java with a fixed structured `-jar` argv and a narrow allowlisted environment,
requires the loopback listener PID to equal that owned child PID, captures Java
responses in memory, verifies that the JAR hash did not change, stops Java, and
only then starts the in-process Nest application for comparison. It writes the
reference artifact after capture for audit, but never reads an external
reference for comparison. It loads the freshly built isolated Nest modules, not
the ignored working-tree `dist`, and removes its temporary dependency junctions
after the comparison. This sequential shape was selected because a prior
Jest/ts-jest attempt exhausted its bounded Node heap before running tests; that
attempt remains `NOT_RUN`, not a functional failure.

The provenance-bound checkpoint fails before importing `AppModule` unless every
isolation invariant is present: `NODE_ENV=test`; no inherited RabbitMQ URL; an
exact `postgresql://engagement_reader@127.0.0.1:56432/engagement_rehearsal`
target with the `engagement` schema; a D-hosted Phase 11 run root; and a live
`postmaster.pid` whose data directory, port, listen address and ready marker
match that root. Reference and report paths must be descendants of the same
root. The probe deletes the absent RabbitMQ environment key before Nest config
loads, so an `.env` file or inherited broker URL cannot activate queue setup.
Owned Java execution additionally requires the exact loopback URL
`http://127.0.0.1:56410/`, rejects credentials/query/fragment and redirects,
rejects the retired split capture mode and any caller-provided reference, and
binds the listener PID to the child process it spawned from the freshly built
D-hosted JAR. Maven and Java receive only required Windows process keys plus
explicit rehearsal values; inherited Spring/JVM/Maven overrides are not copied.
The report records the Java reference hash, actual clean source HEAD, source
archive/build/JAR/Nest/tool/log hashes, owned PID and actual legacy
entry-artifact hash.

Thirteen signed/negative cases produced equal HTTP status and normalized
content type. Status, successful response body and normalized content type are
assertions; a mismatch cannot produce `PASS`:

- student bearer and cookie, lecturer bearer and combined admin filters;
- student/admin pagination and an empty page;
- blank priority, unknown query, limit overflow and repeated query;
- anonymous access and a student attempting the admin listing.

All seven successful responses had exact full-body parity, including ordering,
derived semester/section/course/lecturer objects, arrays, nulls, pagination
metadata and millisecond timestamps. The first comparison falsified parity
because Java emitted whole-second instants without `.000`; the engagement DTO
now formats all four timestamp fields as UTC with exactly three millisecond
digits, and a regression test covers this contract.

The six error cases had matching status and content type but deliberately
different bodies: the legacy Nest body did not expose the Java error `code`,
while Java returned `INVALID_REQUEST`, `UNAUTHENTICATED`, or `ACCESS_DENIED`.
The report records `errorBodyParity=false`; this remains an explicit product/API
decision and is not represented as full parity.

### Verification observed at the exact checkpoint

- `node --check engagement-service/test/engagement-read.rehearsal.cjs`: PASS.
- negative preflight cases for a substring-smuggled active database URL,
  inherited RabbitMQ URL, C-hosted run root and a userinfo-smuggled Java URL:
  all rejected before app import or network use.
- external reference comparison and the retired split capture mode: both
  rejected before build or network use.
- build-input/environment contamination regression: untracked Java and Nest
  source files were placed inside the normal modules while malicious parent
  `SPRING_APPLICATION_JSON` plus `JAVA_TOOL_OPTIONS` were present; the source
  archive, JAR and Nest `dist` excluded both sentinels, override values were not
  inherited, and the differential still passed. The sentinels were then removed
  and the temporary dependency junctions were verified absent.
- the first isolated Nest build attempt used the Nest CLI with a 192 MiB heap
  and ended in OOM before legacy comparison; it is `NOT_RUN`, not a parity
  failure. The final checkpoint invokes the exact TypeScript compiler directly
  with a bounded 256 MiB heap and completed successfully.
- exact-head owned Java capture: 13/13 cases PASS with the listener bound to the
  spawned PID `40336` and the isolated clean-built JAR/hash shown above; Java
  was stopped before legacy startup and port `56410` was closed after the run.
- sequential differential: 13/13 status/content-type assertions PASS and 7/7
  successful full-body assertions PASS; the six intentional error-envelope
  differences remained visible as `errorBodyParity=false`.
- post-request fixture proof: nine rows and checksum
  `7a3d71488910b1e7f0e84a8819130320`, unchanged from baseline.
- focused `AnnouncementReadPersistenceTest`: 6/6 PASS.
- full Java test suite: 45/45 PASS, zero failure/error/skip, run after the exact
  source commit.
- offline Maven package: PASS; JAR hash shown above.
- production engagement source scan: read candidate remains GET/SELECT/COUNT
  only, with feature flags default-off.

### Remaining HOLD gates

This rehearsal does not authorize traffic or deletion of the Nest service. The
following remain required:

- a production-version PostgreSQL clone or approved scrubbed snapshot, rather
  than PostgreSQL 18.4 plus deterministic fixtures;
- production-equivalent database authentication and a real signed token from
  the authoritative issuer;
- the remaining null/time-boundary/malformed-claim corpus, bounded query-plan
  and latency evidence, and deterministic concurrent-writer observation;
- an approved error-envelope contract;
- private legacy → Java → legacy route switching, canary thresholds, monitored
  rollback and rollback timing;
- fresh exact-head Advisor, Kongming and Wukong gates after this evidence is
  committed.
