import { cn } from '@/lib/utils';

/**
 * Visual treatment of the mark.
 *
 * - `icon`    — pure `currentColor` outline. For inline glyphs inside text and
 *               for small controls, where the mark must inherit the surrounding
 *               text colour and never introduce its own palette.
 * - `detailed`— `currentColor` at two opacity tiers plus a single antique-gold
 *               accent. Adapts to whatever surface it sits on, so the same mark
 *               is legible on the deep-navy portal chrome and on a light card.
 * - `brand`   — the fixed brand lockup (institutional navy body, warm off-white
 *               facing, gold accent). Only for light or neutral surfaces; use
 *               `detailed` on any dark or tinted surface.
 */
export type AssistantMascotVariant = 'icon' | 'detailed' | 'brand';

/**
 * Expression of the mark. Differences are intentionally small and are drawn to
 * stay readable from about 20px upward; below that the silhouettes still differ
 * but the expression details are not resolvable.
 *
 * - `idle`      — resting scholar.
 * - `listening` — the assistant is receiving input.
 * - `thinking`  — a request is in flight awaiting a reply.
 * - `answering` — a reply is streaming.
 */
export type AssistantMascotState = 'idle' | 'listening' | 'thinking' | 'answering';

export interface AssistantMascotProps {
  className?: string;
  /** Convenience flag: maps to `state="thinking"` for backwards compatibility. */
  active?: boolean;
  variant?: AssistantMascotVariant;
  state?: AssistantMascotState;
}

const BRAND_NAVY = '#003F87';
const BRAND_GOLD = '#C5B358';
const BRAND_FACE = '#FBF8F1';

/**
 * CampusUTE academic-scholar mascot: an owl in a mortarboard, seated on an open
 * book.
 *
 * The owl is drawn as flat geometry with no gradients and no raster shading, so
 * it reproduces exactly as hand-authored SVG and stays crisp from a 14px inline
 * glyph up to a hero lockup. It deliberately carries an academic motif (cap,
 * tassel, faculty facial discs, open book) rather than a cybernetic one, because
 * the assistant is an academic-advising office rather than a technology product.
 *
 * The `detailed` and `icon` variants draw entirely in `currentColor`, which is
 * what makes the mark legible on every surface the portal uses: on the navy
 * assistant header it inherits the light text colour, on a light card it
 * inherits the dark text colour. Only the tassel and the beak take the gold
 * accent, which is the single accent the brand permits.
 */
export function AssistantMascot({
  className,
  active = false,
  variant = 'icon',
  state,
}: AssistantMascotProps) {
  const resolvedState: AssistantMascotState = state ?? (active ? 'thinking' : 'idle');
  const isBrand = variant === 'brand';
  const isIcon = variant === 'icon';

  // Body strokes: `icon` needs weight to survive 14px; `detailed` is drawn at
  // sizes where a finer line reads better.
  const bodyStroke = isIcon ? 1.35 : 1.15;
  const detailStroke = isIcon ? 1.1 : 0.95;

  // Body plane and facing fill. `icon` stays purely monochrome so it can sit
  // inside a run of text; `detailed` separates the planes with opacity tiers.
  //
  // The facing must be DARKER than the head, not equal to it: both draw in
  // `currentColor`, so an equal-tier pair collapses the owl's face into one
  // solid mass at small sizes. The head stays a light tint and the facial discs
  // take the heavier tint, which is what makes the owl read as an owl.
  const bodyFill = isBrand ? BRAND_NAVY : 'currentColor';
  const bodyFillOpacity = isBrand ? 1 : 0.12;
  const facingFill = isBrand ? BRAND_FACE : 'currentColor';
  const facingFillOpacity = isBrand ? 1 : 0.34;
  const accent = BRAND_GOLD;

  // Outer silhouette stroke. In `brand` mode the body fill is a fixed navy, which
  // would vanish on a navy surface, so the silhouette keyline follows
  // `currentColor`: dark on a light surface (matching the fill), light on a dark
  // one (holding the edge). The brand lockup is still intended for light or
  // neutral surfaces — `detailed` is the correct choice on dark or tinted
  // chrome — but a misuse can no longer render an invisible mark.
  const silhouetteStroke = 'currentColor';
  const interiorStroke = isBrand ? BRAND_NAVY : 'currentColor';

  // Pupils shift to carry the expression without redrawing the silhouette.
  const pupilShift =
    resolvedState === 'thinking'
      ? { dx: 0.34, dy: -0.34 }
      : resolvedState === 'listening'
        ? { dx: 0, dy: 0.24 }
        : { dx: 0, dy: 0 };

  const eyeRadius = resolvedState === 'listening' ? 1.26 : 1.1;
  const glint = (cx: number, cy: number, key: string) => (
    <circle
      key={key}
      cx={cx + pupilShift.dx - 0.38}
      cy={cy + pupilShift.dy - 0.38}
      r={0.4}
      fill={BRAND_FACE}
      opacity={isIcon ? 0 : 0.9}
    />
  );

  const leftEye = { cx: 9.1, cy: 13.9 };
  const rightEye = { cx: 14.9, cy: 13.9 };

  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className={cn(
        'shrink-0',
        resolvedState === 'thinking' && 'motion-safe:animate-pulse motion-reduce:animate-none',
        className,
      )}
    >
      {/* Mortarboard board */}
      <path
        d="M12 1.7 L21.7 5.5 L12 9.3 L2.3 5.5 Z"
        fill={isBrand ? BRAND_NAVY : 'currentColor'}
        fillOpacity={isBrand ? 1 : 0.16}
        stroke={silhouetteStroke}
        strokeWidth={bodyStroke}
        strokeLinejoin="round"
      />
      {/* Cap button */}
      <circle cx="12" cy="5.5" r="0.78" fill={isBrand ? BRAND_FACE : 'currentColor'} />

      {/* Tassel ribbon — the mark's single gold accent */}
      <path
        d="M12 5.5 C14.7 5.9 16.7 7.5 17.5 9.7"
        stroke={accent}
        strokeWidth={detailStroke}
        strokeLinecap="round"
      />
      {/* Tassel fringe */}
      <path d="M17.5 9.5 L19 11.7 L16.1 11.7 Z" fill={accent} />

      {/* Owl head */}
      <path
        d="M5.5 12.5 C5.5 10.3 8.4 9.4 12 9.4 C15.6 9.4 18.5 10.3 18.5 12.5 L18.5 15.3 C18.5 18 15.6 19.6 12 19.6 C8.4 19.6 5.5 18 5.5 15.3 Z"
        fill={bodyFill}
        fillOpacity={bodyFillOpacity}
        stroke={silhouetteStroke}
        strokeWidth={bodyStroke}
        strokeLinejoin="round"
      />

      {/* Facial discs — the owl's defining feature */}
      <circle
        cx={leftEye.cx}
        cy={leftEye.cy}
        r="2.6"
        fill={facingFill}
        fillOpacity={facingFillOpacity}
        stroke={interiorStroke}
        strokeWidth={detailStroke}
      />
      <circle
        cx={rightEye.cx}
        cy={rightEye.cy}
        r="2.6"
        fill={facingFill}
        fillOpacity={facingFillOpacity}
        stroke={interiorStroke}
        strokeWidth={detailStroke}
      />

      {/* Attentive eyes */}
      <circle
        cx={leftEye.cx + pupilShift.dx}
        cy={leftEye.cy + pupilShift.dy}
        r={eyeRadius}
        fill={isBrand ? BRAND_NAVY : 'currentColor'}
      />
      <circle
        cx={rightEye.cx + pupilShift.dx}
        cy={rightEye.cy + pupilShift.dy}
        r={eyeRadius}
        fill={isBrand ? BRAND_NAVY : 'currentColor'}
      />
      {glint(leftEye.cx, leftEye.cy, 'glint-left')}
      {glint(rightEye.cx, rightEye.cy, 'glint-right')}

      {/* Beak */}
      <path
        d="M12 14.2 L13.15 15.8 L12 17.3 L10.85 15.8 Z"
        fill={accent}
      />

      {/* Open book the scholar sits on */}
      <path
        d="M7.2 19.9 C8.7 19.3 10.4 19.3 12 20.15 C13.6 19.3 15.3 19.3 16.8 19.9 L16.8 21.9 C15.3 21.3 13.6 21.3 12 22.15 C10.4 21.3 8.7 21.3 7.2 21.9 Z"
        fill={bodyFill}
        fillOpacity={isBrand ? 1 : 0.2}
        stroke={silhouetteStroke}
        strokeWidth={detailStroke}
        strokeLinejoin="round"
      />

      {/* Expression accents */}
      {resolvedState === 'listening' && (
        <>
          <path
            d="M3.4 12.1 A2.5 2.5 0 0 0 3.4 15.7"
            stroke={accent}
            strokeWidth={detailStroke}
            strokeLinecap="round"
          />
          <path
            d="M20.6 12.1 A2.5 2.5 0 0 1 20.6 15.7"
            stroke={accent}
            strokeWidth={detailStroke}
            strokeLinecap="round"
          />
        </>
      )}
      {resolvedState === 'thinking' && (
        <>
          <circle cx="19.3" cy="8.4" r="0.62" fill={accent} />
          <circle cx="21" cy="6.8" r="0.52" fill={accent} />
          <circle cx="22.3" cy="5.1" r="0.42" fill={accent} />
        </>
      )}
      {resolvedState === 'answering' && (
        <>
          <path
            d="M17.9 15.1 A3.2 3.2 0 0 1 17.9 19.1"
            stroke={accent}
            strokeWidth={detailStroke}
            strokeLinecap="round"
          />
          <path
            d="M20.1 13.7 A5.2 5.2 0 0 1 20.1 20.5"
            stroke={accent}
            strokeWidth={detailStroke}
            strokeLinecap="round"
            opacity="0.65"
          />
        </>
      )}
    </svg>
  );
}
