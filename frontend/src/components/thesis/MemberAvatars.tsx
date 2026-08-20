import { cn } from '@/lib/utils';

interface MemberAvatarsProps {
  memberIds: string[];
  max?: number;
  size?: 'sm' | 'md';
  className?: string;
}

export function MemberAvatars({ memberIds, max = 3, size = 'md', className }: MemberAvatarsProps) {
  const sizeClass = size === 'sm' ? 'h-8 w-8 text-xs' : 'h-10 w-10 text-xs';
  const emptySizeClass = size === 'sm' ? 'h-8 w-8' : 'h-10 w-10';

  return (
    <div className={cn('flex gap-2', className)}>
      {memberIds.slice(0, max).map((memberId, index) => (
        <div
          key={memberId}
          className={cn(
            'flex items-center justify-center rounded-full border border-border bg-secondary font-semibold text-foreground',
            sizeClass,
          )}
          title={memberId}
        >
          {index + 1}
        </div>
      ))}
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
