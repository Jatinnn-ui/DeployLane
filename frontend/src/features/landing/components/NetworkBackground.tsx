import { useId } from 'react';

/**
 * Background plane for the hero scene: a fine technical mesh, not a set of perspective rays.
 *
 * Two crossing diagonal families at a close pitch read as a subtle 3D network plane, which is
 * what sits behind the reference's chassis. It is deliberately quiet - the mesh supports the
 * composition, and the bright wiring lives in the foreground connector layer instead.
 *
 * Authored in the scene's canonical 620x410 coordinate system.
 */

const SCENE_W = 620;
const SCENE_H = 410;

const PITCH = 26;
const RUN = 300;

/** Down-right family. */
const DIAG_A = Array.from({ length: 38 }, (_, i) => -312 + i * PITCH);
/** Down-left family. */
const DIAG_B = Array.from({ length: 36 }, (_, i) => i * PITCH);
/** A few horizontals to give the mesh a ground plane. */
const ROWS = Array.from({ length: 12 }, (_, i) => 24 + i * 34);

const DOTS = [
  { x: 108, y: 96, r: 1.2, o: 0.45 },
  { x: 176, y: 62, r: 0.9, o: 0.3 },
  { x: 214, y: 176, r: 1.1, o: 0.38 },
  { x: 268, y: 118, r: 1.4, o: 0.5 },
  { x: 306, y: 62, r: 1, o: 0.32 },
  { x: 342, y: 158, r: 1.2, o: 0.42 },
  { x: 386, y: 108, r: 0.9, o: 0.3 },
  { x: 428, y: 196, r: 1.3, o: 0.45 },
  { x: 462, y: 128, r: 1, o: 0.34 },
  { x: 508, y: 232, r: 1.2, o: 0.4 },
  { x: 552, y: 168, r: 0.9, o: 0.3 },
  { x: 148, y: 268, r: 1.1, o: 0.36 },
  { x: 232, y: 322, r: 1.3, o: 0.42 },
  { x: 328, y: 372, r: 1, o: 0.3 },
  { x: 452, y: 344, r: 1.2, o: 0.38 },
  { x: 566, y: 296, r: 0.9, o: 0.3 },
];

export function NetworkBackground() {
  const uid = useId().replace(/:/g, '');
  const maskId = `net-mask-${uid}`;

  return (
    <svg
      className="pointer-events-none absolute inset-0 h-full w-full"
      viewBox={`0 0 ${SCENE_W} ${SCENE_H}`}
      preserveAspectRatio="none"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        {/* Densest behind the chassis, gone well before it reaches the hero copy. */}
        <radialGradient id={maskId} cx="0.5" cy="0.5" r="0.62">
          <stop offset="0.3" stopColor="#fff" stopOpacity="1" />
          <stop offset="0.68" stopColor="#fff" stopOpacity="0.55" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </radialGradient>
        <mask id={`${maskId}-m`}>
          <rect width={SCENE_W} height={SCENE_H} fill={`url(#${maskId})`} />
        </mask>
      </defs>

      <g mask={`url(#${maskId}-m)`} opacity="0.13">
        {DIAG_A.map((x) => (
          <line
            key={`a${x}`}
            x1={x}
            y1={-30}
            x2={x + RUN}
            y2={SCENE_H + 40}
            stroke="#7fbf00"
            strokeWidth="0.6"
          />
        ))}
        {DIAG_B.map((x) => (
          <line
            key={`b${x}`}
            x1={x}
            y1={-30}
            x2={x - RUN}
            y2={SCENE_H + 40}
            stroke="#7fbf00"
            strokeWidth="0.6"
          />
        ))}
        {ROWS.map((y) => (
          <line
            key={`r${y}`}
            x1={-40}
            y1={y}
            x2={SCENE_W + 40}
            y2={y}
            stroke="#7fbf00"
            strokeWidth="0.5"
          />
        ))}
      </g>

      <g mask={`url(#${maskId}-m)`}>
        {DOTS.map((dot) => (
          <circle
            key={`${dot.x}:${dot.y}`}
            cx={dot.x}
            cy={dot.y}
            r={dot.r}
            fill="#a8f000"
            fillOpacity={dot.o}
          />
        ))}
      </g>
    </svg>
  );
}
