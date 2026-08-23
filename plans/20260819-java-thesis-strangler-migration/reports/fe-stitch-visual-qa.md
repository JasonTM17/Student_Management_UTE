# FE Stitch visual QA

Date: 2026-08-21T12:46:59.288Z
Base URL: `http://127.0.0.1:8088`
API URL: `http://127.0.0.1:8088/api/v1`

## Summary

- Unique routes captured: 38
- Viewports captured: 3
- Screenshots captured: 114
- Passed captures: 36
- Failed captures: 78
- Horizontal overflow findings: 0
- Missing expected mobile bottom navigation: 1
- Console-error route captures: 27
- Failed-request route captures: 69

## Route matrix

Status | Viewport | Role | Route | Resolved route | HTTP | Overflow X | Mobile bottom nav | Screenshot
--- | --- | --- | --- | --- | ---: | ---: | --- | ---
FAIL | desktop | public | / | / | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-public-home.png)
FAIL | desktop | public | /login | /login | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-public-login.png)
FAIL | desktop | public | /forgot-password | /forgot-password | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-public-forgot-password.png)
FAIL | desktop | public | /reset-password | /reset-password | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-public-reset-password.png)
FAIL | desktop | student | /dashboard | /dashboard | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard.png)
FAIL | desktop | student | /dashboard/register | /dashboard/register | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-register.png)
FAIL | desktop | student | /dashboard/enrollments | /dashboard/enrollments | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-enrollments.png)
FAIL | desktop | student | /dashboard/schedule | /dashboard/schedule | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-schedule.png)
FAIL | desktop | student | /dashboard/grades | /dashboard/grades | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-grades.png)
FAIL | desktop | student | /dashboard/transcript | /dashboard/transcript | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-transcript.png)
FAIL | desktop | student | /dashboard/invoices | /dashboard/invoices | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-invoices.png)
FAIL | desktop | student | /dashboard/announcements | /dashboard/announcements | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-announcements.png)
FAIL | desktop | student | /dashboard/notifications | /dashboard/notifications | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-notifications.png)
FAIL | desktop | student | /dashboard/profile | /dashboard/profile | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-profile.png)
FAIL | desktop | student | /dashboard/sign-out | /dashboard/sign-out | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-sign-out.png)
FAIL | desktop | student | /dashboard/thesis | /dashboard/thesis | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-thesis.png)
FAIL | desktop | student | /dashboard/thesis/topics | /dashboard/thesis/topics | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-thesis-topics.png)
FAIL | desktop | student | /dashboard/thesis/topics/[id] | /dashboard/thesis/topics/[id] | n/a | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-thesis-topics-id.png)
FAIL | desktop | student | /dashboard/thesis/progress | /dashboard/thesis/progress | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-thesis-progress.png)
FAIL | desktop | student | /dashboard/thesis/evaluation | /dashboard/thesis/evaluation | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-student-dashboard-thesis-evaluation.png)
FAIL | desktop | lecturer | /dashboard/lecturer | /dashboard/lecturer | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-lecturer-dashboard-lecturer.png)
PASS | desktop | lecturer | /dashboard/lecturer/schedule | /dashboard/lecturer/schedule | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-lecturer-dashboard-lecturer-schedule.png)
FAIL | desktop | lecturer | /dashboard/lecturer/announcements | /dashboard/lecturer/announcements | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-lecturer-dashboard-lecturer-announcements.png)
FAIL | desktop | lecturer | /dashboard/lecturer/grades | /dashboard/lecturer/grades | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-lecturer-dashboard-lecturer-grades.png)
FAIL | desktop | lecturer | /dashboard/lecturer/grades/[id] | /dashboard/lecturer/grades/481ce055-a4fd-4f03-8970-c0677d9bf1b1 | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-lecturer-dashboard-lecturer-grades-id.png)
FAIL | desktop | admin | /admin | /admin | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin.png)
PASS | desktop | admin | /admin/users | /admin/users | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-users.png)
PASS | desktop | admin | /admin/courses | /admin/courses | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-courses.png)
PASS | desktop | admin | /admin/academic-years | /admin/academic-years | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-academic-years.png)
PASS | desktop | admin | /admin/classrooms | /admin/classrooms | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-classrooms.png)
PASS | desktop | admin | /admin/departments | /admin/departments | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-departments.png)
PASS | desktop | admin | /admin/enrollments | /admin/enrollments | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-enrollments.png)
PASS | desktop | admin | /admin/lecturers | /admin/lecturers | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-lecturers.png)
PASS | desktop | admin | /admin/sections | /admin/sections | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-sections.png)
PASS | desktop | admin | /admin/semesters | /admin/semesters | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-semesters.png)
PASS | desktop | admin | /admin/announcements | /admin/announcements | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-announcements.png)
PASS | desktop | admin | /admin/invoices | /admin/invoices | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-invoices.png)
PASS | desktop | admin | /admin/analytics | /admin/analytics | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/desktop-admin-admin-analytics.png)
FAIL | mobile | public | / | / | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-public-home.png)
FAIL | mobile | public | /login | /login | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-public-login.png)
FAIL | mobile | public | /forgot-password | /forgot-password | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-public-forgot-password.png)
FAIL | mobile | public | /reset-password | /reset-password | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-public-reset-password.png)
FAIL | mobile | student | /dashboard | /dashboard | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard.png)
FAIL | mobile | student | /dashboard/register | /dashboard/register | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-register.png)
FAIL | mobile | student | /dashboard/enrollments | /dashboard/enrollments | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-enrollments.png)
FAIL | mobile | student | /dashboard/schedule | /dashboard/schedule | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-schedule.png)
FAIL | mobile | student | /dashboard/grades | /dashboard/grades | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-grades.png)
FAIL | mobile | student | /dashboard/transcript | /dashboard/transcript | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-transcript.png)
FAIL | mobile | student | /dashboard/invoices | /dashboard/invoices | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-invoices.png)
FAIL | mobile | student | /dashboard/announcements | /dashboard/announcements | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-announcements.png)
FAIL | mobile | student | /dashboard/notifications | /dashboard/notifications | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-notifications.png)
FAIL | mobile | student | /dashboard/profile | /dashboard/profile | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-profile.png)
FAIL | mobile | student | /dashboard/sign-out | /dashboard/sign-out | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-sign-out.png)
FAIL | mobile | student | /dashboard/thesis | /dashboard/thesis | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-thesis.png)
FAIL | mobile | student | /dashboard/thesis/topics | /dashboard/thesis/topics | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-thesis-topics.png)
FAIL | mobile | student | /dashboard/thesis/topics/[id] | /dashboard/thesis/topics/[id] | n/a | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-thesis-topics-id.png)
FAIL | mobile | student | /dashboard/thesis/progress | /dashboard/thesis/progress | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-thesis-progress.png)
FAIL | mobile | student | /dashboard/thesis/evaluation | /dashboard/thesis/evaluation | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-student-dashboard-thesis-evaluation.png)
FAIL | mobile | lecturer | /dashboard/lecturer | /dashboard/lecturer | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-lecturer-dashboard-lecturer.png)
FAIL | mobile | lecturer | /dashboard/lecturer/schedule | /dashboard/lecturer/schedule | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-lecturer-dashboard-lecturer-schedule.png)
FAIL | mobile | lecturer | /dashboard/lecturer/announcements | /dashboard/lecturer/announcements | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-lecturer-dashboard-lecturer-announcements.png)
PASS | mobile | lecturer | /dashboard/lecturer/grades | /dashboard/lecturer/grades | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-lecturer-dashboard-lecturer-grades.png)
FAIL | mobile | lecturer | /dashboard/lecturer/grades/[id] | /dashboard/lecturer/grades/481ce055-a4fd-4f03-8970-c0677d9bf1b1 | 200 | 0 | yes | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-lecturer-dashboard-lecturer-grades-id.png)
PASS | mobile | admin | /admin | /admin | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin.png)
PASS | mobile | admin | /admin/users | /admin/users | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-users.png)
FAIL | mobile | admin | /admin/courses | /admin/courses | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-courses.png)
PASS | mobile | admin | /admin/academic-years | /admin/academic-years | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-academic-years.png)
PASS | mobile | admin | /admin/classrooms | /admin/classrooms | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-classrooms.png)
PASS | mobile | admin | /admin/departments | /admin/departments | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-departments.png)
FAIL | mobile | admin | /admin/enrollments | /admin/enrollments | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-enrollments.png)
FAIL | mobile | admin | /admin/lecturers | /admin/lecturers | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-lecturers.png)
PASS | mobile | admin | /admin/sections | /admin/sections | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-sections.png)
PASS | mobile | admin | /admin/semesters | /admin/semesters | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-semesters.png)
PASS | mobile | admin | /admin/announcements | /admin/announcements | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-announcements.png)
PASS | mobile | admin | /admin/invoices | /admin/invoices | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-invoices.png)
FAIL | mobile | admin | /admin/analytics | /admin/analytics | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/mobile-admin-admin-analytics.png)
FAIL | tablet | public | / | / | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-public-home.png)
FAIL | tablet | public | /login | /login | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-public-login.png)
FAIL | tablet | public | /forgot-password | /forgot-password | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-public-forgot-password.png)
FAIL | tablet | public | /reset-password | /reset-password | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-public-reset-password.png)
FAIL | tablet | student | /dashboard | /dashboard | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard.png)
FAIL | tablet | student | /dashboard/register | /dashboard/register | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-register.png)
FAIL | tablet | student | /dashboard/enrollments | /dashboard/enrollments | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-enrollments.png)
FAIL | tablet | student | /dashboard/schedule | /dashboard/schedule | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-schedule.png)
FAIL | tablet | student | /dashboard/grades | /dashboard/grades | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-grades.png)
FAIL | tablet | student | /dashboard/transcript | /dashboard/transcript | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-transcript.png)
FAIL | tablet | student | /dashboard/invoices | /dashboard/invoices | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-invoices.png)
FAIL | tablet | student | /dashboard/announcements | /dashboard/announcements | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-announcements.png)
FAIL | tablet | student | /dashboard/notifications | /dashboard/notifications | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-notifications.png)
FAIL | tablet | student | /dashboard/profile | /dashboard/profile | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-profile.png)
FAIL | tablet | student | /dashboard/sign-out | /dashboard/sign-out | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-sign-out.png)
FAIL | tablet | student | /dashboard/thesis | /dashboard/thesis | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-thesis.png)
FAIL | tablet | student | /dashboard/thesis/topics | /dashboard/thesis/topics | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-thesis-topics.png)
FAIL | tablet | student | /dashboard/thesis/topics/[id] | /dashboard/thesis/topics/[id] | n/a | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-thesis-topics-id.png)
FAIL | tablet | student | /dashboard/thesis/progress | /dashboard/thesis/progress | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-thesis-progress.png)
FAIL | tablet | student | /dashboard/thesis/evaluation | /dashboard/thesis/evaluation | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-student-dashboard-thesis-evaluation.png)
FAIL | tablet | lecturer | /dashboard/lecturer | /dashboard/lecturer | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-lecturer-dashboard-lecturer.png)
FAIL | tablet | lecturer | /dashboard/lecturer/schedule | /dashboard/lecturer/schedule | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-lecturer-dashboard-lecturer-schedule.png)
PASS | tablet | lecturer | /dashboard/lecturer/announcements | /dashboard/lecturer/announcements | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-lecturer-dashboard-lecturer-announcements.png)
FAIL | tablet | lecturer | /dashboard/lecturer/grades | /dashboard/lecturer/grades | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-lecturer-dashboard-lecturer-grades.png)
FAIL | tablet | lecturer | /dashboard/lecturer/grades/[id] | /dashboard/lecturer/grades/481ce055-a4fd-4f03-8970-c0677d9bf1b1 | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-lecturer-dashboard-lecturer-grades-id.png)
PASS | tablet | admin | /admin | /admin | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin.png)
PASS | tablet | admin | /admin/users | /admin/users | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-users.png)
PASS | tablet | admin | /admin/courses | /admin/courses | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-courses.png)
PASS | tablet | admin | /admin/academic-years | /admin/academic-years | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-academic-years.png)
PASS | tablet | admin | /admin/classrooms | /admin/classrooms | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-classrooms.png)
FAIL | tablet | admin | /admin/departments | /admin/departments | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-departments.png)
PASS | tablet | admin | /admin/enrollments | /admin/enrollments | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-enrollments.png)
PASS | tablet | admin | /admin/lecturers | /admin/lecturers | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-lecturers.png)
PASS | tablet | admin | /admin/sections | /admin/sections | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-sections.png)
PASS | tablet | admin | /admin/semesters | /admin/semesters | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-semesters.png)
PASS | tablet | admin | /admin/announcements | /admin/announcements | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-announcements.png)
PASS | tablet | admin | /admin/invoices | /admin/invoices | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-invoices.png)
PASS | tablet | admin | /admin/analytics | /admin/analytics | 200 | 0 | no | [png](../assets/fe-stitch-visual-qa/screenshots/tablet-admin-admin-analytics.png)

## Findings

- desktop /: noConsoleErrors
- desktop /login: noConsoleErrors, noFailedRequests
- desktop /forgot-password: noConsoleErrors, noFailedRequests
- desktop /reset-password: noConsoleErrors, noFailedRequests
- desktop /dashboard: noFailedRequests
- desktop /dashboard/register: noFailedRequests
- desktop /dashboard/enrollments: noFailedRequests
- desktop /dashboard/schedule: noFailedRequests
- desktop /dashboard/grades: noFailedRequests
- desktop /dashboard/transcript: noFailedRequests
- desktop /dashboard/invoices: noFailedRequests
- desktop /dashboard/announcements: noFailedRequests
- desktop /dashboard/notifications: noFailedRequests
- desktop /dashboard/profile: noFailedRequests
- desktop /dashboard/sign-out: authenticatedRouteStayedAuthed, noFailedRequests
- desktop /dashboard/thesis: noConsoleErrors, noFailedRequests
- desktop /dashboard/thesis/topics: noConsoleErrors, noFailedRequests
- desktop /dashboard/thesis/topics/[id]: navigationOk, noConsoleErrors, noFailedRequests
- desktop /dashboard/thesis/progress: noConsoleErrors, noFailedRequests
- desktop /dashboard/thesis/evaluation: noConsoleErrors, noFailedRequests
- desktop /dashboard/lecturer: noFailedRequests
- desktop /dashboard/lecturer/announcements: noFailedRequests
- desktop /dashboard/lecturer/grades: noFailedRequests
- desktop /dashboard/lecturer/grades/[id]: noFailedRequests
- desktop /admin: noFailedRequests
- mobile /: noConsoleErrors
- mobile /login: noConsoleErrors
- mobile /forgot-password: noConsoleErrors, noFailedRequests
- mobile /reset-password: noConsoleErrors
- mobile /dashboard: noFailedRequests
- mobile /dashboard/register: noFailedRequests
- mobile /dashboard/enrollments: noFailedRequests
- mobile /dashboard/schedule: noFailedRequests
- mobile /dashboard/grades: noFailedRequests
- mobile /dashboard/transcript: noFailedRequests
- mobile /dashboard/invoices: noFailedRequests
- mobile /dashboard/announcements: noFailedRequests
- mobile /dashboard/notifications: noFailedRequests
- mobile /dashboard/profile: noFailedRequests
- mobile /dashboard/sign-out: authenticatedRouteStayedAuthed, mobileBottomNavOk, noFailedRequests
- mobile /dashboard/thesis: noConsoleErrors
- mobile /dashboard/thesis/topics: noConsoleErrors, noFailedRequests
- mobile /dashboard/thesis/topics/[id]: navigationOk, noConsoleErrors, noFailedRequests
- mobile /dashboard/thesis/progress: noConsoleErrors, noFailedRequests
- mobile /dashboard/thesis/evaluation: noConsoleErrors, noFailedRequests
- mobile /dashboard/lecturer: noFailedRequests
- mobile /dashboard/lecturer/schedule: noFailedRequests
- mobile /dashboard/lecturer/announcements: noFailedRequests
- mobile /dashboard/lecturer/grades/[id]: noFailedRequests
- mobile /admin/courses: noFailedRequests
- mobile /admin/enrollments: noFailedRequests
- mobile /admin/lecturers: noFailedRequests
- mobile /admin/analytics: noFailedRequests
- tablet /: noConsoleErrors
- tablet /login: noConsoleErrors
- tablet /forgot-password: noConsoleErrors
- tablet /reset-password: noConsoleErrors
- tablet /dashboard: noFailedRequests
- tablet /dashboard/register: noFailedRequests
- tablet /dashboard/enrollments: noFailedRequests
- tablet /dashboard/schedule: noFailedRequests
- tablet /dashboard/grades: noFailedRequests
- tablet /dashboard/transcript: noFailedRequests
- tablet /dashboard/invoices: noFailedRequests
- tablet /dashboard/announcements: noFailedRequests
- tablet /dashboard/notifications: noFailedRequests
- tablet /dashboard/profile: noFailedRequests
- tablet /dashboard/sign-out: authenticatedRouteStayedAuthed, noFailedRequests
- tablet /dashboard/thesis: noConsoleErrors, noFailedRequests
- tablet /dashboard/thesis/topics: noConsoleErrors, noFailedRequests
- tablet /dashboard/thesis/topics/[id]: navigationOk, noConsoleErrors, noFailedRequests
- tablet /dashboard/thesis/progress: noConsoleErrors, noFailedRequests
- tablet /dashboard/thesis/evaluation: noConsoleErrors, noFailedRequests
- tablet /dashboard/lecturer: noFailedRequests
- tablet /dashboard/lecturer/schedule: noFailedRequests
- tablet /dashboard/lecturer/grades: noFailedRequests
- tablet /dashboard/lecturer/grades/[id]: noFailedRequests
- tablet /admin/departments: noFailedRequests
