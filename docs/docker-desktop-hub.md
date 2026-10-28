# Docker Desktop Hub lookup

The release workflow fixes Docker Hub to `docker.io/nguyenson1710` and GHCR to
`ghcr.io/jasontm17`. A signed-in local account is not the authority for these
repository release destinations.

## Find the username

Use Docker Desktop's account menu to inspect the active local account.
Do not read, decode or commit local authentication files. Account-file formats
are implementation details and are not part of this repository's contract.

## Manual tag examples (not the verified release path)

```powershell
docker tag student-management-ute-frontend:local nguyenson1710/student-management-ute-frontend:<full-commit-sha>
docker push nguyenson1710/student-management-ute-frontend:<full-commit-sha>
docker manifest inspect docker.io/nguyenson1710/student-management-ute-frontend:<full-commit-sha>
```

The supported release path is the `Publish container images` workflow. It
builds and pushes the four images from the same commit to Docker Hub and GHCR:

| Component | Docker Hub | GHCR |
| --- | --- | --- |
| Database | `nguyenson1710/student-management-ute-database` | `ghcr.io/jasontm17/student-management-ute-database` |
| REST API | `nguyenson1710/student-management-ute-restful-api` | `ghcr.io/jasontm17/student-management-ute-restful-api` |
| RAG service | `nguyenson1710/student-management-ute-rag-service` | `ghcr.io/jasontm17/student-management-ute-rag-service` |
| Frontend | `nguyenson1710/student-management-ute-frontend` | `ghcr.io/jasontm17/student-management-ute-frontend` |

The workflow needs repository secrets `DOCKERHUB_USERNAME` and
`DOCKERHUB_TOKEN`; it never stores credentials in the repository. It publishes
an immutable full-commit-SHA tag, refuses to overwrite an existing or mismatched
tag, emits BuildKit provenance and SBOM attestations, and binds each image to
the full source commit without a non-atomic cross-registry `latest` move.
The workflow is manually dispatched with the exact current main SHA and an
independent review confirmation. It checks successful CI before publication,
then verifies registry parity, source revision and attestation manifests.
Manual pushes below do not reproduce those gates or the release manifest;
use the workflow for an accepted release and do not overwrite an existing tag.

For a no-build smoke, set `CAMPUSCORE_IMAGE_TAG` to the reviewed full commit SHA so
all services resolve the same immutable release across registries:

```powershell
$env:CAMPUSCORE_IMAGE_TAG = "<full-commit-sha>"
docker compose -f docker-compose.yml -f docker-compose.rag.override.yml up -d --no-build
```

GitHub Packages stays separate:

```powershell
gh auth token | docker login ghcr.io -u JasonTM17 --password-stdin
docker tag student-management-ute-frontend:local ghcr.io/jasontm17/student-management-ute-frontend:<full-commit-sha>
docker push ghcr.io/jasontm17/student-management-ute-frontend:<full-commit-sha>
```

The GHCR workflow publishes the same course stack as four artifacts. The
database artifact is a migration-free PostgreSQL runtime wrapper; Flyway in
Java owns schema and seed data: `rag-service` runs Flyway in Compose, while
the standalone Render API runs Flyway itself. See [release identity and the
history rewrite](RELEASE.md#history-rewrite-note) before choosing older tags.

```powershell
docker tag student-management-ute-database:local ghcr.io/jasontm17/student-management-ute-database:<full-commit-sha>
docker push ghcr.io/jasontm17/student-management-ute-database:<full-commit-sha>
```
