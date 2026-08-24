# Backend/database scout evidence

Source: read-only Wukong/scout agents on the pre-execution dirty boundary. This
is a gap inventory, not a candidate PASS.

- Clean HEAD contains one Maven REST API module; no tracked runtime sibling
  microservice is part of the target architecture.
- Existing persistence is predominantly JDBC; JPA dependencies are present but
  typed entity/repository boundaries are not yet established.
- Enrollment needs first-class rounds/windows/eligibility, idempotency,
  capacity and owner-safe locking, schedule/prerequisite checks, audit/history,
  and PDF slip hashing.
- Ignored Compose/E2E/Nginx and Docker objects require inventory only; they are
  protected from implicit deletion.
