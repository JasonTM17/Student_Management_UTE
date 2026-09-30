-- H2 twin of V89 carries the R1 sweep only: no account holding an
-- administrative office role (ADMIN/SUPER_ADMIN) may also hold TRUONG_KHOA.
-- The reduced H2 migration chain does not model academic."Lecturer" (the
-- unification of V8 and the lecturer seeds are PostgreSQL-chain artifacts),
-- so the lecturer-profile parts of this migration have no target here and the
-- role separation is the piece the H2 auth tests can observe.
-- When 'TRUONG_KHOA' is absent the subquery yields NULL and the DELETE is a
-- no-op, so the statement stays safe on any chain state.
DELETE FROM campuscore_auth."UserRole"
WHERE "roleId" = (SELECT "id" FROM campuscore_auth."Role" WHERE "name" = 'TRUONG_KHOA')
  AND "userId" IN (
      SELECT held."userId"
      FROM campuscore_auth."UserRole" held
      JOIN campuscore_auth."Role" office ON office."id" = held."roleId"
      WHERE office."name" IN ('ADMIN', 'SUPER_ADMIN')
  );
