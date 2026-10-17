type SemesterLike = {
  id: string;
  status: string;
};

const preferredSemesterStatuses = [
  'IN_PROGRESS',
  'ADD_DROP_OPEN',
  'REGISTRATION_OPEN',
  'ACTIVE',
  'OPEN',
] as const;

export function pickPreferredSemesterId<T extends SemesterLike>(
  semesters: T[],
): string {
  for (const status of preferredSemesterStatuses) {
    const semester = semesters.find((candidate) => candidate.status === status);
    if (semester) {
      return semester.id;
    }
  }

  return semesters[0]?.id ?? '';
}
