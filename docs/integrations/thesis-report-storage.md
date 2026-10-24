# Thesis report storage and recovery

Thesis report uploads use a provider-neutral server boundary. The browser sends
the document to the Java API; it never receives a storage service key and never
writes directly to Supabase Storage.

## Local preview (default)

The default provider is `local`. The [local Compose configuration](../../docker-compose.yml)
mounts a named volume at `/var/lib/campuscore/thesis-reports` to keep document
bytes outside the API container. Preserve and back up this volume alongside the
database: a database backup alone does not contain local report files.

```text
THESIS_REPORT_STORAGE_PROVIDER=local
THESIS_REPORT_STORAGE_BUCKET=thesis-reports
THESIS_REPORT_STORAGE_LOCAL_ROOT=/var/lib/campuscore/thesis-reports
```

The [object-storage migration](../../java-services/restful-api/src/main/resources/db/migration/V46__thesis_report_object_storage.sql)
adds storage references without removing the legacy `file_data` column. New
uploads through object providers (`local` or `supabase`) keep `file_data` NULL;
the [database provider](#database-provider-durable-without-external-storage)
deliberately uses that column instead.

## Supabase Storage provider (explicit environment only)

Set the following only in the server/API runtime after the private bucket has
been created in the intended Supabase project:

```text
THESIS_REPORT_STORAGE_PROVIDER=supabase
THESIS_REPORT_STORAGE_BUCKET=thesis-reports
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<server-only-key>
```

The bucket must be private, allow only `application/pdf`,
`application/msword`, and
`application/vnd.openxmlformats-officedocument.wordprocessingml.document`, and
enforce the same 20 MiB limit as the API. The API uses the service-role key
server-side to put, read, and delete objects; the report download endpoint
still checks the CampusCore group/member/supervisor/staff authorization matrix.

Do not place `SUPABASE_SERVICE_ROLE_KEY` in `NEXT_PUBLIC_*`, frontend code,
browser storage, a committed file, or logs. Do not enable this provider in the
local preview unless a real, private test bucket is intentionally supplied.
No hosted bucket, policy, or object is created by the local Docker workflow.

## Database provider (durable without external storage)

Use `database` when the API host has an ephemeral filesystem and no durable
object store is provisioned. Keeping report bytes with their PostgreSQL row
avoids dependence on an API container's lifetime and lets database backups
include those bytes. The trade-off is increased database and backup size;
choose durable object storage when that cost becomes unsuitable.

The executable owners are [storage selection](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/config/ThesisReportStorageConfiguration.java),
[report persistence](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisReportService.java)
and the [database-storage regression](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisReportDatabaseStorageTest.java).

```text
THESIS_REPORT_STORAGE_PROVIDER=database
```

## Switching providers

Rows record the provider and bucket that stored them. On read the API serves
only the provider that owns the row; a mismatch returns
`REPORT_STORAGE_UNAVAILABLE` rather than silently answering with the wrong
object.

The deliberate compatibility exception is a `local` row whose file still
exists under the configured `THESIS_REPORT_STORAGE_LOCAL_ROOT`; see
`readOrphanedLocalObject` in [report persistence](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisReportService.java).
This fallback reads the retained file. It does not copy that file into the
database or make an ephemeral filesystem durable. Switching providers affects
new uploads; it is not a migration or recovery operation for existing rows.
For remote objects, plan and verify the object and row-reference migration
before changing the active provider or bucket.

## Artifact integrity and concurrent changes

An existing report is an academic artifact, even when its group is awaiting
approval or its submission window has closed. Read access and permission to
change it are separate decisions. The [student page](../../frontend/src/app/dashboard/thesis/page.tsx)
owns presentation; [report persistence](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisReportService.java)
owns authorization, artifact preservation and the write boundary shared with
group membership and round publication.

A missing deadline is not a closed submission window, and a rounded day count
is not permission to write. Keep the timeline aligned with the form's lifecycle
authority; see [timeline presentation](../../frontend/src/features/thesis/components/SubmissionStatusTimeline.tsx)
and its [lifecycle regressions](../../frontend/tests/thesis-report-safety.test.js).

Report confidentiality follows academic responsibility: `ADMIN` and
`SUPER_ADMIN` have institution-wide read authority; `TRUONG_KHOA` has own-faculty
authority determined from the caller's current, active, subject-bound lecturer
profile. An absent, inactive or unbound profile must fail closed. Own-faculty
history includes `PENDING` and topicless groups; approval is not a prerequisite
for preserving staff access to an existing artifact. A faculty head who also
holds `LECTURER` retains legitimate explicit supervisor, council and
counter-reviewer assignments across faculties. Metadata, both round archives
(`reports` and `repository`) and both file-download aliases must share this
boundary so an alternate read route cannot expose another faculty's work.
See `get`, `requireDeanFaculty` and `archiveFilter` in
[report persistence](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisReportService.java)
and the [faculty-scope regressions](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisGroupFacultyScopeTest.java)
for the executable owners.

Metadata changes must not silently discard an attached file, a retained link
or fields omitted by the caller. Replacement and detachment must preserve the
last committed artifact if the transaction rolls back. Object storage is not
part of the database transaction: old-object deletion belongs after commit,
new-object cleanup requires a confirmed rollback, and an unknown transaction
outcome must retain the object for reconciliation. These constraints favor a
recoverable surplus object over a surviving row pointing to deleted bytes.
The [edit regressions](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisReportEditTest.java)
and [concurrency/rollback regressions](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisReportConcurrencyTest.java)
are the executable checks; their presence alone does not establish a passing
run or live-provider verification.

## Missing-document recovery

A filename, submission timestamp or storage key proves only that metadata
exists. Seed references, including rows labelled `s3`, do not prove that a real
object was uploaded or remains downloadable. Neither a UI repair nor a
provider switch can reconstruct bytes lost with an ephemeral filesystem.

Before changing a damaged report, retain a private backup and inspect its row
(`file_data`, provider, bucket, key and checksum) together with any retained
volume, bucket object, export or original uploaded artifact. Compare the
available bytes with the stored checksum where present. Keep backups and
report contents outside public repository artifacts and logs.

Restore only a verified matching artifact through an authorized, scoped
recovery. Do not delete report rows, clear their metadata, disable integrity
checks or restore the entire database merely to hide a failed download: those
actions can destroy surviving evidence or overwrite newer submissions. If no
matching bytes remain, record the report as unrecovered and obtain the
original document from its owner. A successful metadata response is not
recovery evidence; verify the authorized download and the returned bytes.

The database migration and local H2 twin are deliberately separate from any
Supabase hosted migration. Applying hosted storage configuration remains a
future release-authorized operation.
