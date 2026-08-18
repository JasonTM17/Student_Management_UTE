# CampusCore Java Auth Service

This is a shadow compatibility service, not the current auth writer. It
validates existing CampusCore access tokens and exposes the read-only
`GET /api/v1/auth/me` contract against the legacy `auth` schema.

It intentionally does not issue, refresh, revoke, or persist sessions yet.
Those stateful paths need differential contract coverage and a tested rollback
plan before traffic can move from the NestJS canonical service.
