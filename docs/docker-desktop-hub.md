# Docker Desktop Hub lookup

Do not guess the Hub namespace from GitHub (`jasontm17`). Read the signed-in Docker Desktop account.

## Find the username

1. Confirm Docker Desktop is running and signed in.
2. Open `%APPDATA%\Docker\login-info.json`.
3. Field `0` is Base64 JSON. Decode it and use `Username`.
4. Never commit that file. It is machine-local login state.

On this machine the Hub user is `nguyenson1710`.

## Publish a frontend image

```powershell
docker tag campuscore-frontend:local nguyenson1710/campuscore-frontend:<version>
docker push nguyenson1710/campuscore-frontend:<version>
docker manifest inspect docker.io/nguyenson1710/campuscore-frontend:<version>
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
