-- V132: restore the Section capacity invariant on seeded demo data.
--
-- RegistrationService enforces "enrolledCount" < "capacity" atomically at
-- enroll time, so a section whose enrolledCount already exceeds capacity can
-- never be produced through the API. Five mass-seeded demo sections
-- (section-auto-014/017/018/020/021) were generated with room-sized
-- capacities (32..56) while a separate seed stuffed 120..135 ENROLLED rows
-- into them. The catalog therefore renders impossible states such as
-- "129/35".
--
-- The narrow repair preserves every enrollment and bumps capacity to the
-- observed occupancy rounded up to a plausible class-size headroom, keeping
-- capacity >= enrolledCount + spare seats.

UPDATE academic."Section"
SET "capacity" = ((("enrolledCount" + 24) / 25) * 25),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "enrolledCount" > "capacity";
