---
title: "Phase 6: Runtime consolidation and documentation"
status: completed
---

# Phase 6: Runtime consolidation and documentation

## Objective

Make the single-API runtime and operational contract obvious without deleting
historical or user-owned infrastructure artifacts.

## Steps

Scan source/config/Compose/CI for runtime references to old services, gateways,
Redis, RabbitMQ, MinIO, Nginx and Kubernetes. Inventory ignored compose/nginx
files and Docker containers/images/volumes with exact ownership; defer physical
cleanup unless separately authorized. Update architecture, migration/JPA,
API/OpenAPI, privacy/retention, operator and rollback documentation. Correct
CI Flyway assertions to the actual latest migration.

## Exit criterion

Only Java API + PostgreSQL are declared in the active runtime; docs and OpenAPI
match the implementation; secret/encoding/docs/Compose scans and `git diff
--check` pass; no destructive cleanup occurred implicitly.
