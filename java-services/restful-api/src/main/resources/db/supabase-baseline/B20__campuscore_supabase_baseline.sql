-- CampusCore schema-only baseline for new hosted Supabase environments.
-- Generated from a clean PostgreSQL 15.19 V1 -> V20 database with ownership,
-- grants, data, psql restrict markers and Supabase-managed schemas excluded.
-- Do not add users, password hashes, sessions, challenges, rate buckets,
-- chatbot history, Mailpit content, or demo/test rows to this migration.

-- Dumped from database version 15.19
-- Dumped by pg_dump version 15.19

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: academic; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS academic;


--
-- Name: assistant; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS assistant;


--
-- Name: campuscore_auth; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS campuscore_auth;


--
-- Name: engagement; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS engagement;


--
-- Name: notifications; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS notifications;


--
-- Name: thesis; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS thesis;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: AcademicYear; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."AcademicYear" (
    id character varying(120) NOT NULL,
    year integer NOT NULL,
    "startDate" timestamp with time zone NOT NULL,
    "endDate" timestamp with time zone NOT NULL,
    "isCurrent" boolean DEFAULT false NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Attendance; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Attendance" (
    id character varying(120) NOT NULL,
    "studentId" character varying(120) NOT NULL,
    "sectionId" character varying(120) NOT NULL,
    date timestamp with time zone NOT NULL,
    status character varying(40) NOT NULL,
    notes character varying(1000),
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Classroom; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Classroom" (
    id character varying(120) NOT NULL,
    building character varying(160) NOT NULL,
    "roomNumber" character varying(80) NOT NULL,
    capacity integer NOT NULL,
    type character varying(80) NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Course; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Course" (
    id character varying(120) NOT NULL,
    code character varying(60) NOT NULL,
    name character varying(240) NOT NULL,
    "nameEn" character varying(240),
    "nameVi" character varying(240),
    description character varying(1000),
    "descriptionEn" character varying(1000),
    "descriptionVi" character varying(1000),
    credits integer NOT NULL,
    "departmentId" character varying(120) NOT NULL,
    "semesterId" character varying(120),
    "isActive" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: CourseRequirement; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."CourseRequirement" (
    id character varying(120) NOT NULL,
    "courseId" character varying(120) NOT NULL,
    "requiredCourseId" character varying(120) NOT NULL,
    "requirementType" character varying(20) NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT course_requirement_not_self CHECK ((("courseId")::text <> ("requiredCourseId")::text)),
    CONSTRAINT course_requirement_type_valid CHECK ((("requirementType")::text = ANY ((ARRAY['PREREQUISITE'::character varying, 'COREQUISITE'::character varying])::text[])))
);


--
-- Name: Curriculum; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Curriculum" (
    id character varying(120) NOT NULL,
    name character varying(180) NOT NULL,
    "nameEn" character varying(180),
    "nameVi" character varying(180),
    code character varying(40) NOT NULL,
    "departmentId" character varying(120) NOT NULL,
    "academicYearId" character varying(120) NOT NULL,
    "semesterId" character varying(120),
    "totalCredits" integer DEFAULT 0 NOT NULL,
    description character varying(1000),
    "descriptionEn" character varying(1000),
    "descriptionVi" character varying(1000),
    "isActive" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: CurriculumCourse; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."CurriculumCourse" (
    id character varying(120) NOT NULL,
    "curriculumId" character varying(120) NOT NULL,
    "courseId" character varying(120) NOT NULL,
    year integer NOT NULL,
    semester integer NOT NULL,
    "isMandatory" boolean DEFAULT true NOT NULL
);


--
-- Name: Department; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Department" (
    id character varying(120) NOT NULL,
    name character varying(180) NOT NULL,
    "nameEn" character varying(180),
    "nameVi" character varying(180),
    code character varying(40) NOT NULL,
    description character varying(1000),
    "descriptionEn" character varying(1000),
    "descriptionVi" character varying(1000),
    chair character varying(160),
    phone character varying(80),
    email character varying(320),
    building character varying(160),
    "facultyId" character varying(120) NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL
);


--
-- Name: Enrollment; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Enrollment" (
    id character varying(120) NOT NULL,
    "studentId" character varying(120) NOT NULL,
    "sectionId" character varying(120) NOT NULL,
    "semesterId" character varying(120) NOT NULL,
    status character varying(40) NOT NULL,
    "enrolledAt" timestamp with time zone NOT NULL,
    "droppedAt" timestamp with time zone,
    "gradeStatus" character varying(40) NOT NULL,
    "finalGrade" numeric(5,2),
    "letterGrade" character varying(16),
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT enrollment_semester_required CHECK ((length(TRIM(BOTH FROM "semesterId")) > 0))
);


--
-- Name: EnrollmentAudit; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."EnrollmentAudit" (
    id character varying(120) NOT NULL,
    "operationId" character varying(120),
    "studentId" character varying(120) NOT NULL,
    "sectionId" character varying(120) NOT NULL,
    action character varying(20) NOT NULL,
    "reasonCode" character varying(80),
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT enrollment_audit_action_valid CHECK (((action)::text = ANY ((ARRAY['ENROLL'::character varying, 'DROP'::character varying, 'CANCEL'::character varying, 'ARCHIVE'::character varying])::text[])))
);


--
-- Name: EnrollmentOperation; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."EnrollmentOperation" (
    id character varying(120) NOT NULL,
    "studentId" character varying(120) NOT NULL,
    "idempotencyKey" character varying(120) NOT NULL,
    "canonicalRequestHash" character(64) NOT NULL,
    "operationType" character varying(20) NOT NULL,
    state character varying(40) NOT NULL,
    "responseStatus" integer,
    "responseBody" text,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "completedAt" timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT enrollment_operation_hash_valid CHECK (("canonicalRequestHash" ~ '^[0-9a-fA-F]{64}$'::text)),
    CONSTRAINT enrollment_operation_type_valid CHECK ((("operationType")::text = ANY ((ARRAY['ENROLL'::character varying, 'DROP'::character varying])::text[])))
);


--
-- Name: Faculty; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Faculty" (
    id character varying(120) NOT NULL,
    name character varying(180) NOT NULL,
    "nameEn" character varying(180),
    "nameVi" character varying(180),
    code character varying(40) NOT NULL,
    description character varying(1000),
    "descriptionEn" character varying(1000),
    "descriptionVi" character varying(1000),
    dean character varying(160),
    phone character varying(80),
    email character varying(320),
    building character varying(160),
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL
);


--
-- Name: GradeItem; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."GradeItem" (
    id character varying(120) NOT NULL,
    "sectionId" character varying(120) NOT NULL,
    name character varying(180) NOT NULL,
    type character varying(60) NOT NULL,
    "maxScore" numeric(5,2) NOT NULL,
    weight numeric(5,2) NOT NULL,
    "gradedAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT grade_item_score_non_negative CHECK (("maxScore" >= (0)::numeric)),
    CONSTRAINT grade_item_weight_range CHECK (((weight >= (0)::numeric) AND (weight <= (100)::numeric)))
);


--
-- Name: Lecturer; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Lecturer" (
    id character varying(120) NOT NULL,
    "userId" character varying(120) NOT NULL,
    "departmentId" character varying(120) NOT NULL,
    "employeeId" character varying(120) NOT NULL,
    title character varying(160),
    specialization character varying(240),
    office character varying(120),
    phone character varying(80),
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL
);


--
-- Name: RegistrationCohortWindow; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."RegistrationCohortWindow" (
    id character varying(120) NOT NULL,
    "roundId" character varying(120) NOT NULL,
    "cohortCode" character varying(80) NOT NULL,
    "priorityRank" integer DEFAULT 0 NOT NULL,
    "windowStart" timestamp with time zone NOT NULL,
    "windowEnd" timestamp with time zone NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT registration_cohort_window_valid CHECK ((("windowStart" < "windowEnd") AND ("priorityRank" >= 0)))
);


--
-- Name: RegistrationRound; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."RegistrationRound" (
    id character varying(120) NOT NULL,
    "semesterId" character varying(120) NOT NULL,
    status character varying(40) NOT NULL,
    "registrationStart" timestamp with time zone NOT NULL,
    "registrationEnd" timestamp with time zone NOT NULL,
    "addDropStart" timestamp with time zone NOT NULL,
    "addDropEnd" timestamp with time zone NOT NULL,
    "maxCredits" integer DEFAULT 28 NOT NULL,
    "institutionTimeZone" character varying(80) DEFAULT 'Asia/Ho_Chi_Minh'::character varying NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT registration_round_credit_limit_valid CHECK ((("maxCredits" >= 1) AND ("maxCredits" <= 60))),
    CONSTRAINT registration_round_window_valid CHECK ((("registrationStart" < "registrationEnd") AND ("addDropStart" < "addDropEnd")))
);


--
-- Name: RegistrationSlip; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."RegistrationSlip" (
    id character varying(120) NOT NULL,
    "studentId" character varying(120) NOT NULL,
    "roundId" character varying(120) NOT NULL,
    "contentHash" character(64) NOT NULL,
    "generatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "snapshotPayload" text,
    CONSTRAINT registration_slip_hash_valid CHECK (("contentHash" ~ '^[0-9a-fA-F]{64}$'::text))
);


--
-- Name: Section; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Section" (
    id character varying(120) NOT NULL,
    "sectionNumber" character varying(80) NOT NULL,
    "courseId" character varying(120) NOT NULL,
    "semesterId" character varying(120) NOT NULL,
    "lecturerId" character varying(120),
    "classroomId" character varying(120),
    capacity integer NOT NULL,
    "enrolledCount" integer DEFAULT 0 NOT NULL,
    status character varying(40) NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT section_capacity_non_negative CHECK ((capacity >= 0)),
    CONSTRAINT section_enrolled_count_valid CHECK ((("enrolledCount" >= 0) AND ("enrolledCount" <= capacity)))
);


--
-- Name: SectionSchedule; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."SectionSchedule" (
    id character varying(120) NOT NULL,
    "sectionId" character varying(120) NOT NULL,
    "classroomId" character varying(120) NOT NULL,
    "dayOfWeek" integer NOT NULL,
    "startTime" character varying(10) NOT NULL,
    "endTime" character varying(10) NOT NULL,
    "startTimeValue" time without time zone NOT NULL,
    "endTimeValue" time without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT section_schedule_time_order CHECK (("startTimeValue" < "endTimeValue"))
);


--
-- Name: Semester; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Semester" (
    id character varying(120) NOT NULL,
    name character varying(180) NOT NULL,
    "nameEn" character varying(180),
    "nameVi" character varying(180),
    type character varying(40) NOT NULL,
    "academicYearId" character varying(120) NOT NULL,
    "startDate" timestamp with time zone NOT NULL,
    "endDate" timestamp with time zone NOT NULL,
    "registrationStart" timestamp with time zone,
    "registrationEnd" timestamp with time zone,
    "addDropStart" timestamp with time zone,
    "addDropEnd" timestamp with time zone,
    status character varying(40) NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Student; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."Student" (
    id character varying(120) NOT NULL,
    "userId" character varying(120) NOT NULL,
    "studentId" character varying(120) NOT NULL,
    "curriculumId" character varying(120) NOT NULL,
    year integer NOT NULL,
    status character varying(40) DEFAULT 'ACTIVE'::character varying NOT NULL,
    "admissionDate" timestamp with time zone NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: StudentGrade; Type: TABLE; Schema: academic; Owner: -
--

CREATE TABLE academic."StudentGrade" (
    id character varying(120) NOT NULL,
    "enrollmentId" character varying(120) NOT NULL,
    "gradeItemId" character varying(120) NOT NULL,
    score numeric(5,2),
    CONSTRAINT student_grade_score_non_negative CHECK (((score IS NULL) OR (score >= (0)::numeric)))
);


--
-- Name: chat_citation; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.chat_citation (
    id uuid NOT NULL,
    message_id uuid NOT NULL,
    document_id uuid,
    slug character varying(180) NOT NULL,
    title text NOT NULL,
    source text NOT NULL,
    locale character varying(8) NOT NULL,
    excerpt text NOT NULL,
    domain character varying(48) DEFAULT 'THESIS'::character varying NOT NULL,
    source_kind character varying(24) DEFAULT 'CURATED'::character varying NOT NULL,
    source_id character varying(180),
    revision_id uuid,
    revision_version integer,
    catalog_entity_type character varying(48),
    catalog_entity_id character varying(180),
    catalog_updated_at timestamp with time zone,
    snapshot_hash character varying(64),
    ordinal integer DEFAULT 0 NOT NULL,
    CONSTRAINT assistant_chat_citation_source_kind_valid CHECK (((source_kind)::text = ANY ((ARRAY['CURATED'::character varying, 'CATALOG'::character varying, 'LEGACY_SNAPSHOT'::character varying])::text[])))
);


--
-- Name: chat_conversation; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.chat_conversation (
    id uuid NOT NULL,
    owner_id character varying(120) NOT NULL,
    title character varying(160),
    locale character varying(2) DEFAULT 'vi'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone DEFAULT (CURRENT_TIMESTAMP + '90 days'::interval) NOT NULL,
    state character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    archived_at timestamp with time zone,
    archived_by character varying(120),
    CONSTRAINT assistant_chat_conversation_state_valid CHECK (((state)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'PURGED'::character varying])::text[]))),
    CONSTRAINT assistant_chat_locale_valid CHECK (((locale)::text = ANY ((ARRAY['vi'::character varying, 'en'::character varying])::text[])))
);


--
-- Name: chat_message; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.chat_message (
    id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    role character varying(16) NOT NULL,
    content text NOT NULL,
    model character varying(80) NOT NULL,
    degraded boolean DEFAULT false NOT NULL,
    reason_code character varying(48) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    turn_id uuid,
    ordinal integer,
    CONSTRAINT assistant_chat_role_valid CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'ASSISTANT'::character varying])::text[])))
);


--
-- Name: chat_message_feedback; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.chat_message_feedback (
    message_id uuid NOT NULL,
    owner_id character varying(120) NOT NULL,
    rating character varying(4) NOT NULL,
    reason character varying(16),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT assistant_feedback_rating_valid CHECK (((rating)::text = ANY ((ARRAY['UP'::character varying, 'DOWN'::character varying])::text[]))),
    CONSTRAINT assistant_feedback_reason_valid CHECK (((reason IS NULL) OR ((reason)::text = ANY ((ARRAY['HELPFUL'::character varying, 'CLEAR'::character varying, 'INCORRECT'::character varying, 'OUTDATED'::character varying, 'NOT_RELEVANT'::character varying, 'UNSAFE'::character varying])::text[]))))
);


--
-- Name: chat_turn_ledger; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.chat_turn_ledger (
    turn_id uuid NOT NULL,
    owner_id character varying(120) NOT NULL,
    client_request_id uuid NOT NULL,
    request_hash character varying(64) NOT NULL,
    conversation_id uuid,
    state character varying(24) NOT NULL,
    lease_owner character varying(160),
    lease_generation bigint DEFAULT 0 NOT NULL,
    lease_expires_at timestamp with time zone,
    dispatched_at timestamp with time zone,
    quota_reserved boolean DEFAULT false NOT NULL,
    result_message_id uuid,
    terminal_reason character varying(48),
    source_snapshot_hash character varying(64),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    purged_at timestamp with time zone,
    tombstone_until timestamp with time zone,
    created_conversation boolean DEFAULT false NOT NULL,
    CONSTRAINT assistant_turn_hash_valid CHECK ((length((request_hash)::text) = 64)),
    CONSTRAINT assistant_turn_lease_generation_valid CHECK ((lease_generation >= 0)),
    CONSTRAINT assistant_turn_state_valid CHECK (((state)::text = ANY ((ARRAY['RESERVED'::character varying, 'SNAPSHOT_READY'::character varying, 'DISPATCHED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying, 'FAILED_PRE_DISPATCH'::character varying, 'FAILED_AMBIGUOUS'::character varying, 'PURGED'::character varying])::text[])))
);


--
-- Name: knowledge_document; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.knowledge_document (
    id uuid NOT NULL,
    slug text NOT NULL,
    locale text NOT NULL,
    title text NOT NULL,
    content text NOT NULL,
    source text NOT NULL,
    priority smallint DEFAULT 100 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    active boolean DEFAULT true NOT NULL,
    visibility character varying(32) DEFAULT 'PUBLIC'::character varying NOT NULL,
    archived_at timestamp with time zone,
    archived_by character varying(120),
    CONSTRAINT assistant_knowledge_locale_valid CHECK ((locale = ANY (ARRAY['vi'::text, 'en'::text, 'both'::text]))),
    CONSTRAINT assistant_knowledge_priority_valid CHECK (((priority >= 1) AND (priority <= 1000)))
);


--
-- Name: knowledge_document_audit; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.knowledge_document_audit (
    id uuid NOT NULL,
    revision_id uuid NOT NULL,
    action character varying(32) NOT NULL,
    actor_id character varying(120) NOT NULL,
    note text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: knowledge_document_revision; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.knowledge_document_revision (
    id uuid NOT NULL,
    document_id uuid NOT NULL,
    version integer NOT NULL,
    state character varying(24) NOT NULL,
    locale character varying(8) NOT NULL,
    slug character varying(180) NOT NULL,
    title text NOT NULL,
    content text NOT NULL,
    source text NOT NULL,
    priority smallint DEFAULT 100 NOT NULL,
    created_by character varying(120) NOT NULL,
    reviewed_by character varying(120),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at timestamp with time zone,
    CONSTRAINT assistant_revision_state_valid CHECK (((state)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: provider_dispatch_registry; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.provider_dispatch_registry (
    owner_id character varying(120) NOT NULL,
    client_request_id uuid NOT NULL,
    lease_generation bigint NOT NULL,
    provider_handle character varying(180),
    state character varying(24) DEFAULT 'DISPATCHED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cancelled_at timestamp with time zone,
    CONSTRAINT assistant_provider_dispatch_state_valid CHECK (((state)::text = ANY ((ARRAY['DISPATCHED'::character varying, 'CANCELLED'::character varying, 'COMPLETED'::character varying])::text[])))
);


--
-- Name: usage_bucket; Type: TABLE; Schema: assistant; Owner: -
--

CREATE TABLE assistant.usage_bucket (
    bucket_date date NOT NULL,
    owner_id character varying(120) NOT NULL,
    scope character varying(16) DEFAULT 'USER'::character varying NOT NULL,
    request_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT assistant_usage_nonnegative CHECK ((request_count >= 0)),
    CONSTRAINT assistant_usage_scope_valid CHECK (((scope)::text = ANY ((ARRAY['USER'::character varying, 'GLOBAL'::character varying])::text[])))
);


--
-- Name: AuthChallenge; Type: TABLE; Schema: campuscore_auth; Owner: -
--

CREATE TABLE campuscore_auth."AuthChallenge" (
    id character varying(120) NOT NULL,
    "userId" character varying(120) NOT NULL,
    purpose character varying(40) NOT NULL,
    "tokenHash" character varying(64) NOT NULL,
    "expiresAt" timestamp without time zone NOT NULL,
    "consumedAt" timestamp without time zone,
    "attemptCount" integer DEFAULT 0 NOT NULL,
    "lastSentAt" timestamp without time zone NOT NULL,
    "createdAt" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT auth_challenge_attempts_valid CHECK (("attemptCount" >= 0)),
    CONSTRAINT auth_challenge_purpose_valid CHECK (((purpose)::text = ANY ((ARRAY['EMAIL_VERIFICATION'::character varying, 'PASSWORD_RESET'::character varying])::text[])))
);


--
-- Name: AuthRateLimitBucket; Type: TABLE; Schema: campuscore_auth; Owner: -
--

CREATE TABLE campuscore_auth."AuthRateLimitBucket" (
    scope character varying(80) NOT NULL,
    "bucketKeyHash" character varying(64) NOT NULL,
    "windowStart" timestamp without time zone NOT NULL,
    "requestCount" integer DEFAULT 0 NOT NULL,
    "updatedAt" timestamp without time zone NOT NULL,
    CONSTRAINT auth_rate_limit_count_valid CHECK (("requestCount" >= 0))
);


--
-- Name: Lecturer; Type: VIEW; Schema: campuscore_auth; Owner: -
--

CREATE VIEW campuscore_auth."Lecturer" AS
 SELECT "Lecturer".id,
    "Lecturer"."userId",
    "Lecturer"."departmentId",
    "Lecturer"."employeeId",
    "Lecturer"."isActive"
   FROM academic."Lecturer";


--
-- Name: Permission; Type: TABLE; Schema: campuscore_auth; Owner: -
--

CREATE TABLE campuscore_auth."Permission" (
    id character varying(120) NOT NULL,
    name character varying(160) NOT NULL,
    description character varying(500),
    module character varying(80) NOT NULL,
    action character varying(80) NOT NULL,
    "createdAt" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Role; Type: TABLE; Schema: campuscore_auth; Owner: -
--

CREATE TABLE campuscore_auth."Role" (
    id character varying(120) NOT NULL,
    name character varying(80) NOT NULL,
    description character varying(500),
    "isSystem" boolean DEFAULT false NOT NULL,
    "createdAt" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: RolePermission; Type: TABLE; Schema: campuscore_auth; Owner: -
--

CREATE TABLE campuscore_auth."RolePermission" (
    id character varying(120) NOT NULL,
    "roleId" character varying(120) NOT NULL,
    "permissionId" character varying(120) NOT NULL
);


--
-- Name: Session; Type: TABLE; Schema: campuscore_auth; Owner: -
--

CREATE TABLE campuscore_auth."Session" (
    id character varying(120) NOT NULL,
    "userId" character varying(120) NOT NULL,
    "refreshToken" character varying(200) NOT NULL,
    "userAgent" character varying(500),
    "ipAddress" character varying(80),
    "expiresAt" timestamp without time zone NOT NULL,
    "createdAt" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Student; Type: VIEW; Schema: campuscore_auth; Owner: -
--

CREATE VIEW campuscore_auth."Student" AS
 SELECT "Student".id,
    "Student"."userId",
    "Student"."studentId",
    "Student"."curriculumId",
    "Student".year,
    "Student".status,
    "Student"."admissionDate",
    "Student"."createdAt",
    "Student"."updatedAt"
   FROM academic."Student";


--
-- Name: User; Type: TABLE; Schema: campuscore_auth; Owner: -
--

CREATE TABLE campuscore_auth."User" (
    id character varying(120) NOT NULL,
    email character varying(320) NOT NULL,
    password character varying(200) NOT NULL,
    "firstName" character varying(120) NOT NULL,
    "lastName" character varying(120) NOT NULL,
    phone character varying(80),
    gender character varying(40),
    "dateOfBirth" timestamp without time zone,
    address character varying(500),
    avatar character varying(500),
    status character varying(40) NOT NULL,
    "emailVerified" boolean DEFAULT false NOT NULL,
    "isSuperAdmin" boolean DEFAULT false NOT NULL,
    "failedLoginAttempts" integer DEFAULT 0 NOT NULL,
    "lockedUntil" timestamp without time zone,
    "lastLoginAt" timestamp without time zone,
    "passwordChangedAt" timestamp without time zone,
    "refreshToken" character varying(200),
    "resetToken" character varying(200),
    "resetExpires" timestamp without time zone,
    "verificationToken" character varying(200),
    "createdAt" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: UserRole; Type: TABLE; Schema: campuscore_auth; Owner: -
--

CREATE TABLE campuscore_auth."UserRole" (
    id character varying(120) NOT NULL,
    "userId" character varying(120) NOT NULL,
    "roleId" character varying(120) NOT NULL
);


--
-- Name: Announcement; Type: TABLE; Schema: engagement; Owner: -
--

CREATE TABLE engagement."Announcement" (
    id character varying(120) NOT NULL,
    title character varying(240) NOT NULL,
    content text NOT NULL,
    priority character varying(40) NOT NULL,
    "targetRoles" text[] DEFAULT ARRAY[]::text[] NOT NULL,
    "targetYears" integer[] DEFAULT ARRAY[]::integer[] NOT NULL,
    "isGlobal" boolean DEFAULT false NOT NULL,
    "publishAt" timestamp with time zone,
    "expiresAt" timestamp with time zone,
    "publishedBy" character varying(120),
    "semesterId" character varying(120),
    "semesterName" character varying(180),
    "sectionId" character varying(120),
    "sectionNumber" character varying(80),
    "courseCode" character varying(60),
    "courseName" character varying(240),
    "lecturerId" character varying(120),
    "lecturerDisplayName" character varying(240),
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: notification; Type: TABLE; Schema: notifications; Owner: -
--

CREATE TABLE notifications.notification (
    id character varying(120) NOT NULL,
    user_id character varying(120) NOT NULL,
    title character varying(240) NOT NULL,
    message character varying(2000) NOT NULL,
    type character varying(60) NOT NULL,
    link character varying(500),
    is_read boolean DEFAULT false NOT NULL,
    read_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: thesis_group; Type: TABLE; Schema: thesis; Owner: -
--

CREATE TABLE thesis.thesis_group (
    id uuid NOT NULL,
    round_id uuid NOT NULL,
    leader_student_id character varying(120) NOT NULL,
    topic_id uuid,
    status character varying(32) NOT NULL,
    approval_status character varying(32) NOT NULL,
    approved_by character varying(120),
    approved_at timestamp with time zone,
    rejection_reason character varying(500),
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: thesis_group_member; Type: TABLE; Schema: thesis; Owner: -
--

CREATE TABLE thesis.thesis_group_member (
    id uuid NOT NULL,
    group_id uuid NOT NULL,
    round_id uuid NOT NULL,
    student_id character varying(120) NOT NULL,
    member_order integer NOT NULL,
    is_leader boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT thesis_group_member_order_valid CHECK (((member_order >= 1) AND (member_order <= 3)))
);


--
-- Name: thesis_registration_round; Type: TABLE; Schema: thesis; Owner: -
--

CREATE TABLE thesis.thesis_registration_round (
    id uuid NOT NULL,
    name character varying(180) NOT NULL,
    thesis_type character varying(40) NOT NULL,
    registration_start timestamp with time zone NOT NULL,
    registration_end timestamp with time zone NOT NULL,
    proposal_publish_at timestamp with time zone,
    report_date timestamp with time zone,
    status character varying(32) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT thesis_round_dates_valid CHECK ((registration_end > registration_start))
);


--
-- Name: thesis_topic; Type: TABLE; Schema: thesis; Owner: -
--

CREATE TABLE thesis.thesis_topic (
    id uuid NOT NULL,
    round_id uuid NOT NULL,
    department_id character varying(120) NOT NULL,
    title character varying(240) NOT NULL,
    description text NOT NULL,
    max_groups integer DEFAULT 1 NOT NULL,
    status character varying(32) NOT NULL,
    created_by character varying(120) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT thesis_topic_group_limit_valid CHECK (((max_groups >= 1) AND (max_groups <= 20)))
);


--
-- Name: thesis_topic_supervisor; Type: TABLE; Schema: thesis; Owner: -
--

CREATE TABLE thesis.thesis_topic_supervisor (
    id uuid NOT NULL,
    topic_id uuid NOT NULL,
    lecturer_id character varying(120) NOT NULL,
    supervisor_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT thesis_topic_supervisor_order_valid CHECK (((supervisor_order >= 1) AND (supervisor_order <= 2)))
);


--
-- Name: AcademicYear AcademicYear_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."AcademicYear"
    ADD CONSTRAINT "AcademicYear_pkey" PRIMARY KEY (id);


--
-- Name: Attendance Attendance_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Attendance"
    ADD CONSTRAINT "Attendance_pkey" PRIMARY KEY (id);


--
-- Name: Classroom Classroom_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Classroom"
    ADD CONSTRAINT "Classroom_pkey" PRIMARY KEY (id);


--
-- Name: CourseRequirement CourseRequirement_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."CourseRequirement"
    ADD CONSTRAINT "CourseRequirement_pkey" PRIMARY KEY (id);


--
-- Name: Course Course_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Course"
    ADD CONSTRAINT "Course_pkey" PRIMARY KEY (id);


--
-- Name: CurriculumCourse CurriculumCourse_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."CurriculumCourse"
    ADD CONSTRAINT "CurriculumCourse_pkey" PRIMARY KEY (id);


--
-- Name: Curriculum Curriculum_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Curriculum"
    ADD CONSTRAINT "Curriculum_pkey" PRIMARY KEY (id);


--
-- Name: Department Department_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Department"
    ADD CONSTRAINT "Department_pkey" PRIMARY KEY (id);


--
-- Name: EnrollmentAudit EnrollmentAudit_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."EnrollmentAudit"
    ADD CONSTRAINT "EnrollmentAudit_pkey" PRIMARY KEY (id);


--
-- Name: EnrollmentOperation EnrollmentOperation_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."EnrollmentOperation"
    ADD CONSTRAINT "EnrollmentOperation_pkey" PRIMARY KEY (id);


--
-- Name: Enrollment Enrollment_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Enrollment"
    ADD CONSTRAINT "Enrollment_pkey" PRIMARY KEY (id);


--
-- Name: Faculty Faculty_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Faculty"
    ADD CONSTRAINT "Faculty_pkey" PRIMARY KEY (id);


--
-- Name: GradeItem GradeItem_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."GradeItem"
    ADD CONSTRAINT "GradeItem_pkey" PRIMARY KEY (id);


--
-- Name: Lecturer Lecturer_employeeId_key; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Lecturer"
    ADD CONSTRAINT "Lecturer_employeeId_key" UNIQUE ("employeeId");


--
-- Name: Lecturer Lecturer_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Lecturer"
    ADD CONSTRAINT "Lecturer_pkey" PRIMARY KEY (id);


--
-- Name: Lecturer Lecturer_userId_key; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Lecturer"
    ADD CONSTRAINT "Lecturer_userId_key" UNIQUE ("userId");


--
-- Name: RegistrationCohortWindow RegistrationCohortWindow_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationCohortWindow"
    ADD CONSTRAINT "RegistrationCohortWindow_pkey" PRIMARY KEY (id);


--
-- Name: RegistrationRound RegistrationRound_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationRound"
    ADD CONSTRAINT "RegistrationRound_pkey" PRIMARY KEY (id);


--
-- Name: RegistrationSlip RegistrationSlip_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationSlip"
    ADD CONSTRAINT "RegistrationSlip_pkey" PRIMARY KEY (id);


--
-- Name: SectionSchedule SectionSchedule_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."SectionSchedule"
    ADD CONSTRAINT "SectionSchedule_pkey" PRIMARY KEY (id);


--
-- Name: Section Section_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Section"
    ADD CONSTRAINT "Section_pkey" PRIMARY KEY (id);


--
-- Name: Semester Semester_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Semester"
    ADD CONSTRAINT "Semester_pkey" PRIMARY KEY (id);


--
-- Name: StudentGrade StudentGrade_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."StudentGrade"
    ADD CONSTRAINT "StudentGrade_pkey" PRIMARY KEY (id);


--
-- Name: Student Student_pkey; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Student"
    ADD CONSTRAINT "Student_pkey" PRIMARY KEY (id);


--
-- Name: Student Student_studentId_key; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Student"
    ADD CONSTRAINT "Student_studentId_key" UNIQUE ("studentId");


--
-- Name: Student Student_userId_key; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Student"
    ADD CONSTRAINT "Student_userId_key" UNIQUE ("userId");


--
-- Name: Attendance academic_attendance_student_section_date_unique; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Attendance"
    ADD CONSTRAINT academic_attendance_student_section_date_unique UNIQUE ("studentId", "sectionId", date);


--
-- Name: CourseRequirement course_requirement_unique; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."CourseRequirement"
    ADD CONSTRAINT course_requirement_unique UNIQUE ("courseId", "requiredCourseId", "requirementType");


--
-- Name: EnrollmentOperation enrollment_operation_key_unique; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."EnrollmentOperation"
    ADD CONSTRAINT enrollment_operation_key_unique UNIQUE ("studentId", "idempotencyKey");


--
-- Name: RegistrationCohortWindow registration_cohort_round_code_unique; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationCohortWindow"
    ADD CONSTRAINT registration_cohort_round_code_unique UNIQUE ("roundId", "cohortCode");


--
-- Name: RegistrationRound registration_round_semester_unique; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationRound"
    ADD CONSTRAINT registration_round_semester_unique UNIQUE ("semesterId", id);


--
-- Name: RegistrationSlip registration_slip_student_round_unique; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationSlip"
    ADD CONSTRAINT registration_slip_student_round_unique UNIQUE ("studentId", "roundId");


--
-- Name: StudentGrade student_grade_item_unique; Type: CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."StudentGrade"
    ADD CONSTRAINT student_grade_item_unique UNIQUE ("enrollmentId", "gradeItemId");


--
-- Name: chat_citation chat_citation_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_citation
    ADD CONSTRAINT chat_citation_pkey PRIMARY KEY (id);


--
-- Name: chat_conversation chat_conversation_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_conversation
    ADD CONSTRAINT chat_conversation_pkey PRIMARY KEY (id);


--
-- Name: chat_message_feedback chat_message_feedback_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_message_feedback
    ADD CONSTRAINT chat_message_feedback_pkey PRIMARY KEY (message_id, owner_id);


--
-- Name: chat_message chat_message_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_message
    ADD CONSTRAINT chat_message_pkey PRIMARY KEY (id);


--
-- Name: chat_turn_ledger chat_turn_ledger_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_turn_ledger
    ADD CONSTRAINT chat_turn_ledger_pkey PRIMARY KEY (turn_id);


--
-- Name: knowledge_document_audit knowledge_document_audit_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.knowledge_document_audit
    ADD CONSTRAINT knowledge_document_audit_pkey PRIMARY KEY (id);


--
-- Name: knowledge_document knowledge_document_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.knowledge_document
    ADD CONSTRAINT knowledge_document_pkey PRIMARY KEY (id);


--
-- Name: knowledge_document_revision knowledge_document_revision_document_id_version_key; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.knowledge_document_revision
    ADD CONSTRAINT knowledge_document_revision_document_id_version_key UNIQUE (document_id, version);


--
-- Name: knowledge_document_revision knowledge_document_revision_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.knowledge_document_revision
    ADD CONSTRAINT knowledge_document_revision_pkey PRIMARY KEY (id);


--
-- Name: knowledge_document knowledge_document_slug_key; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.knowledge_document
    ADD CONSTRAINT knowledge_document_slug_key UNIQUE (slug);


--
-- Name: provider_dispatch_registry provider_dispatch_registry_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.provider_dispatch_registry
    ADD CONSTRAINT provider_dispatch_registry_pkey PRIMARY KEY (owner_id, client_request_id, lease_generation);


--
-- Name: usage_bucket usage_bucket_pkey; Type: CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.usage_bucket
    ADD CONSTRAINT usage_bucket_pkey PRIMARY KEY (bucket_date, owner_id, scope);


--
-- Name: AuthChallenge AuthChallenge_pkey; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."AuthChallenge"
    ADD CONSTRAINT "AuthChallenge_pkey" PRIMARY KEY (id);


--
-- Name: AuthRateLimitBucket AuthRateLimitBucket_pkey; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."AuthRateLimitBucket"
    ADD CONSTRAINT "AuthRateLimitBucket_pkey" PRIMARY KEY (scope, "bucketKeyHash", "windowStart");


--
-- Name: Permission Permission_name_key; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."Permission"
    ADD CONSTRAINT "Permission_name_key" UNIQUE (name);


--
-- Name: Permission Permission_pkey; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."Permission"
    ADD CONSTRAINT "Permission_pkey" PRIMARY KEY (id);


--
-- Name: RolePermission RolePermission_pkey; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."RolePermission"
    ADD CONSTRAINT "RolePermission_pkey" PRIMARY KEY (id);


--
-- Name: Role Role_name_key; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."Role"
    ADD CONSTRAINT "Role_name_key" UNIQUE (name);


--
-- Name: Role Role_pkey; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."Role"
    ADD CONSTRAINT "Role_pkey" PRIMARY KEY (id);


--
-- Name: Session Session_pkey; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."Session"
    ADD CONSTRAINT "Session_pkey" PRIMARY KEY (id);


--
-- Name: UserRole UserRole_pkey; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."UserRole"
    ADD CONSTRAINT "UserRole_pkey" PRIMARY KEY (id);


--
-- Name: User User_email_key; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."User"
    ADD CONSTRAINT "User_email_key" UNIQUE (email);


--
-- Name: User User_pkey; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."User"
    ADD CONSTRAINT "User_pkey" PRIMARY KEY (id);


--
-- Name: RolePermission auth_role_permission_unique; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."RolePermission"
    ADD CONSTRAINT auth_role_permission_unique UNIQUE ("roleId", "permissionId");


--
-- Name: UserRole auth_user_role_unique; Type: CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."UserRole"
    ADD CONSTRAINT auth_user_role_unique UNIQUE ("userId", "roleId");


--
-- Name: Announcement Announcement_pkey; Type: CONSTRAINT; Schema: engagement; Owner: -
--

ALTER TABLE ONLY engagement."Announcement"
    ADD CONSTRAINT "Announcement_pkey" PRIMARY KEY (id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: notifications; Owner: -
--

ALTER TABLE ONLY notifications.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: thesis_group_member thesis_group_member_pkey; Type: CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_group_member
    ADD CONSTRAINT thesis_group_member_pkey PRIMARY KEY (id);


--
-- Name: thesis_group_member thesis_group_member_unique; Type: CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_group_member
    ADD CONSTRAINT thesis_group_member_unique UNIQUE (group_id, student_id);


--
-- Name: thesis_group thesis_group_pkey; Type: CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_group
    ADD CONSTRAINT thesis_group_pkey PRIMARY KEY (id);


--
-- Name: thesis_registration_round thesis_registration_round_pkey; Type: CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_registration_round
    ADD CONSTRAINT thesis_registration_round_pkey PRIMARY KEY (id);


--
-- Name: thesis_group_member thesis_student_one_group_per_round; Type: CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_group_member
    ADD CONSTRAINT thesis_student_one_group_per_round UNIQUE (round_id, student_id);


--
-- Name: thesis_topic thesis_topic_pkey; Type: CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_topic
    ADD CONSTRAINT thesis_topic_pkey PRIMARY KEY (id);


--
-- Name: thesis_topic_supervisor thesis_topic_supervisor_order_unique; Type: CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_topic_supervisor
    ADD CONSTRAINT thesis_topic_supervisor_order_unique UNIQUE (topic_id, supervisor_order);


--
-- Name: thesis_topic_supervisor thesis_topic_supervisor_pkey; Type: CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_topic_supervisor
    ADD CONSTRAINT thesis_topic_supervisor_pkey PRIMARY KEY (id);


--
-- Name: thesis_topic_supervisor thesis_topic_supervisor_unique; Type: CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_topic_supervisor
    ADD CONSTRAINT thesis_topic_supervisor_unique UNIQUE (topic_id, lecturer_id);


--
-- Name: academic_attendance_section_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX academic_attendance_section_idx ON academic."Attendance" USING btree ("sectionId", date);


--
-- Name: academic_attendance_student_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX academic_attendance_student_idx ON academic."Attendance" USING btree ("studentId", date);


--
-- Name: academic_enrollment_section_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX academic_enrollment_section_idx ON academic."Enrollment" USING btree ("sectionId", status);


--
-- Name: academic_enrollment_student_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX academic_enrollment_student_idx ON academic."Enrollment" USING btree ("studentId", status);


--
-- Name: academic_section_course_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX academic_section_course_idx ON academic."Section" USING btree ("courseId", status);


--
-- Name: academic_section_semester_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX academic_section_semester_idx ON academic."Section" USING btree ("semesterId", status);


--
-- Name: academic_student_user_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX academic_student_user_idx ON academic."Student" USING btree ("userId");


--
-- Name: course_requirement_course_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX course_requirement_course_idx ON academic."CourseRequirement" USING btree ("courseId", "requirementType");


--
-- Name: enrollment_active_student_section_all_status_uq; Type: INDEX; Schema: academic; Owner: -
--

CREATE UNIQUE INDEX enrollment_active_student_section_all_status_uq ON academic."Enrollment" USING btree ("studentId", "sectionId") WHERE (lower((status)::text) = ANY (ARRAY['active'::text, 'enrolled'::text, 'pending'::text, 'confirmed'::text]));


--
-- Name: enrollment_active_student_section_uq; Type: INDEX; Schema: academic; Owner: -
--

CREATE UNIQUE INDEX enrollment_active_student_section_uq ON academic."Enrollment" USING btree ("studentId", "sectionId") WHERE (lower((status)::text) = ANY (ARRAY['active'::text, 'enrolled'::text]));


--
-- Name: enrollment_audit_student_time_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX enrollment_audit_student_time_idx ON academic."EnrollmentAudit" USING btree ("studentId", "createdAt");


--
-- Name: enrollment_operation_key_lookup_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX enrollment_operation_key_lookup_idx ON academic."EnrollmentOperation" USING btree ("studentId", "idempotencyKey", "canonicalRequestHash");


--
-- Name: enrollment_operation_state_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX enrollment_operation_state_idx ON academic."EnrollmentOperation" USING btree ("studentId", state, "updatedAt");


--
-- Name: enrollment_student_semester_status_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX enrollment_student_semester_status_idx ON academic."Enrollment" USING btree ("studentId", "semesterId", status);


--
-- Name: registration_round_status_window_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX registration_round_status_window_idx ON academic."RegistrationRound" USING btree (status, "registrationStart", "registrationEnd");


--
-- Name: registration_slip_student_time_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX registration_slip_student_time_idx ON academic."RegistrationSlip" USING btree ("studentId", "generatedAt" DESC);


--
-- Name: section_schedule_slot_idx; Type: INDEX; Schema: academic; Owner: -
--

CREATE INDEX section_schedule_slot_idx ON academic."SectionSchedule" USING btree ("dayOfWeek", "startTimeValue", "endTimeValue");


--
-- Name: assistant_chat_citation_message_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_chat_citation_message_idx ON assistant.chat_citation USING btree (message_id);


--
-- Name: assistant_chat_citation_provenance_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_chat_citation_provenance_idx ON assistant.chat_citation USING btree (domain, source_kind, source_id, revision_id, catalog_entity_id);


--
-- Name: assistant_chat_expiry_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_chat_expiry_idx ON assistant.chat_conversation USING btree (expires_at);


--
-- Name: assistant_chat_message_one_role_per_turn_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE UNIQUE INDEX assistant_chat_message_one_role_per_turn_idx ON assistant.chat_message USING btree (turn_id, role) WHERE (turn_id IS NOT NULL);


--
-- Name: assistant_chat_message_order_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_chat_message_order_idx ON assistant.chat_message USING btree (conversation_id, created_at, id);


--
-- Name: assistant_chat_message_turn_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_chat_message_turn_idx ON assistant.chat_message USING btree (turn_id, role);


--
-- Name: assistant_chat_owner_state_updated_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_chat_owner_state_updated_idx ON assistant.chat_conversation USING btree (owner_id, state, updated_at DESC);


--
-- Name: assistant_chat_owner_updated_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_chat_owner_updated_idx ON assistant.chat_conversation USING btree (owner_id, updated_at DESC);


--
-- Name: assistant_knowledge_active_locale_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_knowledge_active_locale_idx ON assistant.knowledge_document USING btree (active, visibility, locale, priority);


--
-- Name: assistant_knowledge_archive_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_knowledge_archive_idx ON assistant.knowledge_document USING btree (active, archived_at, updated_at DESC);


--
-- Name: assistant_knowledge_locale_priority_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_knowledge_locale_priority_idx ON assistant.knowledge_document USING btree (locale, priority);


--
-- Name: assistant_revision_one_published_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE UNIQUE INDEX assistant_revision_one_published_idx ON assistant.knowledge_document_revision USING btree (document_id) WHERE ((state)::text = 'PUBLISHED'::text);


--
-- Name: assistant_revision_published_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_revision_published_idx ON assistant.knowledge_document_revision USING btree (state, locale, priority);


--
-- Name: assistant_turn_active_conversation_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE UNIQUE INDEX assistant_turn_active_conversation_idx ON assistant.chat_turn_ledger USING btree (owner_id, conversation_id) WHERE ((conversation_id IS NOT NULL) AND ((state)::text = ANY ((ARRAY['RESERVED'::character varying, 'SNAPSHOT_READY'::character varying, 'DISPATCHED'::character varying])::text[])));


--
-- Name: assistant_turn_expiry_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_turn_expiry_idx ON assistant.chat_turn_ledger USING btree (state, tombstone_until, updated_at);


--
-- Name: assistant_turn_owner_request_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE UNIQUE INDEX assistant_turn_owner_request_idx ON assistant.chat_turn_ledger USING btree (owner_id, client_request_id);


--
-- Name: assistant_turn_owner_updated_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_turn_owner_updated_idx ON assistant.chat_turn_ledger USING btree (owner_id, updated_at DESC);


--
-- Name: assistant_usage_bucket_retention_idx; Type: INDEX; Schema: assistant; Owner: -
--

CREATE INDEX assistant_usage_bucket_retention_idx ON assistant.usage_bucket USING btree (bucket_date, scope);


--
-- Name: auth_challenge_token_hash_uq; Type: INDEX; Schema: campuscore_auth; Owner: -
--

CREATE UNIQUE INDEX auth_challenge_token_hash_uq ON campuscore_auth."AuthChallenge" USING btree ("tokenHash");


--
-- Name: auth_challenge_user_purpose_idx; Type: INDEX; Schema: campuscore_auth; Owner: -
--

CREATE INDEX auth_challenge_user_purpose_idx ON campuscore_auth."AuthChallenge" USING btree ("userId", purpose, "createdAt" DESC);


--
-- Name: auth_rate_limit_updated_idx; Type: INDEX; Schema: campuscore_auth; Owner: -
--

CREATE INDEX auth_rate_limit_updated_idx ON campuscore_auth."AuthRateLimitBucket" USING btree ("updatedAt");


--
-- Name: auth_session_refresh_idx; Type: INDEX; Schema: campuscore_auth; Owner: -
--

CREATE INDEX auth_session_refresh_idx ON campuscore_auth."Session" USING btree ("refreshToken");


--
-- Name: auth_session_refresh_unique; Type: INDEX; Schema: campuscore_auth; Owner: -
--

CREATE UNIQUE INDEX auth_session_refresh_unique ON campuscore_auth."Session" USING btree ("refreshToken");


--
-- Name: auth_session_user_idx; Type: INDEX; Schema: campuscore_auth; Owner: -
--

CREATE INDEX auth_session_user_idx ON campuscore_auth."Session" USING btree ("userId");


--
-- Name: engagement_announcement_publish_idx; Type: INDEX; Schema: engagement; Owner: -
--

CREATE INDEX engagement_announcement_publish_idx ON engagement."Announcement" USING btree ("publishAt", "expiresAt");


--
-- Name: notifications_user_read_idx; Type: INDEX; Schema: notifications; Owner: -
--

CREATE INDEX notifications_user_read_idx ON notifications.notification USING btree (user_id, is_read);


--
-- Name: thesis_group_round_idx; Type: INDEX; Schema: thesis; Owner: -
--

CREATE INDEX thesis_group_round_idx ON thesis.thesis_group USING btree (round_id, status, approval_status);


--
-- Name: thesis_round_status_idx; Type: INDEX; Schema: thesis; Owner: -
--

CREATE INDEX thesis_round_status_idx ON thesis.thesis_registration_round USING btree (status);


--
-- Name: thesis_topic_department_idx; Type: INDEX; Schema: thesis; Owner: -
--

CREATE INDEX thesis_topic_department_idx ON thesis.thesis_topic USING btree (department_id, status);


--
-- Name: thesis_topic_round_idx; Type: INDEX; Schema: thesis; Owner: -
--

CREATE INDEX thesis_topic_round_idx ON thesis.thesis_topic USING btree (round_id, status);


--
-- Name: Attendance Attendance_sectionId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Attendance"
    ADD CONSTRAINT "Attendance_sectionId_fkey" FOREIGN KEY ("sectionId") REFERENCES academic."Section"(id) ON DELETE CASCADE;


--
-- Name: Attendance Attendance_studentId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Attendance"
    ADD CONSTRAINT "Attendance_studentId_fkey" FOREIGN KEY ("studentId") REFERENCES academic."Student"(id) ON DELETE CASCADE;


--
-- Name: CourseRequirement CourseRequirement_courseId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."CourseRequirement"
    ADD CONSTRAINT "CourseRequirement_courseId_fkey" FOREIGN KEY ("courseId") REFERENCES academic."Course"(id) ON DELETE CASCADE;


--
-- Name: CourseRequirement CourseRequirement_requiredCourseId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."CourseRequirement"
    ADD CONSTRAINT "CourseRequirement_requiredCourseId_fkey" FOREIGN KEY ("requiredCourseId") REFERENCES academic."Course"(id);


--
-- Name: Course Course_departmentId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Course"
    ADD CONSTRAINT "Course_departmentId_fkey" FOREIGN KEY ("departmentId") REFERENCES academic."Department"(id);


--
-- Name: CurriculumCourse CurriculumCourse_courseId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."CurriculumCourse"
    ADD CONSTRAINT "CurriculumCourse_courseId_fkey" FOREIGN KEY ("courseId") REFERENCES academic."Course"(id) ON DELETE CASCADE;


--
-- Name: CurriculumCourse CurriculumCourse_curriculumId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."CurriculumCourse"
    ADD CONSTRAINT "CurriculumCourse_curriculumId_fkey" FOREIGN KEY ("curriculumId") REFERENCES academic."Curriculum"(id) ON DELETE CASCADE;


--
-- Name: Curriculum Curriculum_academicYearId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Curriculum"
    ADD CONSTRAINT "Curriculum_academicYearId_fkey" FOREIGN KEY ("academicYearId") REFERENCES academic."AcademicYear"(id);


--
-- Name: Curriculum Curriculum_departmentId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Curriculum"
    ADD CONSTRAINT "Curriculum_departmentId_fkey" FOREIGN KEY ("departmentId") REFERENCES academic."Department"(id);


--
-- Name: Department Department_facultyId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Department"
    ADD CONSTRAINT "Department_facultyId_fkey" FOREIGN KEY ("facultyId") REFERENCES academic."Faculty"(id);


--
-- Name: EnrollmentAudit EnrollmentAudit_operationId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."EnrollmentAudit"
    ADD CONSTRAINT "EnrollmentAudit_operationId_fkey" FOREIGN KEY ("operationId") REFERENCES academic."EnrollmentOperation"(id);


--
-- Name: EnrollmentAudit EnrollmentAudit_sectionId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."EnrollmentAudit"
    ADD CONSTRAINT "EnrollmentAudit_sectionId_fkey" FOREIGN KEY ("sectionId") REFERENCES academic."Section"(id);


--
-- Name: EnrollmentAudit EnrollmentAudit_studentId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."EnrollmentAudit"
    ADD CONSTRAINT "EnrollmentAudit_studentId_fkey" FOREIGN KEY ("studentId") REFERENCES academic."Student"(id);


--
-- Name: EnrollmentOperation EnrollmentOperation_studentId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."EnrollmentOperation"
    ADD CONSTRAINT "EnrollmentOperation_studentId_fkey" FOREIGN KEY ("studentId") REFERENCES academic."Student"(id);


--
-- Name: Enrollment Enrollment_sectionId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Enrollment"
    ADD CONSTRAINT "Enrollment_sectionId_fkey" FOREIGN KEY ("sectionId") REFERENCES academic."Section"(id);


--
-- Name: Enrollment Enrollment_semesterId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Enrollment"
    ADD CONSTRAINT "Enrollment_semesterId_fkey" FOREIGN KEY ("semesterId") REFERENCES academic."Semester"(id);


--
-- Name: Enrollment Enrollment_studentId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Enrollment"
    ADD CONSTRAINT "Enrollment_studentId_fkey" FOREIGN KEY ("studentId") REFERENCES academic."Student"(id);


--
-- Name: GradeItem GradeItem_sectionId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."GradeItem"
    ADD CONSTRAINT "GradeItem_sectionId_fkey" FOREIGN KEY ("sectionId") REFERENCES academic."Section"(id) ON DELETE CASCADE;


--
-- Name: Lecturer Lecturer_departmentId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Lecturer"
    ADD CONSTRAINT "Lecturer_departmentId_fkey" FOREIGN KEY ("departmentId") REFERENCES academic."Department"(id);


--
-- Name: Lecturer Lecturer_userId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Lecturer"
    ADD CONSTRAINT "Lecturer_userId_fkey" FOREIGN KEY ("userId") REFERENCES campuscore_auth."User"(id) ON DELETE CASCADE;


--
-- Name: RegistrationCohortWindow RegistrationCohortWindow_roundId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationCohortWindow"
    ADD CONSTRAINT "RegistrationCohortWindow_roundId_fkey" FOREIGN KEY ("roundId") REFERENCES academic."RegistrationRound"(id) ON DELETE CASCADE;


--
-- Name: RegistrationRound RegistrationRound_semesterId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationRound"
    ADD CONSTRAINT "RegistrationRound_semesterId_fkey" FOREIGN KEY ("semesterId") REFERENCES academic."Semester"(id);


--
-- Name: RegistrationSlip RegistrationSlip_roundId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationSlip"
    ADD CONSTRAINT "RegistrationSlip_roundId_fkey" FOREIGN KEY ("roundId") REFERENCES academic."RegistrationRound"(id);


--
-- Name: RegistrationSlip RegistrationSlip_studentId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."RegistrationSlip"
    ADD CONSTRAINT "RegistrationSlip_studentId_fkey" FOREIGN KEY ("studentId") REFERENCES academic."Student"(id);


--
-- Name: SectionSchedule SectionSchedule_classroomId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."SectionSchedule"
    ADD CONSTRAINT "SectionSchedule_classroomId_fkey" FOREIGN KEY ("classroomId") REFERENCES academic."Classroom"(id);


--
-- Name: SectionSchedule SectionSchedule_sectionId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."SectionSchedule"
    ADD CONSTRAINT "SectionSchedule_sectionId_fkey" FOREIGN KEY ("sectionId") REFERENCES academic."Section"(id) ON DELETE CASCADE;


--
-- Name: Section Section_classroomId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Section"
    ADD CONSTRAINT "Section_classroomId_fkey" FOREIGN KEY ("classroomId") REFERENCES academic."Classroom"(id);


--
-- Name: Section Section_courseId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Section"
    ADD CONSTRAINT "Section_courseId_fkey" FOREIGN KEY ("courseId") REFERENCES academic."Course"(id);


--
-- Name: Section Section_lecturerId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Section"
    ADD CONSTRAINT "Section_lecturerId_fkey" FOREIGN KEY ("lecturerId") REFERENCES academic."Lecturer"(id);


--
-- Name: Section Section_semesterId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Section"
    ADD CONSTRAINT "Section_semesterId_fkey" FOREIGN KEY ("semesterId") REFERENCES academic."Semester"(id);


--
-- Name: Semester Semester_academicYearId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Semester"
    ADD CONSTRAINT "Semester_academicYearId_fkey" FOREIGN KEY ("academicYearId") REFERENCES academic."AcademicYear"(id);


--
-- Name: StudentGrade StudentGrade_enrollmentId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."StudentGrade"
    ADD CONSTRAINT "StudentGrade_enrollmentId_fkey" FOREIGN KEY ("enrollmentId") REFERENCES academic."Enrollment"(id) ON DELETE CASCADE;


--
-- Name: StudentGrade StudentGrade_gradeItemId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."StudentGrade"
    ADD CONSTRAINT "StudentGrade_gradeItemId_fkey" FOREIGN KEY ("gradeItemId") REFERENCES academic."GradeItem"(id) ON DELETE CASCADE;


--
-- Name: Student Student_curriculumId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Student"
    ADD CONSTRAINT "Student_curriculumId_fkey" FOREIGN KEY ("curriculumId") REFERENCES academic."Curriculum"(id);


--
-- Name: Student Student_userId_fkey; Type: FK CONSTRAINT; Schema: academic; Owner: -
--

ALTER TABLE ONLY academic."Student"
    ADD CONSTRAINT "Student_userId_fkey" FOREIGN KEY ("userId") REFERENCES campuscore_auth."User"(id) ON DELETE CASCADE;


--
-- Name: chat_citation chat_citation_message_id_fkey; Type: FK CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_citation
    ADD CONSTRAINT chat_citation_message_id_fkey FOREIGN KEY (message_id) REFERENCES assistant.chat_message(id) ON DELETE CASCADE;


--
-- Name: chat_message chat_message_conversation_id_fkey; Type: FK CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_message
    ADD CONSTRAINT chat_message_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES assistant.chat_conversation(id) ON DELETE CASCADE;


--
-- Name: chat_message_feedback chat_message_feedback_message_id_fkey; Type: FK CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_message_feedback
    ADD CONSTRAINT chat_message_feedback_message_id_fkey FOREIGN KEY (message_id) REFERENCES assistant.chat_message(id) ON DELETE CASCADE;


--
-- Name: chat_turn_ledger chat_turn_ledger_conversation_id_fkey; Type: FK CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_turn_ledger
    ADD CONSTRAINT chat_turn_ledger_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES assistant.chat_conversation(id) ON DELETE SET NULL;


--
-- Name: chat_turn_ledger chat_turn_ledger_result_message_id_fkey; Type: FK CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.chat_turn_ledger
    ADD CONSTRAINT chat_turn_ledger_result_message_id_fkey FOREIGN KEY (result_message_id) REFERENCES assistant.chat_message(id) ON DELETE SET NULL;


--
-- Name: knowledge_document_audit knowledge_document_audit_revision_id_fkey; Type: FK CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.knowledge_document_audit
    ADD CONSTRAINT knowledge_document_audit_revision_id_fkey FOREIGN KEY (revision_id) REFERENCES assistant.knowledge_document_revision(id) ON DELETE CASCADE;


--
-- Name: knowledge_document_revision knowledge_document_revision_document_id_fkey; Type: FK CONSTRAINT; Schema: assistant; Owner: -
--

ALTER TABLE ONLY assistant.knowledge_document_revision
    ADD CONSTRAINT knowledge_document_revision_document_id_fkey FOREIGN KEY (document_id) REFERENCES assistant.knowledge_document(id) ON DELETE CASCADE;


--
-- Name: AuthChallenge AuthChallenge_userId_fkey; Type: FK CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."AuthChallenge"
    ADD CONSTRAINT "AuthChallenge_userId_fkey" FOREIGN KEY ("userId") REFERENCES campuscore_auth."User"(id) ON DELETE CASCADE;


--
-- Name: RolePermission RolePermission_permissionId_fkey; Type: FK CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."RolePermission"
    ADD CONSTRAINT "RolePermission_permissionId_fkey" FOREIGN KEY ("permissionId") REFERENCES campuscore_auth."Permission"(id) ON DELETE CASCADE;


--
-- Name: RolePermission RolePermission_roleId_fkey; Type: FK CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."RolePermission"
    ADD CONSTRAINT "RolePermission_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES campuscore_auth."Role"(id) ON DELETE CASCADE;


--
-- Name: Session Session_userId_fkey; Type: FK CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."Session"
    ADD CONSTRAINT "Session_userId_fkey" FOREIGN KEY ("userId") REFERENCES campuscore_auth."User"(id) ON DELETE CASCADE;


--
-- Name: UserRole UserRole_roleId_fkey; Type: FK CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."UserRole"
    ADD CONSTRAINT "UserRole_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES campuscore_auth."Role"(id) ON DELETE CASCADE;


--
-- Name: UserRole UserRole_userId_fkey; Type: FK CONSTRAINT; Schema: campuscore_auth; Owner: -
--

ALTER TABLE ONLY campuscore_auth."UserRole"
    ADD CONSTRAINT "UserRole_userId_fkey" FOREIGN KEY ("userId") REFERENCES campuscore_auth."User"(id) ON DELETE CASCADE;


--
-- Name: notification notification_user_id_fkey; Type: FK CONSTRAINT; Schema: notifications; Owner: -
--

ALTER TABLE ONLY notifications.notification
    ADD CONSTRAINT notification_user_id_fkey FOREIGN KEY (user_id) REFERENCES campuscore_auth."User"(id) ON DELETE CASCADE;


--
-- Name: thesis_group_member thesis_group_member_group_id_fkey; Type: FK CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_group_member
    ADD CONSTRAINT thesis_group_member_group_id_fkey FOREIGN KEY (group_id) REFERENCES thesis.thesis_group(id) ON DELETE CASCADE;


--
-- Name: thesis_group_member thesis_group_member_round_id_fkey; Type: FK CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_group_member
    ADD CONSTRAINT thesis_group_member_round_id_fkey FOREIGN KEY (round_id) REFERENCES thesis.thesis_registration_round(id);


--
-- Name: thesis_group thesis_group_round_id_fkey; Type: FK CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_group
    ADD CONSTRAINT thesis_group_round_id_fkey FOREIGN KEY (round_id) REFERENCES thesis.thesis_registration_round(id);


--
-- Name: thesis_group thesis_group_topic_id_fkey; Type: FK CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_group
    ADD CONSTRAINT thesis_group_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES thesis.thesis_topic(id);


--
-- Name: thesis_topic thesis_topic_round_id_fkey; Type: FK CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_topic
    ADD CONSTRAINT thesis_topic_round_id_fkey FOREIGN KEY (round_id) REFERENCES thesis.thesis_registration_round(id);


--
-- Name: thesis_topic_supervisor thesis_topic_supervisor_topic_id_fkey; Type: FK CONSTRAINT; Schema: thesis; Owner: -
--

ALTER TABLE ONLY thesis.thesis_topic_supervisor
    ADD CONSTRAINT thesis_topic_supervisor_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES thesis.thesis_topic(id) ON DELETE CASCADE;


-- End of CampusCore Supabase baseline.
