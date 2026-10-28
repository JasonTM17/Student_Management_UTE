# Contributing to CampusCore

Thank you for your interest in contributing to CampusCore! This document outlines the contribution workflow, coding standards, and verification requirements.

## Code of Conduct

Maintain professional communication, respect differences, and prioritize factual, evidence-based reviews.

## Git & Commit Guidelines

1. **Sole Author Rule**: The repository maintainer is the sole commit author. Do not append `Co-authored-by`, `Generated with`, or automated agent/tool attribution trailers to commit messages.
2. **Conventional Commits**: Commit messages must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:
   - `feat(...)`: A new user-facing feature.
   - `fix(...)`: A bug fix or security patch.
   - `docs(...)`: Documentation changes only.
   - `chore(...)`: Dependency updates, tooling, or build adjustments.
   - `test(...)`: Adding or refactoring tests.
   - `refactor(...)`: Code change that neither fixes a bug nor adds a feature.
3. **Branch Naming**: Use intent-based branch names such as `feature/...`, `fix/...`, or `chore/...`.

## Verification Gates Before Submitting

Use Java 21 and Node 20 to match [CI](.github/workflows/ci.yml). Install client dependencies with `npm ci --prefix frontend` and `npm ci --prefix mobile`. Run the applicable gates below from the **repository root**, and report skipped or unavailable checks with a reason. Documentation-only changes require the hygiene/whitespace gates and validation of changed relative links; product changes require the affected client/backend gates too.

### 1. Frontend Web (`frontend/`)
```bash
npm test --prefix frontend
npm run typecheck --prefix frontend
npm run lint --prefix frontend             # Zero warnings allowed
npm run build --prefix frontend
```

### 2. Mobile App (`mobile/`)
```bash
npm test --prefix mobile
npm run typecheck --prefix mobile
```

### 3. Backend Java (`java-services/`)
```bash
mvn -f java-services/pom.xml verify
```

The PostgreSQL race/RLS and Playwright lanes need disposable infrastructure and suite-specific configuration. Follow the named jobs in [CI](.github/workflows/ci.yml); never point mutation suites at production. A compile alone is not backend test evidence.

### 4. Database, Secret & Documentation Hygiene
```bash
node scripts/check-migrations.mjs          # Flyway migration name and order check
node scripts/check-assistant-secrets.mjs   # Secret scan across repository files
node scripts/check-doc-hygiene.mjs         # Documentation encoding check
git diff --check
```

Check every changed relative link/image target (including Markdown anchors). Parse changed issue-form YAML with a YAML parser and review it against GitHub's issue-form schema. Update documentation when behavior, commands or setup change. Never include secrets, access tokens, personal/student records or unredacted logs in issues or pull requests; use the [security policy](SECURITY.md) for private reports.

## Pull Request Process

1. Fork or branch from `main`.
2. Ensure your changes are focused and minimal.
3. Run the applicable verification checks listed above and record their results; distinguish PASS, FAIL, NOT_RUN and BLOCKED_CAPABILITY.
4. Open a Pull Request with a clear summary of changes, motivation, and verification evidence observed.
