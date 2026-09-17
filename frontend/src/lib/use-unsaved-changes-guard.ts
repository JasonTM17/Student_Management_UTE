'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

export interface UnsavedChangesGuardOptions {
  /**
   * Returns whether the current form state would be lost on navigation.
   * Passed as a getter (not a boolean) so listeners always read fresh state
   * without re-subscribing on every keystroke.
   */
  isDirty: () => boolean;
  /** Text shown by the browser dialog on tab close / hard navigation. */
  beforeUnloadMessage?: string;
  /** Text shown by the in-app confirm for in-page destructive actions. */
  confirmTitle?: string;
  confirmBody?: string;
  /** Set false to suspend the guard (e.g. while a save is in flight). */
  enabled?: boolean;
}

export interface UnsavedChangesGuard {
  /** Whether the in-app confirm dialog should be rendered. */
  confirmOpen: boolean;
  confirmTitle: string;
  confirmBody: string;
  /**
   * Runs `action` immediately when the form is clean; otherwise opens the
   * in-app confirm and defers `action` until the author accepts.
   */
  requestLeave: (action: () => void) => void;
  /** Accept: run the deferred action and close the dialog. */
  confirmLeave: () => void;
  /** Decline: close the dialog and keep editing. */
  cancelLeave: () => void;
}

/**
 * RT-P3-3: shared unsaved-changes guard for editors (announcement studio now,
 * grade sheet in a later phase). Combines the two layers a dirty editor needs:
 *
 * - a `beforeunload` listener for tab close / reload / external navigation,
 * - an in-app confirm for in-page destructive actions, which the browser
 *   dialog cannot cover because those actions are plain event handlers.
 *
 * The hook never navigates itself; callers decide what "leaving" means.
 */
export function useUnsavedChangesGuard({
  isDirty,
  beforeUnloadMessage = 'Bạn có nội dung chưa được lưu. Rời khỏi trang sẽ mất thay đổi.',
  confirmTitle = 'Nội dung chưa được lưu',
  confirmBody = 'Bạn có nội dung chưa được lưu. Rời khỏi sẽ mất các thay đổi. Tiếp tục?',
  enabled = true,
}: UnsavedChangesGuardOptions): UnsavedChangesGuard {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const pendingActionRef = useRef<(() => void) | null>(null);
  const dirtyRef = useRef(isDirty);
  dirtyRef.current = isDirty;
  const enabledRef = useRef(enabled);
  enabledRef.current = enabled;

  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!enabledRef.current || !dirtyRef.current()) return;
      event.preventDefault();
      // Chrome requires returnValue to be set to show the dialog.
      event.returnValue = beforeUnloadMessage;
      return beforeUnloadMessage;
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [beforeUnloadMessage]);

  const requestLeave = useCallback((action: () => void) => {
    if (!enabledRef.current || !dirtyRef.current()) {
      action();
      return;
    }
    pendingActionRef.current = action;
    setConfirmOpen(true);
  }, []);

  const confirmLeave = useCallback(() => {
    setConfirmOpen(false);
    const pending = pendingActionRef.current;
    pendingActionRef.current = null;
    pending?.();
  }, []);

  const cancelLeave = useCallback(() => {
    setConfirmOpen(false);
    pendingActionRef.current = null;
  }, []);

  return { confirmOpen, confirmTitle, confirmBody, requestLeave, confirmLeave, cancelLeave };
}
