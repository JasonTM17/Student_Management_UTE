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
  /**
   * LEC-P3-B1: additionally intercept in-app route changes — sidebar and
   * header <Link> clicks and browser Back/forward — which beforeunload never
   * sees because they are SPA navigations. Opt-in so existing editors keep
   * their current behavior until they adopt it.
   */
  interceptRouteChanges?: boolean;
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

/** Why the confirm dialog is open — 'popstate' needs a sentinel re-arm on cancel. */
type PendingKind = 'explicit' | 'anchor' | 'popstate';

/**
 * RT-P3-3 / LEC-P3-B1: shared unsaved-changes guard for editors (announcement
 * studio, grade sheet). A dirty editor needs four layers:
 *
 * - a `beforeunload` listener for tab close / reload / external navigation,
 * - an in-app confirm for in-page destructive actions (plain event handlers
 *   the browser dialog cannot cover), which callers trigger via `requestLeave`,
 * - with `interceptRouteChanges`: a capture-phase click listener on document
 *   that swallows same-origin <a> navigation while dirty, asks once, then
 *   replays the click so the Link's own handler performs the SPA push, and
 * - a history sentinel: when the form first turns dirty a duplicate entry is
 *   pushed for the current URL, so a Back press lands on the same page
 *   instead of unmounting it. The popstate handler then either confirms
 *   (Cancel re-arms the sentinel; OK replays `history.back()` for the real
 *   step) or, when clean, transparently replays the absorbed press.
 *
 * Next.js App Router exposes no router blocker API, which is why the
 * interception happens at the DOM/history boundary. The guard never
 * navigates itself beyond replaying the user's own intent; callers decide
 * what "leaving" means.
 */
export function useUnsavedChangesGuard({
  isDirty,
  beforeUnloadMessage = 'Bạn có nội dung chưa được lưu. Rời khỏi trang sẽ mất thay đổi.',
  confirmTitle = 'Nội dung chưa được lưu',
  confirmBody = 'Bạn có nội dung chưa được lưu. Rời khỏi sẽ mất các thay đổi. Tiếp tục?',
  enabled = true,
  interceptRouteChanges = false,
}: UnsavedChangesGuardOptions): UnsavedChangesGuard {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const pendingActionRef = useRef<(() => void) | null>(null);
  const pendingKindRef = useRef<PendingKind>('explicit');
  const dirtyRef = useRef(isDirty);
  dirtyRef.current = isDirty;
  const enabledRef = useRef(enabled);
  enabledRef.current = enabled;
  // True while the guard itself drives a navigation (replayed anchor click,
  // re-issued history.back) so the interceptors let it through instead of
  // re-asking or double-prompting beforeunload.
  const bypassRef = useRef(false);
  // Whether the duplicate history entry ("sentinel") is currently armed.
  const sentinelArmedRef = useRef(false);

  // Read the dirty bit once per render. The caller re-renders whenever its
  // edit state changes, so this value flipping false→true marks the
  // transition that arms the sentinel — the getter alone is inert.
  const dirtyNow = interceptRouteChanges && enabled && isDirty();

  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (bypassRef.current) return;
      if (!enabledRef.current || !dirtyRef.current()) return;
      event.preventDefault();
      // Chrome requires returnValue to be set to show the dialog.
      event.returnValue = beforeUnloadMessage;
      return beforeUnloadMessage;
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [beforeUnloadMessage]);

  const armSentinel = useCallback(() => {
    if (typeof window === 'undefined' || typeof window.history.pushState !== 'function') return;
    try {
      // Reuse the current state so the App Router restores the same page
      // when the duplicate entry is popped back into.
      window.history.pushState(window.history.state, '', window.location.href);
      sentinelArmedRef.current = true;
    } catch {
      // Browsers throttle pushState spam; the beforeunload layer still
      // covers tab close/reload if arming fails.
    }
  }, []);

  const replayHistoryBack = useCallback(() => {
    bypassRef.current = true;
    window.history.back();
    window.setTimeout(() => {
      bypassRef.current = false;
    }, 0);
  }, []);

  const armLeave = useCallback((kind: PendingKind, action: () => void) => {
    if (bypassRef.current || !enabledRef.current || !dirtyRef.current()) {
      action();
      return;
    }
    pendingActionRef.current = action;
    pendingKindRef.current = kind;
    setConfirmOpen(true);
  }, []);

  const requestLeave = useCallback(
    (action: () => void) => armLeave('explicit', action),
    [armLeave],
  );

  const confirmLeave = useCallback(() => {
    setConfirmOpen(false);
    const pending = pendingActionRef.current;
    pendingActionRef.current = null;
    pendingKindRef.current = 'explicit';
    pending?.();
  }, []);

  const cancelLeave = useCallback(() => {
    setConfirmOpen(false);
    const kind = pendingKindRef.current;
    pendingActionRef.current = null;
    pendingKindRef.current = 'explicit';
    if (kind === 'popstate') {
      // The back press was absorbed by the sentinel and the author chose to
      // stay: re-arm so the next back press is intercepted too.
      armSentinel();
    }
  }, [armSentinel]);

  // LEC-P3-B1 layer 1: same-origin <a> clicks while dirty. Capture phase at
  // document runs before the React root (and therefore before Link's own
  // onClick); the event is swallowed, one confirm is shown, and on accept
  // the click is replayed under the bypass so Link (for SPA links) or the
  // browser (for plain links, downloads stay excluded) performs the real
  // navigation.
  useEffect(() => {
    if (!interceptRouteChanges) return;
    const handleClick = (event: MouseEvent) => {
      if (bypassRef.current) return;
      if (!enabledRef.current || !dirtyRef.current()) return;
      if (event.defaultPrevented || event.button !== 0) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor =
        event.target instanceof Element
          ? event.target.closest<HTMLAnchorElement>('a[href]')
          : null;
      if (!anchor) return;
      if (anchor.target && anchor.target !== '_self') return;
      if (anchor.hasAttribute('download')) return;
      let url: URL;
      try {
        url = new URL(anchor.href, window.location.href);
      } catch {
        return;
      }
      // Cross-origin and non-http links are hard navigations; beforeunload
      // already covers them and router replay could not restore them anyway.
      if (url.origin !== window.location.origin) return;
      if (url.protocol !== 'http:' && url.protocol !== 'https:') return;
      // Same page (hash-only moves included) loses nothing.
      if (url.pathname === window.location.pathname && url.search === window.location.search) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      armLeave('anchor', () => {
        bypassRef.current = true;
        if (anchor.isConnected) {
          anchor.click();
        } else {
          window.location.assign(url.href);
        }
        window.setTimeout(() => {
          bypassRef.current = false;
        }, 0);
      });
    };
    document.addEventListener('click', handleClick, true);
    return () => document.removeEventListener('click', handleClick, true);
  }, [interceptRouteChanges, armLeave]);

  // LEC-P3-B1 layer 2: arm the history sentinel on the clean→dirty
  // transition so a Back press lands on this same URL (see popstate below).
  useEffect(() => {
    if (!interceptRouteChanges) return;
    if (!dirtyNow || sentinelArmedRef.current) return;
    armSentinel();
  }, [interceptRouteChanges, dirtyNow, armSentinel]);

  // LEC-P3-B1 layer 3: the App Router's own popstate handler has already
  // restored the same-URL page by the time this runs; decide whether the
  // absorbed press may continue (clean — replay it) or needs the confirm
  // first (dirty — OK replays the real step, Cancel re-arms the sentinel).
  useEffect(() => {
    if (!interceptRouteChanges) return;
    const handlePopState = () => {
      if (bypassRef.current) return;
      if (!enabledRef.current || !dirtyRef.current()) {
        if (sentinelArmedRef.current) {
          sentinelArmedRef.current = false;
          replayHistoryBack();
        }
        return;
      }
      if (sentinelArmedRef.current) {
        // The press just consumed the sentinel; we sit on the original
        // entry now, so one further back() is the real previous page.
        sentinelArmedRef.current = false;
      }
      armLeave('popstate', replayHistoryBack);
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [interceptRouteChanges, armLeave, replayHistoryBack]);

  return { confirmOpen, confirmTitle, confirmBody, requestLeave, confirmLeave, cancelLeave };
}
