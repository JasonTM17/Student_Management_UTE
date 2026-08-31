# CampusCore database runtime image.
#
# The application owns schema creation and seed data through Flyway. Keep this
# image migration-free so a fresh container has the same lifecycle as the
# local Compose PostgreSQL service and never receives application credentials.
FROM postgres:15-alpine@sha256:fe0737ba566a2c5b2a28f34433c0a423261900ec17b9bf7ad115e1aae7e57f1b

LABEL org.opencontainers.image.title="CampusCore PostgreSQL runtime" \
      org.opencontainers.image.description="PostgreSQL 15 runtime; schema and seed data are managed by Flyway" \
      org.opencontainers.image.vendor="CampusCore"
