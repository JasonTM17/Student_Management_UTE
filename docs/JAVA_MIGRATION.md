# Java Migration Boundary

CampusCore currently runs NestJS/TypeScript services. The Java migration uses a
strangler path: the NestJS stack remains the canonical writer until a Spring
service has passed contract, data, authorization, observability, and rollback
gates.

## First boundary

`java-services/thesis-service` is an isolated Spring Boot service. It is not yet
a replacement for `academic-service` and must not receive public traffic until
the gateway and deployment checks are added in a later change. Its tables use a
dedicated `thesis` schema so legacy Prisma `db push` cannot own or remove them.

The service targets Java 21, uses Flyway for versioned SQL migrations, validates
existing access-token JWTs with `JWT_SECRET`, accepts the legacy access-token
cookie or bearer header, and applies the existing cookie CSRF contract to
mutating requests.

## Migration rules

1. Keep one canonical writer for each migrated domain.
2. Do not use `ddl-auto=update`, `prisma db push`, or destructive migrations for
   production data.
3. Compare status, body, headers, cookies, errors, authorization, and event
   behavior before gateway cutover.
4. Use expand/contract migrations and a tested restore path before a write
   handover.
5. Keep provider keys in the runtime secret manager. Never commit `.env` or a
   real LLM key.

## Local verification

```text
mvn -f java-services/pom.xml test
mvn -f java-services/pom.xml verify
```

The current checkout does not claim a production cutover. Existing Node tests
remain required while the old services are still canonical.
