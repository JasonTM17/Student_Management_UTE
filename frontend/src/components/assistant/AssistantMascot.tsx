import { cn } from '@/lib/utils';

export interface AssistantMascotProps {
  className?: string;
  /** Adds a gentle "thinking" pulse/glow while the assistant is replying. */
  active?: boolean;
  /**
   * - 'icon': crisp vector using currentColor for seamless UI integration (sidebar, buttons, inputs).
   * - 'detailed': full collegiate vector with rich institutional navy, amber gold, and glowing cyan optics.
   */
  variant?: 'icon' | 'detailed';
}

/**
 * CampusUTE Academic AI Scholar Mascot & Icon.
 *
 * Designed with Google Stitch MCP for CampusCore / CampusUTE.
 * Blends academic authority (graduation mortarboard cap with golden tassel)
 * with intelligent AI robotics (sleek cyber visor, smart optics, and audio sensors).
 *
 * Fully scalable from 14px micro-tokens up to large hero displays.
 */
export function AssistantMascot({
  className,
  active = false,
  variant = 'icon',
}: AssistantMascotProps) {
  if (variant === 'detailed') {
    return (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
        className={cn(
          'shrink-0',
          active && 'motion-safe:animate-pulse motion-reduce:animate-none',
          className,
        )}
      >
        <defs>
          <linearGradient id="mascotGold" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#70E4FF" />
            <stop offset="50%" stopColor="#38BDF8" />
            <stop offset="100%" stopColor="#0284C7" />
          </linearGradient>
          <linearGradient id="mascotNavy" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#0F2B5C" />
            <stop offset="100%" stopColor="#0B192C" />
          </linearGradient>
          <linearGradient id="mascotOptic" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#70E4FF" />
            <stop offset="100%" stopColor="#0284C7" />
          </linearGradient>
        </defs>

        {/* Cap skull rim underneath */}
        <path
          d="M6.8 7.4 C6.8 7.4 8.6 9.8 12 9.8 C15.4 9.8 17.2 7.4 17.2 7.4"
          fill="#0F2B5C"
          stroke="#1E3A8A"
          strokeWidth="0.8"
        />

        {/* Head Shell */}
        <rect
          x="4.8"
          y="9.2"
          width="14.4"
          height="11.4"
          rx="4"
          fill="#FFFFFF"
          stroke="#CBD5E1"
          strokeWidth="1"
        />

        {/* Ear Sensors */}
        <path
          d="M4.8 13.2H3.8a0.8 0.8 0 0 0 0 1.6h1"
          stroke="url(#mascotGold)"
          strokeWidth="1.2"
          strokeLinecap="round"
        />
        <path
          d="M19.2 13.2h1a0.8 0.8 0 0 1 0 1.6h-1"
          stroke="url(#mascotGold)"
          strokeWidth="1.2"
          strokeLinecap="round"
        />

        {/* Cyber Visor */}
        <rect
          x="6.8"
          y="11"
          width="10.4"
          height="6"
          rx="2.2"
          fill="url(#mascotNavy)"
        />

        {/* Glowing Smart Optics */}
        <circle cx="9.8" cy="14" r="1.3" fill="url(#mascotOptic)" />
        <circle cx="9.8" cy="14" r="0.5" fill="#FFFFFF" />
        <circle cx="14.2" cy="14" r="1.3" fill="url(#mascotOptic)" />
        <circle cx="14.2" cy="14" r="0.5" fill="#FFFFFF" />

        {/* Micro Smile / Waveform */}
        <path
          d="M10.8 15.8 Q12 16.5 13.2 15.8"
          stroke="url(#mascotGold)"
          strokeWidth="0.9"
          strokeLinecap="round"
          fill="none"
        />

        {/* Academic Mortarboard Diamond Cap */}
        <polygon
          points="12,2.2 21.6,5.8 12,9.4 2.4,5.8"
          fill="#0B192C"
          stroke="url(#mascotGold)"
          strokeWidth="1.2"
          strokeLinejoin="round"
        />

        {/* Cap Button */}
        <circle cx="12" cy="5.8" r="0.9" fill="url(#mascotGold)" />

        {/* Golden Tassel ribbon */}
        <path
          d="M12 5.8 Q8.5 7.2 7 9.6 Q6.2 11.2 6.2 12.8"
          stroke="url(#mascotGold)"
          strokeWidth="1.2"
          strokeLinecap="round"
          fill="none"
        />
        {/* Tassel Fringe */}
        <polygon points="6.2,12.8 5.2,14.6 7.2,14.6" fill="url(#mascotGold)" />
      </svg>
    );
  }

  // Standard lightweight vector (variant === 'icon')
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className={cn(
        'shrink-0',
        active && 'motion-safe:animate-pulse motion-reduce:animate-none',
        className,
      )}
    >
      {/* Mortarboard Skull Rim */}
      <path
        d="M6.8 7.4 C6.8 7.4 8.6 9.8 12 9.8 C15.4 9.8 17.2 7.4 17.2 7.4"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinecap="round"
        fill="none"
      />

      {/* Head Shell */}
      <rect
        x="4.8"
        y="9.2"
        width="14.4"
        height="11.4"
        rx="4"
        stroke="currentColor"
        strokeWidth="1.4"
      />

      {/* Cyber Visor */}
      <rect
        x="6.8"
        y="11"
        width="10.4"
        height="6"
        rx="2.2"
        fill="currentColor"
        fillOpacity="0.16"
        stroke="currentColor"
        strokeWidth="1.1"
      />

      {/* Optics / Eyes */}
      <circle cx="9.8" cy="14" r="1.25" fill="currentColor" />
      <circle cx="14.2" cy="14" r="1.25" fill="currentColor" />

      {/* Micro Smile */}
      <path
        d="M10.8 15.8 Q12 16.5 13.2 15.8"
        stroke="currentColor"
        strokeWidth="1"
        strokeLinecap="round"
        fill="none"
      />

      {/* Ear Sensors */}
      <path
        d="M4.8 13.2H3.8a0.8 0.8 0 0 0 0 1.6h1"
        stroke="currentColor"
        strokeWidth="1.1"
        strokeLinecap="round"
      />
      <path
        d="M19.2 13.2h1a0.8 0.8 0 0 1 0 1.6h-1"
        stroke="currentColor"
        strokeWidth="1.1"
        strokeLinecap="round"
      />

      {/* Mortarboard Diamond Cap */}
      <polygon
        points="12,2.2 21.6,5.8 12,9.4 2.4,5.8"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinejoin="round"
        fill="currentColor"
        fillOpacity="0.18"
      />

      {/* Cap Center Button */}
      <circle cx="12" cy="5.8" r="0.9" fill="currentColor" />

      {/* Tassel Ribbon */}
      <path
        d="M12 5.8 Q8.5 7.2 7 9.6 Q6.2 11.2 6.2 12.8"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinecap="round"
        fill="none"
      />
      {/* Tassel Fringe */}
      <polygon points="6.2,12.8 5.2,14.6 7.2,14.6" fill="currentColor" />
    </svg>
  );
}
