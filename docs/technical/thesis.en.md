# 3. Project and thesis techniques

[Tiếng Việt](thesis.vi.md) · [Index](README.en.md) · [Next: AI and communication](assistant-engagement.en.md)

## T19. Extracting responsibilities while preserving facade transactions

**Problem.** An oversized write service obscures lifecycle rules, authorization and lock order. Splitting files while opening independent transactions in helpers could commit half an operation before the rest fails.

`ThesisMutationService` is now the public facade retained by controllers and cross-module callers. Its `@Transactional` methods start the unit of work and delegate to five responsibilities: `ThesisRoundLifecycleService`, `ThesisPublicationService`, `ThesisTopicMutationService`, `ThesisGroupMembershipService` and `ThesisGroupGovernanceService`. Read/mutation repositories are separated by responsibility; `ThesisMutationSupport` holds shared supporting operations.

**Flow.** Controller → transactional facade → responsibility service checks rules/scope → repository locks/writes → audit joins the transaction → commit → notifications outside the core commit. Extraction does not create microservices or change the public contract. Read the facade for the operation, its delegate for policy, and the repository for SQL.

**Owners:** [facade and responsibility JavaDoc](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationService.java), [mutation support](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationSupport.java), [repositories](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/repository/).

## T20. Round-type state machines and authoritative schedules

The principal lifecycle is `DRAFT → PROPOSAL_OPEN → PROPOSALS_PUBLISHED → REGISTRATION_OPEN → REGISTRATION_CLOSED → RESULTS_PUBLISHED → CLOSED`. `CANCELLED` has distinct cancellation/restoration paths; states are not freely interchangeable. Previews and business guards determine permitted transitions.

Round types are `MON_HOC`, `NCKH`, `TLCN` and `KLTN`. TLCN/KLTN have counter-review/report milestones; a defense date is a KLTN requirement. The UI reads capabilities to offer applicable controls; the API still rejects unsupported actions with stable conflicts. Counter-review waivers are not a default shipped feature: every current undergraduate round type disables them; the operator override is an intentional exception.

Server/database time authorizes write windows. A cancelled round restored to registration-open must still satisfy its stored schedule; changing status does not implicitly move deadlines. Closing registration, publishing, cancelling, restoring and deleting have different consequences. Deletion has narrow guards to preserve dependent history.

**Owners:** [RoundStatus](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/model/RoundStatus.java), [RoundType](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/model/RoundType.java), [lifecycle](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisRoundLifecycleService.java), [capabilities](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisGovernanceCapabilities.java), [restore races](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisRoundRestoreRacePostgresIT.java).

## T21. Topics, supervisors and faculty/department scope

Topics move through proposal/review/publication, have an owning department and explicit supervisor assignments. Supervisors require a valid active account/profile; a directory entry alone does not establish eligibility for assignment.

Administrators govern institution-wide; faculty/department heads act within current academic scope. Scope follows a lecturer profile bound to the account and the object's faculty/department relationships. A faculty head who is also a lecturer may hold legitimate cross-faculty supervisor/council/reviewer assignments, but that assignment does not make every governance permission global.

A withdrawn topic is hidden from the student catalog when the student has no relationship to it. Historical group members still need appropriate access; hiding a catalog entry must not erase all historical read authority. Reassigning a group checks both source-group and destination-topic boundaries to prevent crossing faculties by changing a link.

**Owners:** [topic mutations](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisTopicMutationService.java), [topic reads](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisTopicService.java), [supervisors](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisSupervisorService.java), [dean scope](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/scope/ThesisDeanScope.java), [visibility tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisTopicVisibilityTest.java).

## T22. Groups, academic eligibility and audited exceptions

Draft groups can be staffed incrementally; approval enforces size. The round read model supplies `minGroupSize`/`maxGroupSize`, typically 3–5 members under current rules, with exactly one leader. Locks/database constraints supplement service checks against concurrent leadership or active-membership violations.

Graduation-thesis eligibility reuses the canonical transcript: defaults are at least 110 earned credits and CPA 2.0/4.0. Applicable round types and `ENFORCE`/`ADVISORY` are configurable. ENFORCE blocks failing mutations; ADVISORY still evaluates and warns. Missing published academic data means insufficient evidence, not eligibility inferred from samples. The schema does not model every academic-warning/disciplinary regulation, so this is not complete automatic regulatory compliance.

Governance approval replacing the supervisor requires a reason. Size/roster exceptions have narrow authority and audit. Approval withdrawal moves APPROVED back to PENDING within its permitted window, retaining roster/topic and clearing approval markers. Reports, scores, reviewer/council assignments or results trigger `GROUP_WITHDRAWAL_BLOCKED` with counts explaining downstream dependencies. Cancelled groups retain history rather than becoming freely reopenable active groups.

**Owners:** [membership](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisGroupMembershipService.java), [governance](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisGroupGovernanceService.java), [eligibility](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisEligibilityService.java), [governance contract](../integrations/thesis-group-governance.md), [cancellation history](../integrations/thesis-cancellation-history.md).

## T23. Reports as artifacts: read versus edit, metadata versus bytes

Group leaders submit Word/PDF documents or links through the API. File policy checks type/content/size, currently capped at 20 MiB. Submission authority differs from read authority: members, supervisors, graders and scoped staff read according to responsibility. Metadata, round repositories and download aliases must share the boundary; alternate endpoints must not bypass it.

**Upload flow.** Check authority/window → validate file → store object/bytes through the provider → attach metadata transactionally → commit → remove the old object. External storage cannot participate in SQL rollback. Delete the old object after commit; clean new objects only after confirmed rollback; retain them for reconciliation when transaction outcome is unknown. A surplus object is recoverable, while a surviving row pointing at deleted bytes is data loss.

Metadata edits must preserve omitted attachments/links. Switching providers affects new uploads; it does not migrate old objects. Filename, submission timestamp and storage key do not prove downloadable bytes exist. Recovery requires matching original bytes/metadata/checksum and an authorized download check, not deletion of the row to hide a UI error.

**Owners:** [ThesisReportService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisReportService.java), [file policy](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/storage/ReportFilePolicy.java), [storage/recovery contract](../integrations/thesis-report-storage.md), [rollback/concurrency tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisReportConcurrencyTest.java).

## T24. Counter-review and deadline recovery without reopening uploads

Counter-review (GVPB) is an explicit assignment. Only its authorized reviewer scores within the applicable phase/window. `ThesisReviewOutcome` owns the 5.0 threshold and `REVIEW_FAILED`, `WITHDRAWN`, `GRADED` meanings so finalization, previews, publication and student results cannot derive inconsistent outcomes.

A low review score remains provisional while the window is open. The round must not publish as though that outcome were stable. MON_HOC/NCKH have no counter-review phase; corresponding unsupported operations return `ROUND_HAS_NO_COUNTER_REVIEW`.

**Recent recovery path.** Within the permitted state, extending GVPB requires a future deadline and a reason. If it would pass report/defense dates, the service moves dependent dates forward to retain ordering. If the report-submission deadline previously inherited GVPB, it first pins `report_deadline` to the old deadline. This reopens counter-review scoring without silently reopening report editing. The cascade and before/after audit share one transaction. Extending report submission is a separate action.

**Owners:** [lifecycle deadline operations](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisRoundLifecycleService.java), [review outcome](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisReviewOutcome.java), [reviewer/council gates](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisCouncilService.java), [schedule tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisRoundRegistrationScheduleTest.java).

## T25. Councils, conflicts of interest and finalization

Councils have 3–5 lecturers, exactly one chair and one secretary. Topic assignment, membership, reviewer assignment and scoring have separate guards; faculty-head authority does not substitute for a required grading assignment. Supervisors cannot grade their own topics (`SUPERVISOR_CANNOT_GRADE`).

Each eligible member submits their component score. Read models show submitted/missing scores and appropriately scoped breakdowns. The chair finalizes when requirements are complete. The current defense result is the equally weighted average of eligible graders, computed with decimal arithmetic and explicit rounding. Do not invent a supervisor/reviewer/council 20/30/50 formula when the source uses no such calculation.

Finalization uses locks and guarded state changes, not merely a disabled browser button. Reopen/reassignment/withdrawal operations have their own reason and state guards to avoid orphaned scores or changing a frozen roster. Internal finalization is not student publication; the round's publication gate still applies.

**Owners:** [ThesisCouncilService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisCouncilService.java), [score breakdown tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisScoreBreakdownTest.java), [PostgreSQL score checks](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisScoreBreakdownPostgresIT.java).

## T26. Consequence previews, publication and controlled recovery

Publication preview reports approved groups, included/excluded topics, missing scores and open review windows. The write path rechecks after locking the round: a preview neither reserves state nor guarantees no intervening change.

No usable groups/results yields `RESULTS_NOT_READY`; incomplete scores yield `SCORES_INCOMPLETE`; open counter-review yields `REVIEW_WINDOW_OPEN`. If every group is excluded, publication requires explicit `confirmAllExcluded` and audit. Withdrawn/review-failed topics use the shared outcome definition instead of becoming unexplained empty result rows.

The transaction moves the round to RESULTS_PUBLISHED, with notification fan-out after commit. Retraction, close/reopen and cancellation/restoration have conditions, grace periods and audit; directly editing database status bypasses invariants. Faculty-head dashboards aggregate faculty, department, supervisor workload and progress data; overview totals do not grant additional write authority.

**Owners:** [publication service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisPublicationService.java), [repository](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/repository/ThesisPublicationRepository.java), [publication races](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisPublicationRacePostgresIT.java), [dean overview](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisDeanOverviewService.java).
