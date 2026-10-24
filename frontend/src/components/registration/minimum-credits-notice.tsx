type MinimumCreditsNoticeProps = {
  belowMinimum: boolean;
  minCredits: number;
  message: string;
};

export function MinimumCreditsNotice({ belowMinimum, minCredits, message }: MinimumCreditsNoticeProps) {
  if (!belowMinimum || minCredits <= 0) return null;

  return (
    <p
      className="rounded-lg border border-status-warning/30 bg-status-warning/10 px-3 py-2 text-xs leading-5 text-status-warning-foreground"
      role="status"
    >
      {message}
    </p>
  );
}
