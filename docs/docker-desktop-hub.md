# Docker Desktop Hub lookup

Do not guess the Hub namespace from GitHub (`jasontm17`). Read the signed-in Docker Desktop account.

## Find the username

1. Confirm Docker Desktop is running and signed in.
2. Open `%APPDATA%\Docker\login-info.json`.
3. Field `0` is Base64 JSON. Decode it and use `Username`.
4. Never commit that file. It is machine-local login state.

On this machine the Hub user is `nguyenson1710`.

## Publish images manually (fallback)

```powershell
docker tag campuscore-frontend:local nguyenson1710/campuscore-frontend:<version>
docker push nguyenson1710/campuscore-frontend:<version>
docker manifest inspect docker.io/nguyenson1710/campuscore-frontend:<version>
```

The supported release path is the `Publish container images` workflow. It
builds and pushes the four images from the same commit to Docker Hub and GHCR:

| Component | Docker Hub | GHCR |
| --- | --- | --- |
| Database | `nguyenson1710/campuscore-database` | `ghcr.io/jasontm17/student-management-ute-database` |
| REST API | `nguyenson1710/campuscore-restful-api` | `ghcr.io/jasontm17/student-management-ute-restful-api` |
| RAG service | `nguyenson1710/campuscore-rag-service` | `ghcr.io/jasontm17/student-management-ute-rag-service` |
| Frontend | `nguyenson1710/campuscore-frontend` | `ghcr.io/jasontm17/student-management-ute-frontend` |

The workflow needs repository secrets `DOCKERHUB_USERNAME` and
`DOCKERHUB_TOKEN`; it never stores credentials in the repository. It publishes
an immutable short-SHA tag, refuses to overwrite an existing or mismatched
tag, emits BuildKit provenance and SBOM attestations, and only moves `latest`
on the default branch after GHCR/Docker Hub digest parity is verified.

For a no-build smoke, set `CAMPUSCORE_IMAGE_TAG` to the reviewed short SHA so
all services resolve the same immutable release across registries:

```powershell
$env:CAMPUSCORE_IMAGE_TAG = "<short-sha>"
docker compose -f docker-compose.yml -f docker-compose.rag.override.yml up -d --no-build
```

GitHub Packages stays separate:

```powershell
gh auth token | docker login ghcr.io -u JasonTM17 --password-stdin
docker tag campuscore-frontend:local ghcr.io/jasontm17/student-management-ute-frontend:<version>
docker push ghcr.io/jasontm17/student-management-ute-frontend:<version>
```

The GHCR workflow publishes the same course stack as four artifacts. The
database artifact is a migration-free PostgreSQL runtime wrapper; Flyway in
the REST API still owns schema and seed data.

```powershell
docker tag campuscore-database:local ghcr.io/jasontm17/student-management-ute-database:<version>
docker push ghcr.io/jasontm17/student-management-ute-database:<version>
```
