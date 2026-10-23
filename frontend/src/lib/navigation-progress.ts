type TimerHandle = ReturnType<typeof setTimeout>;

export interface NavigationProgressState {
  active: boolean;
  progress: number;
}

export interface AnchorLike {
  href: string;
  target: string;
  getAttribute: (name: string) => string | null;
  hasAttribute: (name: string) => boolean;
}

export interface LocationLike {
  href: string;
  origin: string;
  pathname: string;
  search: string;
}

const RAMP_DELAY_MS = 120;
const FINISH_DELAY_MS = 250;
// An anchor click that never commits a route (swallowed by an interceptor,
// prevented by another handler, or simply abandoned) must still release the
// bar — nothing else resets it when the URL never changes.
const STALE_TIMEOUT_MS = 8000;

export function shouldStartProgress(anchor: AnchorLike, location: LocationLike): boolean {
  const href = anchor.getAttribute('href');
  if (
    !href ||
    href.startsWith('#') ||
    href.startsWith('mailto:') ||
    href.startsWith('tel:') ||
    href.startsWith('javascript:') ||
    (anchor.target !== '' && anchor.target !== '_self') ||
    anchor.hasAttribute('download')
  ) {
    return false;
  }
  try {
    const targetUrl = new URL(anchor.href, location.href);
    if (targetUrl.origin !== location.origin) return false;
    return targetUrl.pathname + targetUrl.search !== location.pathname + location.search;
  } catch {
    return false;
  }
}

export class NavigationProgressController {
  private phase: 'idle' | 'running' | 'finishing' = 'idle';
  private progress = 0;
  private rampTimer: TimerHandle | null = null;
  private finishTimer: TimerHandle | null = null;
  private staleTimer: TimerHandle | null = null;

  constructor(
    private readonly publish: (state: NavigationProgressState) => void,
    private readonly schedule: (fn: () => void, ms: number) => TimerHandle = (fn, ms) =>
      setTimeout(fn, ms),
    private readonly unschedule: (handle: TimerHandle) => void = (handle) => clearTimeout(handle),
  ) {}

  handleNavigationStart(): void {
    // Interrupt any in-flight completion fade, then re-arm from scratch.
    this.clear('finishTimer');
    this.phase = 'running';
    this.progress = 30;
    this.emit();
    this.clear('rampTimer');
    this.rampTimer = this.schedule(() => {
      if (this.phase !== 'running') return;
      this.progress = 70;
      this.emit();
    }, RAMP_DELAY_MS);
    this.clear('staleTimer');
    this.staleTimer = this.schedule(() => {
      if (this.phase === 'running') this.reset();
    }, STALE_TIMEOUT_MS);
  }

  handleRouteCommitted(): void {
    if (this.phase === 'idle') return;
    this.clear('rampTimer');
    this.clear('staleTimer');
    this.phase = 'finishing';
    this.progress = 100;
    this.emit();
    this.clear('finishTimer');
    this.finishTimer = this.schedule(() => this.reset(), FINISH_DELAY_MS);
  }

  dispose(): void {
    this.reset();
  }

  private reset(): void {
    this.clear('rampTimer');
    this.clear('finishTimer');
    this.clear('staleTimer');
    this.phase = 'idle';
    this.progress = 0;
    this.emit();
  }

  private clear(timer: 'rampTimer' | 'finishTimer' | 'staleTimer'): void {
    if (this[timer] !== null) {
      this.unschedule(this[timer]);
      this[timer] = null;
    }
  }

  private emit(): void {
    this.publish({ active: this.phase !== 'idle', progress: this.progress });
  }
}
