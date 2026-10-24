# Thesis group governance

Group governance follows responsibility for the students and their faculty.
Holding `TRUONG_KHOA` must not grant institution-wide control over groups:
`ADMIN` and `SUPER_ADMIN` retain that authority, while a faculty head may manage
members, assign a topic, update progress, approve or reject only within their
own faculty. The cross-faculty exception is a current, explicit supervising
lecturer (GVHD) assignment when the faculty head also holds `LECTURER`.
Council or counter-reviewer assignments alone do not confer group-write authority.

Faculty authority requires a current, active lecturer profile bound to the
JWT subject; a missing, inactive or borrowed profile must fail closed (`403`).
An unassigned foreign group remains undisclosed (`404`). Faculty ownership
comes from the topic's department or, for a topicless group, its members'
curricula. This preserves governance of draft groups before topic selection.

Topic assignment must respect both the existing group's boundary and the
destination topic's boundary. Access to an own-faculty group must not become
a way to retarget it to a foreign topic without a qualifying GVHD assignment.
Existing round, group-state and roster constraints still apply.

This policy preserves a plain `LECTURER`'s existing assigned-supervisor roster
and review permissions; it does not grant lecturers new progress or topic
assignment powers. It covers these group mutations only. Round, topic and
council lifecycle authority remains a separate contract. Report submission
and upload remain group-leader-only; report read authority is documented in
[report storage and recovery](thesis-report-storage.md#artifact-integrity-and-concurrent-changes).

The counter-review waiver ("Miễn phản biện") remains implemented but is
disabled for every shipped round type: undergraduate rounds always require a
counter-reviewer, so the waive endpoint answers
`409 COUNTER_REVIEW_WAIVER_NOT_SUPPORTED` and the admin UI hides the action.
Stored waivers still work — revoke, scoring, and publication treat them as
before. A future postgraduate round type opts in by returning `true` from
`RoundType.allowsCounterReviewWaiver()`; an operator override also exists via
`thesis.governance.counter-review-waiver.enabled`
(`THESIS_GOVERNANCE_COUNTER_REVIEW_WAIVER_ENABLED`, default `false`). The
single owner is `ThesisGovernanceCapabilities`, which feeds both the write
path and the round read model (`counterReviewWaiverAllowed`).

## Registration eligibility (accumulated credits + CPA)

A student may register (create a group, be added as a member, or pick the
group's topic) only when their published transcript meets the regulation's
condition: at least `thesis.eligibility.min-credits` accumulated credits
(default `110`) and a cumulative GPA of at least `thesis.eligibility.min-cpa`
(default `2.0`, 4.0 scale). Both numbers come from the canonical transcript
summary — published grades only, best attempt per course, letter `F` earns
nothing. A student with no published academic data fails closed
(`ACADEMIC_DATA_UNAVAILABLE`).

- `thesis.eligibility.enforcement` = `ENFORCE` (default) refuses the mutation
  with `409 THESIS_ELIGIBILITY_NOT_MET` plus `details` carrying
  `earnedCredits`, `requiredCredits`, `cpa`, `requiredCpa` and a `reasons`
  list. `ADVISORY` still evaluates and reports in the read model but never
  blocks.
- The round read model carries `eligibility` for student callers; group
  members carry an `eligible` flag for roster chips. No extra round trip.
- Governance escape hatches are deliberately not blocked: the audited roster
  override admits an ineligible student, and the audit row records the
  ineligibility note (`student does not meet thesis eligibility (...)` +
  `studentEligible=false`). A reason remains required, as before.
- The regulation's academic-warning and disciplinary-sanction prerequisites
  have no data model in this schema, so they are not evaluated.

## Executable owners

- [Group mutations](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationService.java):
  `isGroupAdmin`, `requireDeanScope` and `requireDeanTopicAccess` own the group
  authorization boundary; the separate `isAdmin` owner governs other lifecycle operations.
- [Eligibility policy](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisEligibilityService.java)
  owns the credit/CPA rule; the batch transcript summary lives in
  `AcademicEnrollmentReadService.findTranscriptSummaries`, reusing the
  single-source grade reduction.
- [Faculty identity and ownership](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/repository/ThesisGroupReadRepository.java):
  `findLecturerScope` and `groupInFaculty`.
- [Group mutation faculty-scope regressions](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisGroupMutationFacultyScopeTest.java)
  own the positive and negative authorization checks. Their presence does not
  establish a passing run, runtime acceptance or deployment.
