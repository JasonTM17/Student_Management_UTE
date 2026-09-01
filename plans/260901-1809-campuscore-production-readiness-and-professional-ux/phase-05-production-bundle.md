# Phase 05 — Production bundle and CI

Add a production Compose overlay with Caddy as the only public service, private networks, no Mailpit, health/readiness, resource/log limits, non-root/hardening controls, persistent PostgreSQL and digest-selected image variables. Add portable secret-file examples, backup/restore scripts, checksums, retention and rollback runbooks plus VPS/domain cutover instructions.

Change publication to an explicit, CI-verified dispatch for an exact main SHA. Build the four first-party images once, publish to Docker Hub and GHCR, verify manifest digest parity, OCI revision, SBOM, provenance and vulnerability results, and never auto-promote `latest`. Exit criterion: local config, fresh isolated image stack, Caddy validation and backup/restore drill pass.
