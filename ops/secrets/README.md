# Runtime secrets (production)

Create these files on the VPS with mode `0600`, owned by the deployment user,
before starting the production Compose project. The filenames are deliberately
not tracked; the Compose file mounts each file read-only and the Spring
bootstrap fails closed when a required file is missing or blank.

Required files:

- `postgres_password`
- `jwt_secret`
- `jwt_refresh_secret`
- `readiness_key`
- `rag_service_token`
- `supabase_service_role_key` (required when `ASSISTANT_SUPABASE_ENABLED=true`)
- `deepseek_api_key` (required only when `DEEPSEEK_ENABLED=true`)

Use a newly rotated provider key. A key pasted into a chat, ticket, shell
history, image, or Git repository is considered compromised and must not be
reused. Do not mount these files into the web or Caddy containers.

Example provisioning (run on the VPS, never commit the values):

```sh
install -d -m 700 ops/secrets
umask 077
openssl rand -base64 48 > ops/secrets/jwt_secret
openssl rand -base64 48 > ops/secrets/jwt_refresh_secret
openssl rand -base64 32 > ops/secrets/readiness_key
openssl rand -base64 32 > ops/secrets/rag_service_token
printf '%s\n' 'replace-with-a-strong-database-password' > ops/secrets/postgres_password
```

Keep provider and Supabase values in the same files only on the target host;
the release workflow never receives them.
