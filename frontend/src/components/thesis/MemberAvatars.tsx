import { cn } from '@/lib/utils';
import type { ThesisGroupMember } from '@/lib/thesis-api';

interface MemberAvatarsProps {
  memberIds: string[];
  /** Roster rows with display names; falls back to the raw id when absent. */
  members?: ThesisGroupMember[];
  max?: number;
  size?: 'sm' | 'md';
  className?: string;
}

export function MemberAvatars({ memberIds, members, max = 3, size = 'md', className }: MemberAvatarsProps) {
  const sizeClass = size === 'sm' ? 'h-8 w-8 text-xs' : 'h-10 w-10 text-xs';
  const emptySizeClass = size === 'sm' ? 'h-8 w-8' : 'h-10 w-10';
  const byStudentId = new Map((members ?? []).map((member) => [member.studentId, member]));
  const visible = memberIds.slice(0, max);
  const hiddenCount = memberIds.length - visible.length;

  return (
    <div className={cn('flex gap-2', className)}>
      {visible.map((memberId) => {
        const member = byStudentId.get(memberId);
        const label = member?.displayName || member?.studentNumber || memberId;
        return (
          <div
            key={memberId}
            className={cn(
              'flex items-center justify-center rounded-full border border-border bg-secondary font-semibold text-foreground',
              sizeClass,
            )}
            title={label}
            aria-label={label}
          >
            {label.slice(0, 2).toUpperCase()}
          </div>
        );
      })}
      {hiddenCount > 0 ? (
        <div
          className={cn(
            'flex items-center justify-center rounded-full border border-border bg-secondary text-muted-foreground',
            sizeClass,
          )}
          title={`+${hiddenCount}`}
          aria-label={`+${hiddenCount}`}
        >
          +{hiddenCount}
        </div>
      ) : null}
      {Array.from({ length: Math.max(0, max - memberIds.length) }).map((_, index) => (
        <div
          key={`empty-${index}`}
          className={cn(
            'flex items-center justify-center rounded-full border border-dashed border-border text-xs text-muted-foreground',
            emptySizeClass,
          )}
        >
          +
        </div>
      ))}
    </div>
  );
}
