# CampusCore production package runbook

This package is deployment-ready only after the external VPS, DNS, firewall,
TLS and secret-provisioning gates below are completed. A green local/CI check
does not mean that CampusCore is running in production.

## Preflight

1. Install Docker Engine/Compose on the VPS and allow inbound TCP `80` and
   `443` only. Keep PostgreSQL, REST, RAG and web ports private.
2. Point the domain in `.env.production` at the VPS and wait for DNS
   propagation. Set `ACME_EMAIL` and a real domain; placeholders must fail.
3. Pull the exact reviewed full SHA in `CAMPUSCORE_IMAGE_TAG` and verify all
   four registry digests match the release manifest.
4. Provision `ops/secrets/*` with mode `0600`; rotate any key ever pasted into
   chat or committed history. Keep DeepSeek disabled until a newly rotated key
   has been loaded and a live smoke is explicitly approved.
5. Generate the signing secrets with `openssl rand -base64 48` — one value each
   for `ops/secrets/jwt_secret` and `ops/secrets/jwt_refresh_secret`. They must
   differ from each other and from every value committed to this repository.
   `docker-compose.prod.yml` sets `SECURITY_JWT_REJECT_KNOWN_DEFAULTS=true`, so
   the API aborts startup when it detects a secret published here.
6. Confirm a recent PostgreSQL backup and test its checksum before startup.

## Start and smoke

```sh
docker compose --env-file .env.production -f docker-compose.prod.yml pull
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --wait
curl --fail --silent --show-error https://$CAMPUSCORE_DOMAIN/health
curl --fail --silent --show-error https://$CAMPUSCORE_DOMAIN/api/v1/health/liveness
curl --fail --silent --show-error -H "X-Health-Key: $(cat ops/secrets/readiness_key)" \
  https://$CAMPUSCORE_DOMAIN/api/v1/health/readiness
```

Then exercise login, one representative student/lecturer/admin route, campus
assistant JSON, assistant SSE event order, citation rendering and logout from
an external browser. Verify that `/internal/rag/*`, PostgreSQL and service
ports are not reachable from the public interface.

## Rollback

Stop Caddy traffic, set the previous verified full SHA, pull and restart the
same four images, then repeat the smoke. Never retag or pull `latest`. If a
database migration is involved, restore an isolated archive first and obtain a
recovery decision; do not run `docker compose down -v`.

## Provider and data boundaries

Supabase service-role and DeepSeek keys are server-only runtime files. The
browser receives neither key, raw provider error, model identifier nor private
campus records. If the provider is unavailable, the assistant must return a
cited lexical answer with a degraded product status.

## Render cold-start mitigation

The GitHub Actions heartbeat (`keepalive-heartbeat.yml`) probes Render, Supabase
and Vercel every 6 hours with three bounded retries; a target that stays
unreachable after all attempts fails the workflow visibly instead of logging a
fake success. Render's free tier still sleeps between runs, so two extra layers
cover the gap:

1. The frontend axios layer retries idempotent GETs once (3s backoff) when the
   gateway answers 502/503/504, which absorbs single cold-start requests.
2. `supabase/migrations/20260920210000_render_keepalive_warm_ping.sql` schedules
   a 10-minute in-database ping via `pg_cron` + `pg_net`. It is a no-op unless
   both extensions are enabled (Dashboard -> Database -> Extensions), then
   re-apply the migration to register the job. Verify with
   `SELECT jobname, schedule FROM cron.job;`.

A paid Render instance removes the cold start entirely; that is a cost decision
tracked outside this runbook.
