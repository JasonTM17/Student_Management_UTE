# Attendance date keys

Attendance identifies a campus calendar day, rather than a moment when a class
begins. Java attendance API callers must use the same day for recording,
retrieval and `RESET` so a late-hour timestamp cannot select a different day's
student record.

A `YYYY-MM-DD` value names that campus date explicitly. An ISO-8601 instant
first resolves to its calendar date in `Asia/Ho_Chi_Minh`. The database key is
that date at UTC midnight: it is a date marker, not the instant obtained by
converting campus midnight to UTC.

For example, `2026-08-25T23:00:00Z` falls on August 26 at the campus and selects
the `2026-08-26T00:00:00Z` key. PUT, dated GET and `RESET` must agree on this key.
Resetting that day must leave a prior August 25 attendance record untouched.

Omitting the optional date on a read keeps all relevant rows within the
request's other filters and access scope. A missing or malformed required
write date remains a `400` response with `INVALID_ATTENDANCE_DATE`.
This contract does not call for a schema change or a rewrite of existing datasets.

## Executable owners

- [AttendanceDay](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/model/AttendanceDay.java)
  owns the shared campus-day interpretation.
- [Attendance reads](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicAttendanceReadService.java)
  and [attendance writes](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicAttendanceWriteService.java)
  own API normalization and validation.
- [Attendance persistence regressions](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/academic/AcademicAttendanceMutationPersistenceTest.java),
  especially `lateHourInstantReadsAndResetsTheSameCampusDayWithoutTouchingThePreviousDay`,
  own the cross-day read/reset check. Test source alone does not establish a
  passing run, current UI behavior, runtime acceptance or deployment.
