# Security Policy

## Supported Versions

Only the latest release on the default branch receives security updates and vulnerability patches.

| Version | Supported          |
| ------- | ------------------ |
| 1.1.x   | :white_check_mark: |
| 1.0.x   | :x:                |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of CampusCore seriously. If you discover a security vulnerability or sensitive information exposure, please report it privately.

**Do NOT report security vulnerabilities through public GitHub issues or pull requests.**

### How to Report

Please email the project maintainer directly:
- **Contact:** Nguyen Tien Son
- **Email:** `jasonbmt06@gmail.com`

Include the following details in your report:
1. Type of issue (e.g., SQL injection, authentication bypass, CSRF, privilege escalation, secret leak).
2. Step-by-step instructions or proof-of-concept to reproduce the behavior.
3. Affected components (backend service, web portal, mobile app, or deployment configuration).
4. Proposed mitigation or patch if available.

### Response Timeline

- **Initial Response:** Within 48 hours acknowledging receipt of the report.
- **Triage & Assessment:** Within 5 business days confirming validity and severity.
- **Fix & Disclosure:** Security fixes will be released promptly, followed by a public advisory after affected deployments are patched.

## Credential & Secret Management

- Secrets, private keys, database passwords, and third-party tokens must **never** be committed to the repository.
- Follow the runtime provisioning instructions in [`ops/secrets/README.md`](ops/secrets/README.md).
- Automated CI scans (`node scripts/check-assistant-secrets.mjs` and GitLeaks) run before releases to reject accidental credential exposure.
