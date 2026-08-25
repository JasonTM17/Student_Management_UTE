import * as React from 'react';
import { cn } from '@/lib/utils';

export interface InputProps
  extends React.InputHTMLAttributes<HTMLInputElement> {
  icon?: React.ReactNode
  error?: string
  hint?: string
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, icon, error, hint, 'aria-describedby': ariaDescribedBy, ...props }, ref) => {
    const messageId = React.useId();
    const message = error || hint;
    const describedBy = [ariaDescribedBy, message ? messageId : undefined]
      .filter(Boolean)
      .join(' ') || undefined;

    return (
      <div className="w-full">
        <div className="relative">
          {icon && (
            <div className="absolute inset-y-0 left-0 flex items-center pl-3 text-muted-foreground pointer-events-none">
              {icon}
            </div>
          )}
          <input
            {...props}
            type={type}
            aria-describedby={describedBy}
            aria-invalid={error ? true : props['aria-invalid']}
            className={cn(
              'flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-base text-foreground ring-offset-background transition-[border-color,box-shadow,background-color] placeholder:text-muted-foreground/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 sm:text-sm',
              icon && 'pl-10',
              error && 'border-destructive focus-visible:ring-destructive',
              className
            )}
            ref={ref}
          />
        </div>
        {error && (
          <p id={messageId} className="mt-1 text-sm text-destructive">{error}</p>
        )}
        {!error && hint ? (
          <p id={messageId} className="mt-1 text-sm text-muted-foreground">{hint}</p>
        ) : null}
      </div>
    )
  }
)
Input.displayName = 'Input';

export { Input };
