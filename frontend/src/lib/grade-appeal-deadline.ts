export function shouldShowAppealDeadline(
  appealAllowed: boolean,
  appealDeadline: string | null | undefined,
): appealDeadline is string {
  return appealAllowed && Boolean(appealDeadline);
}
