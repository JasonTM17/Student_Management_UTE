import { cn } from '@/lib/utils';

/**
 * Visual treatment of the AI Scholar mark (Stitch "CampusUTE AI Scholar").
 *
 * - `icon`     — pure `currentColor` outline for inline glyphs and small
 *                controls; inherits surrounding text colour.
 * - `detailed` — full-colour robot head without the app tile, for headers
 *                and panels on light or navy surfaces.
 * - `brand`    — the complete app-icon lockup (navy tile, gold border,
 *                constellations). For hero placements.
 */
export type AssistantMascotVariant = 'icon' | 'detailed' | 'brand';

/**
 * Expression of the mark.
 *
 * - `idle`      — resting scholar, smiling optics.
 * - `listening` — receiving input: widened optics, open mouth, pulsing ears.
 * - `thinking`  — request in flight: upcast optics, animated dot trail.
 * - `answering` — reply streaming: bright optics, sparkle lit.
 */
export type AssistantMascotState = 'idle' | 'listening' | 'thinking' | 'answering';

export interface AssistantMascotProps {
  className?: string;
  /** Convenience flag: maps to `state="thinking"` for backwards compatibility. */
  active?: boolean;
  variant?: AssistantMascotVariant;
  state?: AssistantMascotState;
}

const GOLD = 'url(#goldGrad)';

function Optics({ state, gold }: { state: AssistantMascotState; gold: string }) {
  if (state === 'thinking') {
    return (
      <g>
        <path d="M98 146 Q106 138 114 146" stroke="#E0F2FE" strokeWidth={4} strokeLinecap="round" fill="none" />
        <path d="M142 146 Q150 138 158 146" stroke="#E0F2FE" strokeWidth={4} strokeLinecap="round" fill="none" />
        <circle cx="110" cy="86" r="3" fill="#70E4FF" className="animate-pulse" />
        <circle cx="128" cy="80" r="3" fill="#70E4FF" className="animate-pulse [animation-delay:150ms]" />
        <circle cx="146" cy="86" r="3" fill="#70E4FF" className="animate-pulse [animation-delay:300ms]" />
      </g>
    );
  }
  const eyeR = state === 'listening' ? 10 : 9;
  return (
    <g>
      <circle cx="106" cy="144" r="14" fill="url(#opticGlow)" />
      <circle cx="106" cy="144" r={eyeR} fill="#0284C7" />
      <circle cx="106" cy="144" r="5" fill="#E0F2FE" />
      <circle cx="109" cy="141" r="2" fill="#FFFFFF" />
      <circle cx="150" cy="144" r="14" fill="url(#opticGlow)" />
      <circle cx="150" cy="144" r={eyeR} fill="#0284C7" />
      <circle cx="150" cy="144" r="5" fill="#E0F2FE" />
      <circle cx="153" cy="141" r="2" fill="#FFFFFF" />
      {state === 'listening' ? (
        <circle cx="128" cy="163" r="4" fill="none" stroke={gold} strokeWidth={2.5} />
      ) : (
        <path
          d={state === 'answering' ? 'M118 161 Q128 169 138 161' : 'M120 162 Q128 167 136 162'}
          stroke={gold}
          strokeWidth={2.5}
          strokeLinecap="round"
          fill="none"
        />
      )}
    </g>
  );
}

function RobotCore({ state }: { state: AssistantMascotState }) {
  return (
    <>
      <g id="ScholarHead">
        <rect x="62" y="96" width="132" height="106" rx="36" fill="#FFFFFF" />
        <rect x="63" y="97" width="130" height="104" rx="35" stroke="#E2E8F0" strokeWidth={1.5} />
        <rect
          x="52"
          y="126"
          width="10"
          height="42"
          rx="5"
          fill="#0B192C"
          className={state === 'listening' ? 'animate-pulse' : undefined}
        />
        <rect x="55" y="135" width="4" height="24" rx="2" fill={GOLD} />
        <rect
          x="194"
          y="126"
          width="10"
          height="42"
          rx="5"
          fill="#0B192C"
          className={state === 'listening' ? 'animate-pulse' : undefined}
        />
        <rect x="197" y="135" width="4" height="24" rx="2" fill={GOLD} />
        <rect x="74" y="112" width="108" height="66" rx="22" fill="url(#visorGrad)" />
        <rect x="74" y="112" width="108" height="66" rx="22" stroke="#1E3A8A" strokeWidth={1} />
        <Optics state={state} gold={GOLD} />
        <path d="M96 202 L160 202 L150 216 L106 216 Z" fill={GOLD} opacity={0.95} />
        <circle cx="128" cy="209" r="2.5" fill="#0B192C" />
      </g>
      <g id="Mortarboard">
        <polygon points="128,46 214,75 128,102 42,75" fill="#0B192C" stroke={GOLD} strokeWidth={2} />
        <path d="M82 86 C82 86 96 106 128 106 C160 106 174 86 174 86" fill="#0F2B5C" stroke="#1E3A8A" strokeWidth={1.5} />
        <ellipse cx="128" cy="74" rx="5" ry="4" fill={GOLD} />
        <path d="M128 74 Q100 80 84 100 Q78 112 76 126" fill="none" stroke={GOLD} strokeWidth={2.5} strokeLinecap="round" />
        <polygon points="76,126 71,142 81,142" fill={GOLD} />
        <circle cx="76" cy="127" r="2.5" fill="#FFFFFF" />
      </g>
      <path
        d="M198 94 L201 103 L210 106 L201 109 L198 118 L195 109 L186 106 L195 103 Z"
        fill={GOLD}
        opacity={state === 'answering' ? 1 : 0.85}
        className={state === 'answering' ? 'animate-pulse' : undefined}
      />
      <circle cx="218" cy="126" r="2" fill="#70E4FF" />
    </>
  );
}

function Defs() {
  return (
    <defs>
      <linearGradient id="primaryGrad" x1="20" y1="20" x2="236" y2="236" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stopColor="#0F2B5C" />
        <stop offset="50%" stopColor="#003F87" />
        <stop offset="100%" stopColor="#0B192C" />
      </linearGradient>
      <linearGradient id="goldGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#F6CE7A" />
        <stop offset="50%" stopColor="#E5A93C" />
        <stop offset="100%" stopColor="#B87D1B" />
      </linearGradient>
      <linearGradient id="visorGrad" x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" stopColor="#0D213F" />
        <stop offset="100%" stopColor="#06101E" />
      </linearGradient>
      <radialGradient id="opticGlow" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stopColor="#38BDF8" stopOpacity="0.9" />
        <stop offset="60%" stopColor="#0284C7" stopOpacity="0.4" />
        <stop offset="100%" stopColor="#0284C7" stopOpacity="0" />
      </radialGradient>
    </defs>
  );
}

function IconGlyph({ state, className }: { state: AssistantMascotState; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} className={className} aria-hidden="true">
      <path d="M3 6.2 12 3l9 3.2-9 3.2-9-3.2Z" strokeLinejoin="round" />
      <rect x="5.5" y="9.8" width="13" height="10.7" rx="3.6" />
      {state === 'thinking' ? (
        <>
          <path d="M8.4 14.6q1.1-1.1 2.2 0" strokeLinecap="round" />
          <path d="M13.4 14.6q1.1-1.1 2.2 0" strokeLinecap="round" />
        </>
      ) : (
        <>
          <circle cx="9.5" cy="14.4" r="1.1" fill="currentColor" stroke="none" />
          <circle cx="14.5" cy="14.4" r="1.1" fill="currentColor" stroke="none" />
        </>
      )}
      {state === 'listening' ? (
        <circle cx="12" cy="17.6" r="1.2" />
      ) : (
        <path d="M10 17.6q2 1.3 4 0" strokeLinecap="round" />
      )}
    </svg>
  );
}

export function AssistantMascot({
  className,
  active = false,
  variant = 'icon',
  state,
}: AssistantMascotProps) {
  const resolvedState: AssistantMascotState = state ?? (active ? 'thinking' : 'idle');

  if (variant === 'icon') {
    return <IconGlyph state={resolvedState} className={cn('h-5 w-5', className)} />;
  }

  return (
    <svg
      viewBox={variant === 'brand' ? '0 0 256 256' : '32 36 192 192'}
      fill="none"
      className={cn('h-10 w-10', className)}
      aria-hidden="true"
    >
      <Defs />
      {variant === 'brand' && (
        <>
          <rect x="16" y="16" width="224" height="224" rx="52" fill="url(#primaryGrad)" />
          <rect x="16.75" y="16.75" width="222.5" height="222.5" rx="51.25" stroke={GOLD} strokeWidth={1.5} strokeOpacity={0.45} />
          <g opacity={0.22} stroke="#E0F2FE" strokeWidth={1} strokeDasharray="2 3">
            <line x1="48" y1="64" x2="88" y2="44" />
            <line x1="168" y1="44" x2="208" y2="64" />
            <line x1="44" y1="180" x2="74" y2="206" />
            <line x1="212" y1="180" x2="182" y2="206" />
          </g>
          <circle cx="48" cy="64" r="2.5" fill="#E5A93C" />
          <circle cx="208" cy="64" r="2.5" fill="#E5A93C" />
          <circle cx="88" cy="44" r="2" fill="#70E4FF" />
          <circle cx="168" cy="44" r="2" fill="#70E4FF" />
        </>
      )}
      <RobotCore state={resolvedState} />
    </svg>
  );
}
