package io.campuscore.restfulapi.people.model;

/**
 * The student slice a non-admin caller may see: their own record, students
 * enrolled in sections they teach or reach through the
 * AcademicEnrollmentReadRepository.hasSectionAccess department/faculty
 * rules, and students in thesis groups whose topic they supervise, review,
 * or sit on a council for. Thesis lecturer keys match either stored form
 * (profile id or auth user id) the same way ThesisGroupReadRepository
 * resolves them.
 */
public record LecturerDirectoryScope(
        String lecturerId, String userId, boolean departmentScoped, boolean facultyScoped) { }
