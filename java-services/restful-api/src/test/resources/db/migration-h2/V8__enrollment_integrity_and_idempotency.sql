CREATE INDEX IF NOT EXISTS enrollment_operation_state_idx
    ON academic."EnrollmentOperation" ("studentId", "state", "updatedAt");
CREATE INDEX IF NOT EXISTS enrollment_audit_student_time_idx
    ON academic."EnrollmentAudit" ("studentId", "createdAt");
