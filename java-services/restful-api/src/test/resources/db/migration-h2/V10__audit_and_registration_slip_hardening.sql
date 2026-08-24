CREATE INDEX IF NOT EXISTS registration_slip_student_time_idx
    ON academic."RegistrationSlip" ("studentId", "generatedAt");
CREATE INDEX IF NOT EXISTS enrollment_operation_key_lookup_idx
    ON academic."EnrollmentOperation" ("studentId", "idempotencyKey", "canonicalRequestHash");
