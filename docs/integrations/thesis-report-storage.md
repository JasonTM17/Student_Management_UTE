# Thesis report object storage

Thesis report uploads use a provider-neutral server boundary. The browser sends
the document to the Java API; it never receives a storage service key and never
writes directly to Supabase Storage.

## Local preview (default)

The default provider is `local`. Docker mounts a named volume at
`/var/lib/campuscore/thesis-reports`, so a restart of the API does not put the
document bytes back into PostgreSQL or discard them with the container.

```text
THESIS_REPORT_STORAGE_PROVIDER=local
THESIS_REPORT_STORAGE_BUCKET=thesis-reports
THESIS_REPORT_STORAGE_LOCAL_ROOT=/var/lib/campuscore/thesis-reports
```

`V46__thesis_report_object_storage.sql` keeps only provider, bucket, key, size,
type, and SHA-256 metadata in `thesis.thesis_group_report`. V44 `file_data`
remains readable for legacy rows; new uploads keep it `NULL`.

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

## Rendering and lifecycle

The thesis page renders the stored filename, MIME type, size, submission time,
and an authorized download action. Replacing a report writes a new server-
generated key first, commits the metadata, then best-effort removes the old
object. A checksum mismatch or unavailable provider returns a generic
`REPORT_STORAGE_UNAVAILABLE` response without exposing provider details.

The database migration and local H2 twin are deliberately separate from any
Supabase hosted migration. Applying hosted storage configuration remains a
future release-authorized operation.
