CREATE SCHEMA IF NOT EXISTS academic;

CREATE TABLE IF NOT EXISTS academic.faculty (
    id                UUID PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    name_en           VARCHAR(200),
    name_vi           VARCHAR(200),
    code              VARCHAR(50) NOT NULL UNIQUE,
    description       TEXT,
    description_en    TEXT,
    description_vi    TEXT,
    dean              VARCHAR(200),
    phone             VARCHAR(30),
    email             VARCHAR(255),
    building          VARCHAR(100),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_faculty_code ON academic.faculty (code);

CREATE TABLE IF NOT EXISTS academic.department (
    id                UUID PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    name_en           VARCHAR(200),
    name_vi           VARCHAR(200),
    code              VARCHAR(50) NOT NULL UNIQUE,
    description       TEXT,
    description_en    TEXT,
    description_vi    TEXT,
    chair             VARCHAR(200),
    phone             VARCHAR(30),
    email             VARCHAR(255),
    building          VARCHAR(100),
    faculty_id        UUID NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_department_code ON academic.department (code);
CREATE INDEX IF NOT EXISTS idx_department_faculty ON academic.department (faculty_id);

CREATE TABLE IF NOT EXISTS academic.curriculum (
    id                UUID PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    name_en           VARCHAR(200),
    name_vi           VARCHAR(200),
    code              VARCHAR(50) NOT NULL UNIQUE,
    department_id     UUID NOT NULL,
    department_code   VARCHAR(50),
    department_name   VARCHAR(200),
    description       TEXT,
    credits           INTEGER NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_curriculum_code ON academic.curriculum (code);
CREATE INDEX IF NOT EXISTS idx_curriculum_department ON academic.curriculum (department_id);

CREATE TABLE IF NOT EXISTS academic.course (
    id                UUID PRIMARY KEY,
    code              VARCHAR(50) NOT NULL UNIQUE,
    name              VARCHAR(200) NOT NULL,
    name_en           VARCHAR(200),
    name_vi           VARCHAR(200),
    description       TEXT,
    description_en    TEXT,
    description_vi    TEXT,
    credits           INTEGER NOT NULL,
    department_id     UUID NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_course_code ON academic.course (code);
CREATE INDEX IF NOT EXISTS idx_course_department ON academic.course (department_id);
