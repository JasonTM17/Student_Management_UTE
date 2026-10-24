'use client';

import * as React from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { filterSearchOptions } from '@/lib/search-text';
import { useI18n } from '@/i18n';

export interface ComboboxOption {
  value: string;
  label: string;
  /** Muted secondary line (department, workload hint) under the label. */
  description?: string;
  /** Extra search haystack — employee id, email — never rendered. */
  keywords?: string;
  disabled?: boolean;
  /** Why the backend would refuse this option; shown muted under the label. */
  disabledReason?: string;
}

export interface ComboboxProps {
  id?: string;
  label?: string;
  error?: string;
  options: ComboboxOption[];
  /** Controlled selected value; '' means no selection. */
  value: string;
  /** Called with the chosen option's value — '' when the user clears. */
  onChange: (value: string) => void;
  /** Text shown in the empty input, mirroring the old '-- pick one --' row. */
  placeholder?: string;
  /** Renders the clear affordances (button + in-list clear row). */
  allowClear?: boolean;
  /** Label for the in-list clear row; defaults to the placeholder. */
  emptyLabel?: string;
  disabled?: boolean;
  /** Shows the loading row inside the open list instead of the options. */
  loading?: boolean;
  required?: boolean;
  className?: string;
  /** Layout classes for the outer wrapper (e.g. flex-1 inside a row). */
  wrapperClassName?: string;
  'aria-label'?: string;
  'aria-describedby'?: string;
  /** Open the list on the very first render — for tests and previews. */
  defaultOpen?: boolean;
}

interface NavigableOption {
  disabled?: boolean;
  /** Marks the synthetic clear row: keyboard-reachable but never auto-activated. */
  isEmpty?: boolean;
}

function firstEnabled(options: readonly NavigableOption[], includeEmpty = false): number {
  return options.findIndex((option) => !option.disabled && (includeEmpty || !option.isEmpty));
}

function nextEnabled(
  options: readonly NavigableOption[],
  from: number,
  direction: 1 | -1,
): number {
  if (options.length === 0) return -1;
  for (let step = 1; step <= options.length; step += 1) {
    const index = (from + direction * step + options.length * step) % options.length;
    if (!options[index]?.disabled) return index;
  }
  return -1;
}

export interface ComboboxNavigationResult {
  preventDefault?: boolean;
  open?: boolean;
  close?: boolean;
  activeIndex?: number;
  commitIndex?: number;
}

/**
 * Pure keyboard state machine behind the combobox, exported so `node --test`
 * can drive it without a DOM. WAI-ARIA editable-combobox semantics:
 * ArrowUp/Down/Home/End move the active option (wrapping, skipping disabled),
 * Enter commits the active or first enabled match and opens a closed popup
 * without submitting the surrounding form, Escape closes only the popup,
 * Tab leaves and closes.
 */
export function resolveComboboxNavigation(
  key: string,
  state: {
    open: boolean;
    activeIndex: number;
    /** Index of the currently selected option, or -1. */
    selectedIndex?: number;
    options: readonly NavigableOption[];
  },
): ComboboxNavigationResult {
  const { open, activeIndex, options } = state;
  const selectedIndex = state.selectedIndex ?? -1;
  switch (key) {
    case 'ArrowDown':
      return open
        ? { preventDefault: true, activeIndex: nextEnabled(options, activeIndex, 1) }
        : {
            preventDefault: true,
            open: true,
            activeIndex: selectedIndex >= 0 ? selectedIndex : firstEnabled(options),
          };
    case 'ArrowUp':
      return open
        ? { preventDefault: true, activeIndex: nextEnabled(options, activeIndex, -1) }
        : {
            preventDefault: true,
            open: true,
            activeIndex: selectedIndex >= 0 ? selectedIndex : lastEnabled(options),
          };
    case 'Home':
      return open
        ? { preventDefault: true, activeIndex: firstEnabled(options, true) }
        : { preventDefault: true, open: true, activeIndex: firstEnabled(options, true) };
    case 'End':
      return open
        ? { preventDefault: true, activeIndex: lastEnabled(options) }
        : { preventDefault: true, open: true, activeIndex: lastEnabled(options) };
    case 'Enter':
      if (!open) {
        return {
          preventDefault: true,
          open: true,
          activeIndex: selectedIndex >= 0 ? selectedIndex : firstEnabled(options),
        };
      }
      if (activeIndex >= 0) {
        return options[activeIndex]?.disabled
          ? { preventDefault: true }
          : { preventDefault: true, commitIndex: activeIndex };
      }
      {
        const first = firstEnabled(options);
        return first >= 0 ? { preventDefault: true, commitIndex: first } : { preventDefault: true };
      }
    case 'Escape':
      return open ? { preventDefault: true, close: true } : {};
    case 'Tab':
      return open ? { close: true } : {};
    default:
      return {};
  }
}

function lastEnabled(options: readonly NavigableOption[]): number {
  for (let index = options.length - 1; index >= 0; index -= 1) {
    if (!options[index]?.disabled) return index;
  }
  return -1;
}

const EMPTY_OPTION: NavigableOption & ComboboxOption = {
  value: '',
  label: '',
  isEmpty: true,
};

const Combobox = React.forwardRef<HTMLInputElement, ComboboxProps>(
  (
    {
      id,
      label,
      error,
      options,
      value,
      onChange,
      placeholder,
      allowClear = false,
      emptyLabel,
      disabled = false,
      loading = false,
      required = false,
      className,
      wrapperClassName,
      'aria-label': ariaLabel,
      'aria-describedby': externalDescribedBy,
      defaultOpen = false,
    },
    forwardedRef,
  ) => {
    const { messages } = useI18n();
    const generatedId = React.useId().replace(/:/g, '');
    const inputId = id ?? `combobox-${generatedId}`;
    const listboxId = `combobox-list-${generatedId}`;
    const errorId = `combobox-error-${generatedId}`;
    const describedBy = [externalDescribedBy, error ? errorId : null]
      .filter(Boolean)
      .join(' ')
      .trim();

    const [open, setOpen] = React.useState(defaultOpen);
    const [query, setQuery] = React.useState('');
    const [activeIndex, setActiveIndex] = React.useState(-1);
    const [mounted, setMounted] = React.useState(false);
    const [popupStyle, setPopupStyle] = React.useState<React.CSSProperties>({});
    const rootRef = React.useRef<HTMLDivElement>(null);
    const inputRef = React.useRef<HTMLInputElement | null>(null);
    const listRef = React.useRef<HTMLUListElement>(null);

    const selected = options.find((option) => option.value === value && option.value !== '');
    const filtered = React.useMemo(() => filterSearchOptions(options, query), [options, query]);
    // The clear row pins to the top whenever a selection exists; isEmpty keeps
    // it keyboard-reachable but out of Enter/arrow auto-activation.
    const renderedOptions = React.useMemo<(ComboboxOption & NavigableOption)[]>(
      () =>
        allowClear && value !== ''
          ? [{ ...EMPTY_OPTION, label: emptyLabel ?? placeholder ?? '—' }, ...filtered]
          : filtered,
      [allowClear, value, filtered, emptyLabel, placeholder],
    );
    const selectedIndex = renderedOptions.findIndex((option) => option.value === value);
    const inPortal = mounted && typeof document !== 'undefined' && Boolean(document.body);

    React.useEffect(() => {
      setMounted(true);
    }, []);

    const closeList = React.useCallback(() => {
      setOpen(false);
      setQuery('');
      setActiveIndex(-1);
    }, []);

    const openList = React.useCallback(() => {
      setOpen(true);
      setQuery('');
    }, []);

    // When the popup opens (or the option set changes under it), put the active
    // descendant on the selected option, else the first enabled match.
    React.useEffect(() => {
      if (!open) return;
      setActiveIndex((current) => {
        if (current >= 0 && current < renderedOptions.length && !renderedOptions[current]?.disabled) {
          return current;
        }
        if (selectedIndex >= 0 && !renderedOptions[selectedIndex]?.disabled) return selectedIndex;
        return firstEnabled(renderedOptions);
      });
    }, [open, renderedOptions, selectedIndex]);

    const commit = React.useCallback(
      (option: ComboboxOption & NavigableOption) => {
        if (option.disabled) return;
        onChange(option.value);
        closeList();
      },
      [onChange, closeList],
    );

    const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
      const result = resolveComboboxNavigation(event.key, {
        open,
        activeIndex,
        selectedIndex,
        options: renderedOptions,
      });
      if (result.preventDefault) {
        event.preventDefault();
        // The page-level modal listens on document for Escape; consuming the
        // keypress here must not bubble into a dialog close.
        if (event.key === 'Escape') event.stopPropagation();
      }
      if (result.open) setOpen(true);
      if (result.close) closeList();
      if (result.activeIndex !== undefined && result.activeIndex >= 0) setActiveIndex(result.activeIndex);
      if (result.commitIndex !== undefined) commit(renderedOptions[result.commitIndex]);
    };

    const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
      const nextQuery = event.target.value;
      setQuery(nextQuery);
      if (!open) setOpen(true);
      const nextFiltered = filterSearchOptions(options, nextQuery);
      const nextRendered =
        allowClear && value !== ''
          ? [{ ...EMPTY_OPTION, label: emptyLabel ?? placeholder ?? '—' }, ...nextFiltered]
          : nextFiltered;
      setActiveIndex(firstEnabled(nextRendered));
    };

    // Keep the popup pinned to the input across modal scroll and resize.
    React.useEffect(() => {
      if (!open || !inPortal) return undefined;
      const update = () => {
        const input = inputRef.current;
        if (!input) return;
        const rect = input.getBoundingClientRect();
        const below = window.innerHeight - rect.bottom;
        const above = rect.top;
        const flip = below < 240 && above > below;
        setPopupStyle({
          position: 'fixed',
          left: rect.left,
          width: rect.width,
          maxHeight: Math.max(120, Math.min(288, (flip ? above : below) - 8)),
          ...(flip
            ? { bottom: window.innerHeight - rect.top + 4 }
            : { top: rect.bottom + 4 }),
        });
      };
      update();
      window.addEventListener('resize', update);
      document.addEventListener('scroll', update, true);
      return () => {
        window.removeEventListener('resize', update);
        document.removeEventListener('scroll', update, true);
      };
    }, [open, inPortal]);

    // Scroll the active option into view while navigating.
    React.useEffect(() => {
      if (!open || activeIndex < 0 || !listRef.current) return;
      const active = listRef.current.querySelector<HTMLElement>(
        `#${listboxId}-opt-${activeIndex}`,
      );
      active?.scrollIntoView?.({ block: 'nearest' });
    }, [activeIndex, open, listboxId]);

    const list = open ? (
      <ul
        id={listboxId}
        ref={listRef}
        role="listbox"
        aria-label={ariaLabel ?? label}
        aria-busy={loading || undefined}
        onMouseDown={(event) => event.preventDefault()}
        className={cn(
          'z-[70] overflow-auto rounded-lg border border-border bg-popover text-popover-foreground shadow-lg',
          inPortal
            ? 'fixed'
            : 'absolute inset-x-0 top-full mt-1 max-h-60',
        )}
        style={inPortal ? popupStyle : undefined}
      >
        {loading ? (
          <li role="option" aria-selected="false" aria-disabled="true" className="px-3 py-2.5 text-sm text-muted-foreground">
            {messages.common.states.loading}
          </li>
        ) : renderedOptions.length === 0 ? (
          <li role="option" aria-selected="false" aria-disabled="true" className="px-3 py-2.5 text-sm text-muted-foreground">
            {messages.common.combobox.noResults}
          </li>
        ) : (
          renderedOptions.map((option, index) => (
            <li
              key={option.isEmpty ? '__empty__' : option.value}
              id={`${listboxId}-opt-${index}`}
              role="option"
              aria-selected={option.value === value}
              aria-disabled={option.disabled || undefined}
              onMouseEnter={() => {
                if (!option.disabled) setActiveIndex(index);
              }}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => commit(option)}
              className={cn(
                'flex min-h-[2.75rem] cursor-pointer items-center gap-2 px-3 py-1.5 text-sm',
                index === activeIndex && 'bg-accent text-accent-foreground',
                option.disabled && 'cursor-not-allowed opacity-60',
                option.isEmpty && 'text-muted-foreground',
              )}
            >
              <Check
                className={cn(
                  'h-4 w-4 shrink-0',
                  option.value === value && !option.isEmpty ? 'opacity-100' : 'opacity-0',
                )}
                aria-hidden="true"
              />
              <span className="min-w-0 flex-1">
                <span className="block truncate">{option.label}</span>
                {option.description ? (
                  <span
                    className={cn(
                      'block truncate text-xs',
                      index === activeIndex ? 'opacity-80' : 'text-muted-foreground',
                    )}
                  >
                    {option.description}
                  </span>
                ) : null}
                {option.disabled && option.disabledReason ? (
                  <span
                    className={cn(
                      'block truncate text-xs',
                      index === activeIndex ? 'opacity-80' : 'text-muted-foreground',
                    )}
                  >
                    {option.disabledReason}
                  </span>
                ) : null}
              </span>
            </li>
          ))
        )}
      </ul>
    ) : null;

    return (
      <div className={cn('w-full', wrapperClassName)} ref={rootRef}>
        {label && (
          <label htmlFor={inputId} className="mb-1 block text-sm font-medium text-foreground">
            {label}
          </label>
        )}
        <div className="relative">
          <input
            ref={(element) => {
              inputRef.current = element;
              if (typeof forwardedRef === 'function') forwardedRef(element);
              else if (forwardedRef) forwardedRef.current = element;
            }}
            id={inputId}
            type="text"
            role="combobox"
            aria-expanded={open}
            aria-controls={open ? listboxId : undefined}
            aria-autocomplete="list"
            aria-activedescendant={
              open && activeIndex >= 0 ? `${listboxId}-opt-${activeIndex}` : undefined
            }
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy || undefined}
            aria-label={ariaLabel}
            aria-required={required || undefined}
            autoComplete="off"
            disabled={disabled}
            required={required}
            placeholder={placeholder}
            value={open ? query : selected?.label ?? ''}
            onFocus={() => {
              if (!disabled && !open) openList();
            }}
            onClick={() => {
              if (!disabled && !open) openList();
            }}
            onBlur={closeList}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            className={cn(
              'flex h-11 w-full rounded-lg border border-input bg-background py-0 pl-3 text-sm text-foreground ring-offset-background transition-[border-color,box-shadow] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60',
              allowClear && value !== '' ? 'pr-16' : 'pr-10',
              error && 'border-destructive focus:ring-destructive',
              className,
            )}
          />
          {allowClear && value !== '' && !disabled ? (
            <button
              type="button"
              tabIndex={-1}
              aria-label={messages.common.combobox.clearSelection}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => onChange('')}
              className="absolute right-8 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          ) : null}
          <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          {!inPortal ? list : null}
        </div>
        {inPortal && list ? createPortal(list, document.body) : null}
        {error && (
          <p id={errorId} className="mt-1 text-sm text-destructive">
            {error}
          </p>
        )}
      </div>
    );
  },
);
Combobox.displayName = 'Combobox';

export { Combobox };
