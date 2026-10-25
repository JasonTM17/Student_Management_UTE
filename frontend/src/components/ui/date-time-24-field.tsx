'use client';

import * as React from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import { editDateTime24, splitDateTime24 } from '@/lib/date-time-24';

export interface DateTime24FieldProps {
  /** Lands on the date control so tests and htmlFor labels stay stable. */
  id?: string;
  /** Visible field label; also names the wrapping group for screen readers. */
  label?: React.ReactNode;
  /** Local wall-clock 'YYYY-MM-DDTHH:mm' (seconds tolerated); '' when unset. */
  value: string;
  /** Emits canonical 'YYYY-MM-DDTHH:mm'; '' when the date is cleared. */
  onChange: (value: string) => void;
  min?: string;
  max?: string;
  required?: boolean;
  disabled?: boolean;
  error?: string;
  hint?: string;
  className?: string;
  /** Accessible name for the group when there is no visible `label`. */
  'aria-label'?: string;
  'aria-describedby'?: string;
}

const HOURS: readonly string[] = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'));
const MINUTES: readonly string[] = Array.from({ length: 60 }, (_, i) => String(i).padStart(2, '0'));

const controlClass = (error: boolean, extra?: string) =>
  cn(
    'h-11 rounded-lg border border-input bg-background text-base text-foreground ring-offset-background transition-[border-color,box-shadow,background-color] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 sm:text-sm',
    error && 'border-destructive focus-visible:ring-destructive',
    extra,
  );

export const DateTime24Field = React.forwardRef<HTMLDivElement, DateTime24FieldProps>(
  function DateTime24Field(
    {
      id,
      label,
      value,
      onChange,
      min,
      max,
      required,
      disabled,
      error,
      hint,
      className,
      'aria-label': groupAriaLabel,
      'aria-describedby': externalDescribedBy,
    },
    ref,
  ) {
    const { messages } = useI18n();
    const copy = messages.common.dateTime24;
    const uid = React.useId().replace(/:/g, '');
    const dateId = id ?? `dt24-${uid}`;
    const labelId = `dt24-${uid}-label`;
    const errorId = `dt24-${uid}-error`;
    const hintId = `dt24-${uid}-hint`;
    const describedBy = [
      externalDescribedBy,
      error ? errorId : hint ? hintId : null,
    ]
      .filter(Boolean)
      .join(' ')
      .trim() || undefined;

    const parsed = splitDateTime24(value);
    const hasDate = parsed.date !== '';

    // A time picked before the date is kept pending and consumed by the next
    // date pick; any externally driven value change resets it.
    const [pending, setPending] = React.useState({ hour: '', minute: '' });
    const [previousValue, setPreviousValue] = React.useState(value);
    if (previousValue !== value) {
      setPreviousValue(value);
      setPending({ hour: '', minute: '' });
    }

    const apply = (part: 'date' | 'hour' | 'minute', next: string) => {
      const result = editDateTime24(value, part, next, pending);
      setPending({ hour: result.pendingHour, minute: result.pendingMinute });
      if (result.emit !== null) onChange(result.emit);
    };

    const hourShown = hasDate ? parsed.hour || '00' : pending.hour;
    const minuteShown = hasDate ? parsed.minute || '00' : pending.minute;
    const invalid = Boolean(error);

    const timeSelect = (
      ariaLabel: string,
      shown: string,
      options: readonly string[],
      part: 'hour' | 'minute',
    ) => (
      <div className="relative w-16 shrink-0">
        <select
          value={shown}
          onChange={(event) => apply(part, event.target.value)}
          disabled={disabled}
          aria-label={ariaLabel}
          aria-invalid={invalid || undefined}
          className={controlClass(invalid, 'w-full appearance-none pl-2 pr-6 text-sm')}
        >
          {!hasDate ? <option value="">--</option> : null}
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
        <ChevronDown
          aria-hidden="true"
          className="pointer-events-none absolute right-1.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground"
        />
      </div>
    );

    return (
      <div ref={ref} className={cn('w-full', className)}>
        {label ? (
          <label id={labelId} htmlFor={dateId} className="mb-2 block text-sm font-medium text-foreground">
            {label}
          </label>
        ) : null}
        <div
          role="group"
          aria-labelledby={label ? labelId : undefined}
          aria-label={label ? undefined : groupAriaLabel}
          aria-describedby={describedBy}
          className="flex flex-wrap items-center gap-2"
        >
          <input
            id={dateId}
            type="date"
            value={parsed.date}
            onChange={(event) => apply('date', event.target.value)}
            min={min}
            max={max}
            required={required}
            disabled={disabled}
            aria-label={copy.date}
            aria-invalid={invalid || undefined}
            className={controlClass(invalid, 'min-w-36 flex-1 px-3 py-0')}
          />
          <div className="flex shrink-0 items-center gap-1">
            {timeSelect(copy.hour, hourShown, HOURS, 'hour')}
            <span aria-hidden="true" className="text-muted-foreground">:</span>
            {timeSelect(copy.minute, minuteShown, MINUTES, 'minute')}
          </div>
        </div>
        {error ? (
          <p id={errorId} className="mt-1 text-sm text-destructive">{error}</p>
        ) : hint ? (
          <p id={hintId} className="mt-1 text-sm text-muted-foreground">{hint}</p>
        ) : null}
      </div>
    );
  },
);
DateTime24Field.displayName = 'DateTime24Field';

export default DateTime24Field;
