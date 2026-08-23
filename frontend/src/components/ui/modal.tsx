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

  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
        return;
      }

      if (e.key !== 'Tab' || !dialogRef.current) return;

      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>(
          'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      );

      if (focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
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

    const previousBodyOverflow = document.body.style.overflow;
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
      document.body.style.overflow = previousBodyOverflow;
      previousFocusRef.current?.focus();
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div
        className="absolute inset-0 bg-black/50 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />
      <div className="relative z-50 flex min-h-full items-start justify-center px-4 py-4 sm:py-8">
        <div
          ref={dialogRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby={title ? titleId : undefined}
          aria-label={title ? undefined : messages.common.states.dialog}
          tabIndex={-1}
          className={cn(
            'relative flex max-h-[calc(100vh-2rem)] w-full max-w-lg flex-col rounded-lg border border-border/80 bg-card shadow-2xl sm:max-h-[calc(100vh-4rem)]',
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
                className="rounded-lg p-2 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
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
}: ConfirmModalProps) {
  const { messages } = useI18n();

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title}>
      <div className="space-y-4">
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
