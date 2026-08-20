CREATE SCHEMA IF NOT EXISTS people;

CREATE TABLE IF NOT EXISTS people.student (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL UNIQUE,
    email            VARCHAR(255) NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    student_id       VARCHAR(50) NOT NULL UNIQUE,
    curriculum_id    UUID NOT NULL,
    curriculum_code  VARCHAR(50),
    curriculum_name  VARCHAR(200),
    department_id    UUID,
    department_code  VARCHAR(50),
    department_name  VARCHAR(200),
    study_year       INTEGER NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    admission_date   TIMESTAMP NOT NULL,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_student_student_id ON people.student (student_id);
CREATE INDEX IF NOT EXISTS idx_student_user_id ON people.student (user_id);
CREATE INDEX IF NOT EXISTS idx_student_status ON people.student (status);
CREATE INDEX IF NOT EXISTS idx_student_curriculum ON people.student (curriculum_id);

CREATE TABLE IF NOT EXISTS people.lecturer (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL UNIQUE,
    email             VARCHAR(255) NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    department_id     UUID NOT NULL,
    department_code   VARCHAR(50),
    department_name   VARCHAR(200),
    employee_id       VARCHAR(50) NOT NULL UNIQUE,
    title             VARCHAR(100),
    specialization    VARCHAR(200),
    office            VARCHAR(200),
    phone             VARCHAR(30),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_lecturer_employee_id ON people.lecturer (employee_id);
CREATE INDEX IF NOT EXISTS idx_lecturer_user_id ON people.lecturer (user_id);
CREATE INDEX IF NOT EXISTS idx_lecturer_department ON people.lecturer (department_id);
