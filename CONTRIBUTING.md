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

All contributions must pass local verification gates before opening a pull request:

### 1. Frontend Web (`frontend/`)
```bash
cd frontend
npm test                 # Unit and contract tests (must pass with 0 failures)
npm run typecheck        # TypeScript static verification
npm run lint             # ESLint with zero warnings allowed
```

### 2. Mobile App (`mobile/`)
```bash
cd mobile
npm test                 # Mobile unit tests
npm run typecheck        # React Native TypeScript check
```

### 3. Backend Java (`java-services/`)
```bash
cd java-services
mvn test-compile         # Java compilation check
```

### 4. Database & Secret Hygiene
```bash
node scripts/check-migrations.mjs          # Flyway migration name and order check
node scripts/check-assistant-secrets.mjs   # Secret scan across repository files
node scripts/check-doc-hygiene.mjs         # Documentation encoding check
```

## Pull Request Process

1. Fork or branch from `main`.
2. Ensure your changes are focused and minimal.
3. Run all verification checks listed above.
4. Open a Pull Request with a clear summary of changes, motivation, and verification evidence observed.
