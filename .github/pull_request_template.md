## Summary

Describe the problem, the change, and any setup or migration impact.

## Verification

Follow [CONTRIBUTING.md](../CONTRIBUTING.md). Record commands and results below;
mark unavailable or inapplicable checks with a reason.

- [ ] Web changes: `npm test --prefix frontend`, `npm run typecheck --prefix frontend`, `npm run lint --prefix frontend`, `npm run build --prefix frontend`.
- [ ] Mobile changes: `npm test --prefix mobile`, `npm run typecheck --prefix mobile`.
- [ ] Java changes: `mvn -f java-services/pom.xml verify` with Java 21.
- [ ] Affected PostgreSQL race/RLS and Playwright checks follow the CI jobs using disposable infrastructure.
- [ ] `node scripts/check-migrations.mjs`.
- [ ] `node scripts/check-assistant-secrets.mjs`.
- [ ] `node scripts/check-doc-hygiene.mjs` and `git diff --check`.
- [ ] Changed relative links/images/anchors checked; changed issue-form YAML parsed and schema reviewed.
- [ ] Documentation updated for changed behavior, commands, or setup.
- [ ] No secrets, access tokens, personal/student records, or unredacted logs included.

Results (PASS / FAIL / NOT_RUN / BLOCKED_CAPABILITY, with reasons):

<!-- Security vulnerabilities belong in the private channel described in SECURITY.md. -->
