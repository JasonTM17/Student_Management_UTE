'use client';

import { useEffect, useRef, type RefObject } from 'react';

type DialogFocusTrapOptions<T extends HTMLElement> = {
  open: boolean;
  onClose: () => void;
  initialFocusRef?: RefObject<HTMLElement | null>;
  restoreFocusRef?: RefObject<HTMLElement | null>;
  lockScroll?: boolean;
};

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Shared keyboard/focus behavior for custom dialogs and side drawers.
 * Nested dialogs (for example a confirmation modal inside the assistant)
 * own their own focus loop and are ignored by the parent listener.
 */
export function useDialogFocusTrap<T extends HTMLElement>({
  open,
  onClose,
  initialFocusRef,
  restoreFocusRef,
  lockScroll = true,
}: DialogFocusTrapOptions<T>) {
  const dialogRef = useRef<T>(null);
  const onCloseRef = useRef(onClose);
  const initialFocusRefValue = initialFocusRef;
  const restoreFocusRefValue = restoreFocusRef;

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return undefined;

    const dialog = dialogRef.current;
    if (!dialog) return undefined;

    const previousFocus =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    const restoreFocus = restoreFocusRefValue?.current ?? previousFocus;
    const previousOverflow = document.body.style.overflow;

    const focusableElements = () =>
      Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
        (element) => !element.hasAttribute('hidden') && !element.getAttribute('aria-hidden'),
      );

    const handleKeyDown = (event: KeyboardEvent) => {
      // A nested role=dialog (Modal/ConfirmModal) owns the interaction while
      // it is open; the parent must not steal its Tab cycle or Escape key.
      const activeDialog = document.activeElement?.closest('[role="dialog"]');
      if (activeDialog && activeDialog !== dialog) return;

      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab') return;

      const focusable = focusableElements();
      if (focusable.length === 0) {
        event.preventDefault();
        dialog.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || !dialog.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (active === last || !dialog.contains(active))) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    if (lockScroll) document.body.style.overflow = 'hidden';

    const frame = window.requestAnimationFrame(() => {
      const preferred = initialFocusRefValue?.current;
      if (preferred && !preferred.hasAttribute('disabled')) {
        preferred.focus();
      } else {
        (focusableElements()[0] ?? dialog).focus();
      }
    });

    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener('keydown', handleKeyDown);
      if (lockScroll) document.body.style.overflow = previousOverflow;
      if (restoreFocus && document.contains(restoreFocus)) restoreFocus.focus({ preventScroll: true });
    };
  }, [open, lockScroll, initialFocusRefValue, restoreFocusRefValue]);

  return dialogRef;
}
