import { useThemeStore } from '@/stores/theme-store';
import { cn } from '@/lib/utils';

/**
 * DeployLane brand lockup — layered chevron arrow mark + wordmark.
 *
 * Built as inline SVG so it stays crisp at any size, inherits theme tokens,
 * and doesn't require external asset loading. The mark consists of stacked
 * chevron arrows with horizontal motion lines conveying speed/deployment.
 *
 * In dark mode: "Deploy" is white, "Lane" is lime.
 * In light mode: "Deploy" is near-black, "Lane" is dark green.
 */
export function BrandLogo({ className }: { className?: string }) {
  const theme = useThemeStore((state) => state.theme);
  const isDark = theme === 'dark';

  // Wordmark colors
  const deployColor = isDark ? '#f5f5f5' : '#1a1c1a';
  const laneColor = isDark ? '#a8f000' : '#4a6800';

  // Arrow mark colors
  const arrowPrimary = '#a8f000';
  const arrowSecondary = isDark ? '#7ab800' : '#5a7a00';
  const arrowTertiary = isDark ? '#4d7500' : '#3d5600';
  const motionLines = isDark ? 'rgba(168, 240, 0, 0.5)' : 'rgba(74, 104, 0, 0.45)';

  return (
    <svg
      viewBox="0 0 180 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn('h-full w-auto', className)}
      aria-label="DeployLane"
      role="img"
    >
      {/* === Layered chevron arrow mark === */}
      {/* Back arrow (smallest, darkest) */}
      <path
        d="M4 12 L14 20 L4 28"
        stroke={arrowTertiary}
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
      {/* Middle arrow */}
      <path
        d="M10 10 L22 20 L10 30"
        stroke={arrowSecondary}
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
      {/* Front arrow (largest, brightest) */}
      <path
        d="M16 8 L30 20 L16 32"
        stroke={arrowPrimary}
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />

      {/* Motion/speed lines */}
      <line x1="1" y1="17" x2="8" y2="17" stroke={motionLines} strokeWidth="1.5" strokeLinecap="round" />
      <line x1="2" y1="20" x2="11" y2="20" stroke={motionLines} strokeWidth="1.5" strokeLinecap="round" />
      <line x1="1" y1="23" x2="8" y2="23" stroke={motionLines} strokeWidth="1.5" strokeLinecap="round" />
      <line x1="3" y1="14" x2="7" y2="14" stroke={motionLines} strokeWidth="1" strokeLinecap="round" />
      <line x1="3" y1="26" x2="7" y2="26" stroke={motionLines} strokeWidth="1" strokeLinecap="round" />

      {/* === Wordmark === */}
      {/* "Deploy" — bold italic */}
      <text
        x="36"
        y="26.5"
        fontFamily="Inter, -apple-system, BlinkMacSystemFont, sans-serif"
        fontSize="19"
        fontWeight="700"
        fontStyle="italic"
        fill={deployColor}
        letterSpacing="-0.5"
      >
        Deploy
      </text>
      {/* "Lane" — bold italic, lime accent */}
      <text
        x="103"
        y="26.5"
        fontFamily="Inter, -apple-system, BlinkMacSystemFont, sans-serif"
        fontSize="19"
        fontWeight="700"
        fontStyle="italic"
        fill={laneColor}
        letterSpacing="-0.5"
      >
        Lane
      </text>
    </svg>
  );
}
