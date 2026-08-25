-- Deliberately creates no application object.  The safety regression uses the
-- versionless Flyway history row itself as the incompatible pre-existing state.
SELECT 1;
