type MinimumCreditsNoticeProps = {
  belowMinimum: boolean;
  message: string;
};

export function MinimumCreditsNotice({ belowMinimum, message }: MinimumCreditsNoticeProps) {
  if (!belowMinimum) return null;

  return (
    <p
      className="rounded-lg border border-status-warning/30 bg-status-warning/10 px-3 py-2 text-xs leading-5 text-status-warning-foreground"
      role="status"
    >
      {message}
    </p>
  );
}
