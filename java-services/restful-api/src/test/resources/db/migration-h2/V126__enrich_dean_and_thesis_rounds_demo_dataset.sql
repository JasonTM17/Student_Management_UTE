-- H2 twin of V126__enrich_dean_and_thesis_rounds_demo_dataset.sql.
-- The reduced H2 test migration chain does not include the full PostgreSQL
-- demo population migrations (V26/V33/V43/V52/V74) or the academic section
-- schedule/gradebook tables, and thesis persistence tests wipe and seed their
-- own isolated rounds in @BeforeEach. Keep the version registered in H2 so
-- the migration numbering stays aligned with production.
SELECT 1;
