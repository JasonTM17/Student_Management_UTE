/**
 * Disabled-with-reason rules for the thesis people pickers, mirroring
 * ThesisCouncilService on the backend (which stays authoritative):
 *
 * - assignReviewer: a topic supervisor cannot be its counter-reviewer
 *   (SUPERVISOR_CANNOT_REVIEW) and the reviewer cannot sit on the defense
 *   council grading that topic (REVIEWER_MUST_BE_INDEPENDENT).
 * - addMember / replaceMember: the reviewer of an assigned topic and any
 *   supervisor of an assigned topic are refused on every seat
 *   (REVIEWER_MUST_BE_INDEPENDENT / SUPERVISOR_CANNOT_CHAIR_COUNCIL).
 * - assignTopic: a supervisor cannot hold the chair or secretary seat for the
 *   topic, at least three non-supervisor graders must remain
 *   (COUNCIL_SIZE_INVALID), and the topic's counter-reviewer cannot sit on
 *   the council in any seat (REVIEWER_MUST_BE_INDEPENDENT).
 *
 * Storage caveat the queries themselves work around: supervisor and member
 * rows may key the lecturer by either Lecturer.id or the legacy User.id, so
 * every comparison resolves both forms through the lecturer directory.
 */

export interface LecturerIdentity {
  id: string;
  userId?: string | null;
}

export interface TopicReviewerRef {
  id: string;
  gvpbLecturerId?: string | null;
}

export type PickerConflictKey =
  | 'conflictSupervisorOfTopic'
  | 'conflictCouncilMemberOfTopic'
  | 'conflictReviewsAssignedTopic'
  | 'conflictSupervisesAssignedTopic'
  | 'conflictSupervisorOfficer'
  | 'conflictReviewerOnCouncil'
  | 'conflictTooFewGraders';

/**
 * Every id form a raw stored id list can mean: each value itself, plus the
 * Lecturer.id/User.id pair of any profile it resolves to. Lets a caller ask
 * "is this stored id one of these people" without caring which column the
 * row was keyed by.
 */
export function identityKeySet(
  lecturers: readonly LecturerIdentity[],
  rawIds: readonly string[],
): Set<string> {
  const keys = new Set<string>();
  for (const raw of rawIds) {
    keys.add(raw);
    const profile = lecturers.find((l) => l.id === raw || l.userId === raw);
    if (profile) {
      keys.add(profile.id);
      if (profile.userId) keys.add(profile.userId);
    }
  }
  return keys;
}

/** Whether a lecturer option matches any raw stored id, in either key form. */
export function lecturerMatches(
  lecturer: LecturerIdentity,
  rawIds: readonly string[],
): boolean {
  return (
    rawIds.includes(lecturer.id) ||
    (lecturer.userId != null && rawIds.includes(lecturer.userId))
  );
}

/**
 * GVPB counter-reviewer options for one topic: a supervisor of the topic or
 * a member of the council grading it is refused (SUPERVISOR_CANNOT_REVIEW /
 * REVIEWER_MUST_BE_INDEPENDENT).
 */
export function reviewerConflict(args: {
  lecturer: LecturerIdentity;
  supervisorIds: readonly string[];
  councilMemberIds: readonly string[];
}): PickerConflictKey | null {
  if (lecturerMatches(args.lecturer, args.supervisorIds)) {
    return 'conflictSupervisorOfTopic';
  }
  if (lecturerMatches(args.lecturer, args.councilMemberIds)) {
    return 'conflictCouncilMemberOfTopic';
  }
  return null;
}

/**
 * Council member options (add or replace): the reviewer of, or a supervisor
 * of, a topic already assigned to the council is refused on any seat
 * (REVIEWER_MUST_BE_INDEPENDENT / SUPERVISOR_CANNOT_CHAIR_COUNCIL).
 */
export function councilMemberConflict(args: {
  lecturer: LecturerIdentity;
  assignedTopics: readonly TopicReviewerRef[];
  topicSupervisors: Record<string, readonly string[]>;
}): PickerConflictKey | null {
  const { lecturer, assignedTopics, topicSupervisors } = args;
  if (
    assignedTopics.some(
      (topic) =>
        topic.gvpbLecturerId != null &&
        (topic.gvpbLecturerId === lecturer.id ||
          topic.gvpbLecturerId === lecturer.userId),
    )
  ) {
    return 'conflictReviewsAssignedTopic';
  }
  if (
    assignedTopics.some((topic) =>
      lecturerMatches(lecturer, topicSupervisors[topic.id] ?? []),
    )
  ) {
    return 'conflictSupervisesAssignedTopic';
  }
  return null;
}

/**
 * Topic options inside the assign-to-council modal: refuse the topic when a
 * supervisor holds a privileged seat (SUPERVISOR_CANNOT_CHAIR_COUNCIL), when
 * the topic's counter-reviewer sits on the council
 * (REVIEWER_MUST_BE_INDEPENDENT), or when removing supervisors would leave
 * under three eligible graders (COUNCIL_SIZE_INVALID).
 */
export function assignTopicConflict(args: {
  topicSupervisorIds: readonly string[];
  reviewerId?: string | null;
  /** Raw lecturer_id values of every council member. */
  memberIds: readonly string[];
  /** Raw lecturer_id values of the chair and secretary members. */
  officerIds: readonly string[];
  /** Directory used to resolve either stored key form. */
  lecturers: readonly LecturerIdentity[];
  /** Minimum non-supervisor graders the backend requires (default 3). */
  minGraders?: number;
}): PickerConflictKey | null {
  const officerKeys = identityKeySet(args.lecturers, args.officerIds);
  if (args.topicSupervisorIds.some((id) => officerKeys.has(id))) {
    return 'conflictSupervisorOfficer';
  }
  const memberKeys = identityKeySet(args.lecturers, args.memberIds);
  if (args.reviewerId && memberKeys.has(args.reviewerId)) {
    return 'conflictReviewerOnCouncil';
  }
  const supervisorKeys = identityKeySet(args.lecturers, args.topicSupervisorIds);
  const eligible = args.memberIds.filter((id) => !supervisorKeys.has(id)).length;
  if (eligible < (args.minGraders ?? 3)) {
    return 'conflictTooFewGraders';
  }
  return null;
}
