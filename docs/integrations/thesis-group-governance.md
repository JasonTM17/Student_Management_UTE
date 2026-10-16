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

## Executable owners

- [Group mutations](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationService.java):
  `isGroupAdmin`, `requireDeanScope` and `requireDeanTopicAccess` own the group
  authorization boundary; the separate `isAdmin` owner governs other lifecycle operations.
- [Faculty identity and ownership](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/repository/ThesisGroupReadRepository.java):
  `findLecturerScope` and `groupInFaculty`.
- [Group mutation faculty-scope regressions](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisGroupMutationFacultyScopeTest.java)
  own the positive and negative authorization checks. Their presence does not
  establish a passing run, runtime acceptance or deployment.
