import { useId } from 'react';

/**
 * Perspective grid and particle field behind the deployment scene.
 *
 * Lines are generated rather than hand-authored: horizontals use an eased step so they
 * bunch toward the horizon, and verticals converge on a vanishing point. A radial mask keeps
 * the field strongest behind the server and fades it out before it reaches the hero copy,
 * which is what stops it competing with the foreground.
 */

const VANISH_X = 300;
const VANISH_Y = 40;

/** Eased spacing: rows compress toward the horizon. */
const ROWS = Array.from({ length: 10 }, (_, i) => {
  const t = (i + 1) / 10;
  return VANISH_Y + t ** 2.3 * 430;
});

const COLUMNS = Array.from({ length: 13 }, (_, i) => -260 + i * 90);

const PARTICLES = [
  { x: 96, y: 128, r: 1.5, o: 0.5 },
  { x: 168, y: 74, r: 1.1, o: 0.35 },
  { x: 214, y: 196, r: 1.3, o: 0.4 },
  { x: 322, y: 96, r: 1.6, o: 0.55 },
  { x: 398, y: 156, r: 1.2, o: 0.4 },
  { x: 452, y: 246, r: 1.5, o: 0.45 },
  { x: 508, y: 112, r: 1.1, o: 0.3 },
  { x: 556, y: 208, r: 1.4, o: 0.5 },
  { x: 138, y: 286, r: 1.2, o: 0.35 },
  { x: 268, y: 330, r: 1.3, o: 0.4 },
  { x: 486, y: 348, r: 1.1, o: 0.3 },
  { x: 588, y: 296, r: 1.5, o: 0.45 },
];

/** A few brighter intersections, so the field reads as a network and not just a grid. */
const BRIGHT = [
  { x: 240, y: 152 },
  { x: 366, y: 214 },
  { x: 148, y: 232 },
  { x: 470, y: 178 },
];

export function NetworkBackground() {
  const uid = useId().replace(/:/g, '');
  const maskId = `net-mask-${uid}`;
  const glowId = `net-glow-${uid}`;

  return (
    <svg
      className="pointer-events-none absolute inset-0 h-full w-full"
      viewBox="0 0 614 440"
      preserveAspectRatio="none"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        {/* Strongest behind the appliance, gone before it reaches the hero copy. */}
        <radialGradient id={maskId} cx="0.48" cy="0.47" r="0.62">
          <stop offset="0.35" stopColor="#fff" stopOpacity="1" />
          <stop offset="0.65" stopColor="#fff" stopOpacity="0.65" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </radialGradient>
        <mask id={`${maskId}-m`}>
          <rect width="614" height="440" fill={`url(#${maskId})`} />
        </mask>
        <filter id={glowId} x="-300%" y="-300%" width="700%" height="700%">
          <feGaussianBlur stdDeviation="2" />
        </filter>
      </defs>

      <g mask={`url(#${maskId}-m)`} opacity="0.16">
        {ROWS.map((y) => (
          <line key={`r${y}`} x1="-120" y1={y} x2="740" y2={y} stroke="#7fbf00" strokeWidth="0.7" />
        ))}
        {COLUMNS.map((x) => (
          <line
            key={`c${x}`}
            x1={VANISH_X}
            y1={VANISH_Y}
            x2={x}
            y2="500"
            stroke="#7fbf00"
            strokeWidth="0.7"
          />
        ))}
      </g>

      <g mask={`url(#${maskId}-m)`}>
        {PARTICLES.map((particle) => (
          <circle
            key={`${particle.x}:${particle.y}`}
            cx={particle.x}
            cy={particle.y}
            r={particle.r}
            fill="#a8f000"
            fillOpacity={particle.o}
          />
        ))}
        {BRIGHT.map((node) => (
          <g key={`${node.x}:${node.y}`}>
            <circle cx={node.x} cy={node.y} r="3.4" fill="#b6ff00" opacity="0.5" filter={`url(#${glowId})`} />
            <circle cx={node.x} cy={node.y} r="1.5" fill="#d7ff7a" />
          </g>
        ))}
      </g>
    </svg>
  );
}
