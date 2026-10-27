# 2. Academic workflow techniques

[Tiếng Việt](academic.vi.md) · [Index](README.en.md) · [Next: theses](thesis.en.md)

## T11. Enrollment: locking both the student and the section

**Problem.** A separate availability check and insert can overbook a class. Two enrollments into different sections can also concurrently exceed one student's credit limit or create a timetable conflict. Locking only the section cannot solve both races.

**Current write flow.** `AcademicMutationService.enroll` delegates to `RegistrationService`, receiving the authenticated student identity, section ID and idempotency key. Inside a transaction: claim/replay the request → lock Student → lock Section → check semester/round/cohort → inspect active enrollments → check duplicate section/course, credits, prerequisites/corequisites and schedules → conditionally increment capacity usage → insert Enrollment → record EnrollmentEvent and complete idempotency. Related state mutations must use compatible lock order.

The increment succeeds only while `enrolledCount < capacity` and section status is `OPEN`. Zero affected rows produces `409 SECTION_FULL`; later failures must roll back the increment too. Partial unique indexes enforce active-record uniqueness in PostgreSQL, supplementing service checks. This is concurrency control, not a claim that `FOR UPDATE` makes every table serializable or eliminates every isolation anomaly.

**Owners:** [RegistrationService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/RegistrationService.java), [facade](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicMutationService.java), [migrations](../../java-services/restful-api/src/main/resources/db/migration/README.md), [enrollment persistence tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/academic/AcademicEnrollmentMutationPersistenceTest.java).

## T12. Idempotency, snapshots and registration slips

**Input/output.** A key represents one operation intent; a request hash binds that key to its payload. The same owner/key/hash can replay the existing enrollment. Reusing a key for another payload is a conflict, not a new request. A network retry keeps its key; a different operation gets a different key.

Enrollment stores snapshots such as course, credits and round so history does not rely entirely on mutable catalog data. EnrollmentEvent records action and actor. Registration slips are PDFs with SHA-256; the read path locks and rechecks the relevant enrollment set to avoid publishing a stale slip after section cancellation. A checksum proves byte consistency, not download authority.

Seat counts are derived data. The reconcile job compares them with active-enrollment counts and warns about drift; automatic repair defaults off. Seeds or out-of-band administration can therefore require operational reconciliation. “Four layers” is not an unconditional claim that every database counter is always correct.

**Owners:** [idempotency/slips](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/RegistrationService.java), [PDF renderer](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/renderer/RegistrationPdfRenderer.java), [reconcile job](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/job/EnrolledCountReconcileJob.java).

## T13. Registration catalogs, schedules and curriculum plans

Catalog responses include schedules, rooms, lecturers, capacity and curriculum relevance. One fan-out query is grouped into sections instead of fetching schedules once per card. This controls N+1 queries; the source query budget is not a measured production latency claim.

Curriculum planning maps a student's academic year/term to mandatory courses, proposes open sections with seats and compatible schedules, and explains skips such as already completed, missing prerequisites, full sections or insufficient credit budget. Plan-based registration reuses the ordinary locked write path instead of bypassing its rules.

Administrators use **dry-run → apply**, bounded batches, resume cursors and audit. Per-student transactions keep a single failure from turning the entire roster into a long transaction. Stable keys support safe reruns; `CURRICULUM_DEFAULT` identifies where an enrollment originated. A course deliberately dropped by the student is not silently restored on the next apply. There is no automatic background enrollment schedule replacing the academic office's decision.

**Owners:** [catalog/registration](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/RegistrationService.java), [curriculum service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/CurriculumDefaultRegistrationService.java), [repository](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/repository/CurriculumDefaultRegistrationRepository.java), [race checks](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/academic/CurriculumDefaultRegistrationRacePostgresIT.java).

## T14. Effective credit limits and two withdrawal paths

The server computes the effective limit from student, round, semester and configuration; dashboard labels do not authorize enrollment. Current code has a 28-credit standard, a 30-credit approved exception, summer limits and a separate demo override. Minimum-credit/slip-submission conditions are distinct from the ceiling for adding a course; one number cannot describe every stage.

A **self-service drop** within the permitted window releases a seat and updates current enrollment. An **approved withdrawal application** is a `PENDING` record reviewed by authorized staff; approval preserves a transcript entry with grade `W`. W remains historical but contributes neither GPA, earned credits nor best-attempt selection. Review locks and rechecks state so two reviewers cannot independently decide the same pending record.

Cancelled sections retain history. Reopening does not imply resurrection of every enrollment that has been replaced. Read models and mobile share active-enrollment vocabulary so withdrawn/cancelled history is not counted as current study.

**Owners:** [credit-limit policy](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/CreditLimitApplicationService.java), [withdrawal service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/WithdrawalApplicationService.java), [status owner](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/model/EnrollmentStatuses.java), [frontend status rules](../../frontend/src/lib/enrollment-status.ts).

## T15. Grade entry, publication and cumulative transcripts

**Writes.** Lecturers edit their assigned sections; administrators require the appropriate authority. The service locks related data, verifies enrollment/section membership and writable state, then stores components transactionally. Current grading assigns 50% each to process and final-exam scores. `BigDecimal` makes the rounding rule explicit. A process-only draft may exist without a complete final grade.

**Publication.** Saving a draft differs from publishing. Publication checks completeness and state; students must not see drafts as official results. Notification side effects occur after commit; an SMTP failure must not turn a committed grade into a reported save failure. The frontend maps backend business codes instead of hiding useful reasons behind a generic toast.

**Reads.** Transcripts include 10-point, letter and 4.0 conversions through `LetterGrades`. The cumulative reducer selects the best eligible attempt per course, multiplies by credits and divides by the credits participating in GPA. Semester blocks retain attempt history. F earns no credit; W is excluded from selection/GPA. Thesis eligibility reuses transcript summaries rather than inventing a second GPA calculation.

**Owners:** [grade writes](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicMutationService.java), [transcript reducer](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicEnrollmentReadService.java), [grade conversion](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/model/LetterGrades.java), [grade UI](../../frontend/src/app/dashboard/lecturer/grades/page.tsx).

## T16. Grade appeals and unpublishing

A student appeals their own enrollment with a published grade inside the allowed window. The server derives the deadline from `gradePublishedAt` and the configured duration, currently seven days by default. Browser timezone or page-open time must not replace the server's decision. The current contract permits one submission per enrollment; cancellation applies only while pending.

The assigned lecturer or an authorized fallback reviews the appeal; a student cannot review their own appeal. Acceptance may supply a corrected score within permitted range/precision. Rejection cannot carry a correction as though accepted. The service locks, rechecks state, updates the official result and consistently records audit/notification.

Unpublishing is a separate reasoned action with guards. A grade with a pending appeal or a correction from an accepted appeal cannot be unpublished to erase that decision's evidence. Grade editing, publication and appeal races need PostgreSQL interleaving tests, not only UI tests.

**Owners:** [GradeAppealService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/GradeAppealService.java), [persistence tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/academic/GradeAppealPersistenceTest.java), [race tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/academic/GradeAppealRacePostgresIT.java), [deadline UI](../../frontend/src/lib/grade-appeal-deadline.ts).

## T17. Attendance, conduct and advisory warnings

Attendance uses stable class-date keys and schedules, checking assigned lecturers, roster membership and edit windows. Its rate is `(PRESENT + LATE) / (PRESENT + LATE + ABSENT)`; EXCUSED appears in neither numerator nor denominator. Rounded percentages are display values. `AT_RISK` uses the exact threshold, avoiding the error where 35/44 displays as 80% and is incorrectly treated as meeting 80%.

Before enough sessions exist, the signal is `INSUFFICIENT_DATA`. This is advisory read-model information; it does not automatically ban exams, enrollment or grading. Enforcement would require a separate business contract.

Conduct views read semester/activity data and present five criteria, totals and classification. Lecturers read students they teach; students read their own record. This read module must not be described as a complete disciplinary or conduct-score approval system.

**Owners:** [attendance writes](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicAttendanceWriteService.java), [rate formula](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AttendanceRateCalculator.java), [advisory signal](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AttendanceEligibility.java), [conduct reads](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicConductService.java), [attendance date contract](../integrations/attendance-date-keys.md).

## T18. Imports, exports, catalogs and search

The grade-sheet parser accepts CSV, semicolon-separated files or tab-separated ranges pasted from Excel. It matches student code/email, validates scores, detects duplicates and reports skipped rows with reasons. Preview fills the form; authorized draft/publish API actions still persist changes. This is not a general `.xlsx` workbook reader.

Grade/attendance exports and iCalendar schedules have distinct formats. iCalendar escapes separators/newlines and generates recurring events from section schedules and semester limits. Only relevant active enrollments should create events. Backend PDF registration slips are separate from CSV downloads and browser transcript printing.

Academic years, semesters, faculties/departments, courses, rooms, lecturers and sections are related catalog entities. Fields whose changes would reinterpret existing enrollments have guards; the UI explains locked fields instead of accepting edits that later fail. Search uses text normalization and parameterized queries; a search feature does not imply a separate search engine.

**Owners:** [grade parser](../../frontend/src/lib/grade-sheet.ts), [grade export](../../frontend/src/lib/grade-export.ts), [attendance export](../../frontend/src/lib/attendance-export.ts), [calendar generator](../../frontend/src/lib/ical-generator.ts), [catalog writes](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AdminCatalogMutationService.java), [section-lock regression](../../frontend/tests/section-enrollment-lock.test.js).

Student certificates are rendered client-side from identity and loaded curriculum/profile data; missing values remain absent instead of being fabricated. Printable output alone does not establish digital signatures or a legally authoritative issuance workflow. **Owner:** [certificate screen](../../frontend/src/app/dashboard/certificates/page.tsx).
