'use client';

import * as React from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  className?: string;
  closeLabel?: string;
}

export function Modal({
  isOpen,
  onClose,
  title,
  children,
  className,
  closeLabel,
}: ModalProps) {
  const { messages } = useI18n();
  const titleId = React.useId();
  const dialogRef = React.useRef<HTMLDivElement>(null);
  const closeButtonRef = React.useRef<HTMLButtonElement>(null);
  const previousFocusRef = React.useRef<HTMLElement | null>(null);
  const onCloseRef = React.useRef(onClose);
  onCloseRef.current = onClose;

  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current();
        return;
      }

      if (event.key !== 'Tab' || !dialogRef.current) {
        return;
      }

      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>(
          'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      ).filter((element) => !element.hasAttribute('hidden'));

      if (focusable.length === 0) {
        event.preventDefault();
        dialogRef.current.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;

      if (event.shiftKey && (active === first || !dialogRef.current.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    };

    if (!isOpen) {
      return undefined;
    }

    previousFocusRef.current =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;

    const previousOverflow = document.body.style.overflow;
    document.addEventListener('keydown', handleKeyDown);
    document.body.style.overflow = 'hidden';

    requestAnimationFrame(() => {
      closeButtonRef.current?.focus();
      if (!closeButtonRef.current) {
        dialogRef.current?.focus();
      }
    });

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previousFocusRef.current?.focus();
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto overscroll-contain">
      <div
        className="absolute inset-0 bg-[var(--portal-scrim)]"
        onClick={onClose}
        aria-hidden="true"
      />
      <div className="relative z-50 flex min-h-full items-start justify-center px-4 py-4 sm:py-8">
        <div
          ref={dialogRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby={title ? titleId : undefined}
          tabIndex={-1}
          className={cn(
            'relative flex max-h-[calc(100vh-2rem)] w-full max-w-lg flex-col rounded-md border border-border/80 bg-card shadow-2xl sm:max-h-[calc(100vh-4rem)]',
            className,
          )}
        >
          {title && (
            <div className="flex shrink-0 items-center justify-between border-b border-border/70 px-5 py-4">
              <h3 id={titleId} className="text-lg font-semibold text-foreground">
                {title}
              </h3>
              <button
                ref={closeButtonRef}
                type="button"
                onClick={onClose}
                className="flex h-11 w-11 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                aria-label={closeLabel || messages.common.states.closeModal}
              >
                <X className="h-5 w-5" />
              </button>
            </div>
          )}
          <div className="min-h-0 overflow-y-auto p-5">{children}</div>
        </div>
      </div>
    </div>
  );
}

interface ConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'default' | 'destructive';
  isLoading?: boolean;
  error?: string;
}

export function ConfirmModal({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmText,
  cancelText,
  variant = 'default',
  isLoading = false,
  error,
}: ConfirmModalProps) {
  const { messages } = useI18n();

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title}>
      <div className="space-y-4">
        {error ? (
          <div role="alert" className="border border-destructive/35 bg-destructive/5 px-3 py-2 text-sm text-destructive">
            {error}
          </div>
        ) : null}
        <p className="text-sm leading-6 text-muted-foreground">{message}</p>
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            disabled={isLoading}
          >
            {cancelText || messages.common.actions.cancel}
          </Button>
          <Button
            type="button"
            variant={variant === 'destructive' ? 'destructive' : 'default'}
            onClick={onConfirm}
            disabled={isLoading}
          >
            {isLoading
              ? messages.common.states.loading
              : confirmText || messages.common.actions.confirm}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
