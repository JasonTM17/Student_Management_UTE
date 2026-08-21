# Analytics PostgreSQL focused rehearsal — 2026-08-21

- Exact head: `2a56a2dd85c218ed7e355ac57c23a196b91d5127`.
- Disposable PostgreSQL target: `127.0.0.1:56471`.
- Database: `postgres` with `currentSchema=public`.
- Run:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest' '-DforkCount=0' test`
- Result: PASS.
- Surefire summary: `17 tests / 0 failures / 0 errors / 0 skipped`.
- Behavior covered: overview, finance summary, revenue, attendance, lecturer,
  enrollment trends, operator summary, cockpit, section occupancy,
  registration pressure, top courses, student statistics, grade distribution
  and notification summary on real PostgreSQL syntax/types.
- Limitation: focused PostgreSQL parity only; no restored legacy dataset,
  route canary, rollback or public traffic handoff proof.
