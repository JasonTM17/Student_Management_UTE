# Contributing

Thanks for helping improve CampusCore. This guide covers the checks a change must pass.

## Set up

Prerequisites: Docker Desktop 24+, JDK 21, Node.js 20, Maven 3.9.

```bash
git clone https://github.com/JasonTM17/Student_Management_UTE.git
cd Student_Management_UTE
cp .env.example .env            # review the values; never commit .env
docker compose up -d --build postgres mailpit rag-service restful-api web
```

The full local stack, ports and demo accounts are described in [`README.en.md`](README.en.md).

## Layout

| Path | Contents |
| --- | --- |
| `java-services/` | Spring Boot REST API and RAG sidecar (Maven, Flyway migrations) |
| `frontend/` | Next.js web portal |
| `mobile/` | Expo mobile app |
| `database/`, `supabase/` | Database image and seed data |
| `docs/` | Architecture, runbooks, release and security docs |

## Checks to run before opening a pull request

Run the ones that match what you changed. CI runs all of them.

```bash
# API
mvn -q -f java-services/pom.xml verify

# Web portal (from frontend/)
npm ci && npm test && npm run typecheck && npm run lint && npm run build

# Mobile (from mobile/)
npm ci && npm test && npm run typecheck

# Repository checks
node scripts/check-migrations.mjs
node scripts/check-doc-hygiene.mjs
docker compose config
```

Database changes go in a new Flyway migration under `java-services/restful-api/src/main/resources/db/migration`. Never edit a migration that has been merged.

## Pull requests

- Branch from `main` using an intent-based name such as `feature/...` or `fix/...`.
- Use [Conventional Commits](https://www.conventionalcommits.org/) (`feat(auth): ...`, `fix(mail): ...`, `docs: ...`). Keep each commit to one coherent change.
- Add the smallest test that fails without your change and passes with it. Do not weaken or skip existing tests to make a build pass.
- Update documentation when behavior, setup, commands or contracts change.
- Never commit secrets, `.env` files, real personal data or generated artifacts.
- Open the pull request against `main`; it can merge once CI is green.

## Reporting problems

Open an issue for bugs and ideas. For security vulnerabilities follow [`SECURITY.md`](SECURITY.md) instead of opening a public issue.
