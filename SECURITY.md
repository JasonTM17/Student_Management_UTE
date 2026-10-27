# Security Policy

## Supported versions

Security fixes are applied to `main` and released as a new patch version. Only the latest release is supported.

| Version | Supported |
| --- | --- |
| Latest `v1.1.x` and `main` | Yes |
| Older releases | No |

## Reporting a vulnerability

Report privately through GitHub: **Security** tab → **Report a vulnerability**
([direct link](https://github.com/JasonTM17/Student_Management_UTE/security/advisories/new)).

Do not open a public issue or pull request for a vulnerability, and do not include real personal data, credentials or tokens in a report.

Please include:

- affected component (API, web portal, mobile app, database, container image) and version or commit;
- steps to reproduce and the impact you observed;
- a suggested fix, if you have one.

You can expect an acknowledgement within 7 days. Confirmed issues are fixed on `main`, released as a patch version, and credited in the advisory unless you ask otherwise.

## Scope

In scope: this repository's source, the published container images, and the deployed demo (`campusute.io.vn`).

Out of scope: denial-of-service or volumetric testing, social engineering, automated scanning that generates high request volume, and testing with real student or staff accounts. Use only data you create yourself, and never access or modify other people's data.

## How the project handles security

- Authorization rules: [`docs/security/authorization-model.md`](docs/security/authorization-model.md).
- Secrets live only in environment variables and are never committed; `.env.example` lists the names.
- Every release image is scanned with Trivy for fixable HIGH and CRITICAL vulnerabilities before it is accepted, and carries provenance and SBOM attestations.
