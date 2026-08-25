# Authentication mail and templates

CampusCore owns student email verification and password reset in the Java API.
Self-registration returns HTTP `202` without access/refresh tokens or session
cookies. A user signs in only after the verification challenge has been
consumed; password reset revokes every refresh session.

## Local Mailpit

Copy `.env.example` to the ignored `.env` file, keep the Mailpit defaults and
start the local services:

```powershell
docker compose up -d --build postgres mailpit restful-api
```

Mailpit receives SMTP on `127.0.0.1:1025` and exposes its local UI at
`http://127.0.0.1:8025`. If the Java API runs directly on the host, set
`SPRING_MAIL_HOST=localhost`; inside Compose it must remain `mailpit`.

## External SMTP

Inject these values in the server process or deployment secret store:

```text
MAIL_ENABLED=true
SPRING_MAIL_HOST=<smtp-host>
SPRING_MAIL_PORT=<smtp-port>
SPRING_MAIL_USERNAME=<smtp-username>
SPRING_MAIL_PASSWORD=<smtp-password>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
MAIL_FROM=<verified-sender-address>
AUTH_FRONTEND_BASE_URL=https://<trusted-web-origin>
```

Do not put real credentials in `.env.example`, frontend/mobile variables,
screenshots, logs or committed evidence. `AUTH_FRONTEND_BASE_URL` must be a
trusted application origin because it becomes the base of verification and
reset links. Raw challenges are placed in the URL fragment (`#token=...`),
which is never sent to the web server or reverse proxy; the page reads it once
and immediately removes it with `history.replaceState`.

## Template inventory

The classpath templates are:

- `templates/mail/vi/verify-email.html` and `.txt`
- `templates/mail/vi/reset-password.html` and `.txt`
- `templates/mail/en/verify-email.html` and `.txt`
- `templates/mail/en/reset-password.html` and `.txt`

Resend reuses the verification template. The renderer accepts only its typed
allowlist, HTML-escapes dynamic values, includes the challenge TTL and safety
warning, and sends multipart HTML plus plain-text fallback.

## Security and delivery invariants

- Verification challenges live for 24 hours; password-reset challenges live
  for 30 minutes by default.
- Challenges are single-use, store only SHA-256 hashes, allow at most five bad
  attempts and are replaced when a permitted resend creates a new challenge.
- Resend/forgot responses are generic. Default throttling is 60 seconds and at
  most five requests per 24 hours for hashed email and IP buckets.
- Mail is dispatched after the database transaction commits. SMTP failure does
  not roll back account creation; resend is the recovery path.
- Raw challenge values must never be logged or persisted. Access/refresh
  tokens must never appear in email links.

The limits can be overridden with the `AUTH_*` placeholders documented in
`.env.example`. Any change should retain the single-use, enumeration-safe and
session-revocation regression tests.

## Local acceptance flow

1. Register a unique student and assert HTTP `202` with no session cookie.
2. Capture both HTML and text verification bodies in Mailpit.
3. Confirm the challenge once, reject its replay, then log in.
4. Rotate refresh, log out and prove the old refresh session is revoked.
5. Request reset with both known and unknown email addresses and compare the
   generic public response.
6. Capture reset mail, reset once, reject replay, reject the old password and
   log in with the new password.
7. Query only challenge counts/hash lengths/consumed flags; never print token
   values in test output.
