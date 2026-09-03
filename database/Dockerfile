# CampusCore database runtime image.
#
# The application owns schema creation and seed data through Flyway. Keep this
# image migration-free so a fresh container has the same lifecycle as the
# local Compose PostgreSQL service and never receives application credentials.
FROM postgres:15-alpine@sha256:fe0737ba566a2c5b2a28f34433c0a423261900ec17b9bf7ad115e1aae7e57f1b AS base

RUN apk update && \
    apk upgrade --no-cache libcrypto3 libssl3 && \
    apk add --no-cache su-exec && \
    rm -f /usr/local/bin/gosu && \
    ln -s /sbin/su-exec /usr/local/bin/gosu && \
    rm -rf /var/cache/apk/*

FROM scratch
COPY --from=base / /

ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    GOSU_VERSION=1.19 \
    LANG=en_US.utf8 \
    PG_MAJOR=15 \
    PG_VERSION=15.19 \
    PG_SHA256=e1a64a87a46b825b88c082e4518161a47aab53c45694964f8ba1df28f7859f89 \
    PGDATA=/var/lib/postgresql/data

LABEL org.opencontainers.image.title="CampusCore PostgreSQL runtime" \
      org.opencontainers.image.description="PostgreSQL 15 runtime; schema and seed data are managed by Flyway" \
      org.opencontainers.image.vendor="CampusCore"

VOLUME ["/var/lib/postgresql/data"]
EXPOSE 5432
STOPSIGNAL SIGINT
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["postgres"]
