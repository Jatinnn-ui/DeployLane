import { useId } from 'react';
import { cn } from '@/lib/utils';

/**
 * The DeployLane deployment server: a rounded hardware cube on a lit circular platform.
 *
 * Inline SVG rather than WebGL so it renders on first paint and its geometry can be measured
 * against the reference's bounding boxes.
 *
 * Corners are genuinely rounded rather than mitred: `roundedPath` walks each polygon and
 * replaces every vertex with a quadratic through pulled-back edge points. That is what stops
 * the body reading as a flat open-top box, which a plain `<polygon>` cannot avoid.
 */

type Point = [number, number];

function roundedPath(points: Point[], radius: number): string {
  const count = points.length;
  const segments: string[] = [];

  for (let i = 0; i < count; i += 1) {
    const previous = points[(i - 1 + count) % count];
    const current = points[i];
    const next = points[(i + 1) % count];

    // Clamp the pull-back so short edges cannot invert the curve.
    const back = pullBack(current, previous, radius);
    const forward = pullBack(current, next, radius);

    segments.push(
      i === 0 ? `M ${back[0]} ${back[1]}` : `L ${back[0]} ${back[1]}`,
      `Q ${current[0]} ${current[1]} ${forward[0]} ${forward[1]}`,
    );
  }

  return `${segments.join(' ')} Z`;
}

function pullBack(from: Point, toward: Point, radius: number): Point {
  const dx = toward[0] - from[0];
  const dy = toward[1] - from[1];
  const length = Math.hypot(dx, dy) || 1;
  const distance = Math.min(radius, length / 2);

  return [from[0] + (dx / length) * distance, from[1] + (dy / length) * distance];
}

/* Isometric cube. Top rhombus is 210 wide × 110 tall; the body drops 88. */
const HALF_W = 105;
const HALF_D = 55;
const BODY = 88;
const CX = 200;
const TOP_Y = 150;

const T: Point = [CX, TOP_Y - HALF_D];
const R: Point = [CX + HALF_W, TOP_Y];
const B: Point = [CX, TOP_Y + HALF_D];
const L: Point = [CX - HALF_W, TOP_Y];
const LB: Point = [L[0], L[1] + BODY];
const BB: Point = [B[0], B[1] + BODY];
const RB: Point = [R[0], R[1] + BODY];

const RADIUS = 15;

/** Unit-square mapping for each visible face, so decoration inherits the isometric shear. */
const FACE_LEFT = `matrix(${HALF_W} ${HALF_D} 0 ${BODY} ${L[0]} ${L[1]})`;
const FACE_RIGHT = `matrix(${HALF_W} ${-HALF_D} 0 ${BODY} ${B[0]} ${B[1]})`;
const FACE_TOP = `matrix(${HALF_W} ${HALF_D} ${HALF_W} ${-HALF_D} ${L[0]} ${L[1]})`;

const CHEVRON = 'M 0.32 0.20 L 0.60 0.50 L 0.32 0.80 L 0.44 0.91 L 0.83 0.50 L 0.44 0.09 Z';

/* Platform. Flatter than the cube's top face, matching the reference's lower camera. */
const BASE_CY = 276;
const BASE_RX = 144;
const BASE_RATIO = 0.27;

export function ServerIllustration({ className }: { className?: string }) {
  // Two instances of this component appear on the page, so paint-server ids must be scoped
  // per instance or the second copy would reference the first one's defs.
  const uid = useId().replace(/:/g, '');
  const ref = (name: string) => `${name}-${uid}`;
  const url = (name: string) => `url(#${ref(name)})`;

  return (
    <svg
      viewBox="0 40 400 320"
      className={cn('h-auto w-full', className)}
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={ref('top')} x1="0.1" y1="0" x2="0.8" y2="1">
          <stop offset="0" stopColor="#3d4548" />
          <stop offset="0.5" stopColor="#242b2d" />
          <stop offset="1" stopColor="#171d1e" />
        </linearGradient>
        <linearGradient id={ref('left')} x1="0" y1="0" x2="0.4" y2="1">
          <stop offset="0" stopColor="#20272a" />
          <stop offset="0.55" stopColor="#14191b" />
          <stop offset="1" stopColor="#0b0f10" />
        </linearGradient>
        <linearGradient id={ref('right')} x1="0" y1="0" x2="0.7" y2="1">
          <stop offset="0" stopColor="#151b1c" />
          <stop offset="1" stopColor="#080b0c" />
        </linearGradient>
        <linearGradient id={ref('cyl')} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#0a0e0f" />
          <stop offset="0.5" stopColor="#151a1b" />
          <stop offset="1" stopColor="#080b0c" />
        </linearGradient>
        <radialGradient id={ref('bloom')}>
          <stop offset="0" stopColor="#a8f000" stopOpacity="0.42" />
          <stop offset="0.5" stopColor="#a8f000" stopOpacity="0.11" />
          <stop offset="1" stopColor="#a8f000" stopOpacity="0" />
        </radialGradient>
        <filter id={ref('glow')} x="-80%" y="-80%" width="260%" height="260%">
          <feGaussianBlur stdDeviation="2.6" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <filter id={ref('soft')} x="-90%" y="-90%" width="280%" height="280%">
          <feGaussianBlur stdDeviation="7" />
        </filter>
      </defs>

      {/* Ground bloom, tight to the platform so the field stays black. */}
      <ellipse cx={CX} cy={BASE_CY + 6} rx={190} ry={62} fill={url('bloom')} />

      {/* Wide, very faint outer rings. */}
      {[196, 172].map((rx, index) => (
        <ellipse
          key={rx}
          cx={CX}
          cy={BASE_CY}
          rx={rx}
          ry={rx * BASE_RATIO}
          fill="none"
          stroke="#a8f000"
          strokeOpacity={index === 0 ? 0.1 : 0.16}
          strokeWidth="1"
        />
      ))}

      {/* Platform: cylinder wall, lit top rim, concentric interface rings. */}
      <path
        d={`M ${CX - BASE_RX} ${BASE_CY} A ${BASE_RX} ${BASE_RX * BASE_RATIO} 0 0 0 ${CX + BASE_RX} ${BASE_CY} L ${CX + BASE_RX} ${BASE_CY + 15} A ${BASE_RX} ${BASE_RX * BASE_RATIO} 0 0 1 ${CX - BASE_RX} ${BASE_CY + 15} Z`}
        fill={url('cyl')}
      />
      <ellipse cx={CX} cy={BASE_CY} rx={BASE_RX} ry={BASE_RX * BASE_RATIO} fill="#0a0e0f" />
      <g filter={url('glow')}>
        <ellipse
          cx={CX}
          cy={BASE_CY}
          rx={BASE_RX}
          ry={BASE_RX * BASE_RATIO}
          fill="none"
          stroke="#a8f000"
          strokeOpacity="0.85"
          strokeWidth="1.5"
        />
      </g>
      {[118, 92].map((rx) => (
        <ellipse
          key={rx}
          cx={CX}
          cy={BASE_CY}
          rx={rx}
          ry={rx * BASE_RATIO}
          fill="none"
          stroke="#a8f000"
          strokeOpacity="0.34"
          strokeWidth="1"
        />
      ))}

      {/* Body faces. Left and right first, then the top so its rounding overlaps cleanly. */}
      <path d={roundedPath([L, B, BB, LB], RADIUS)} fill={url('left')} />
      <path d={roundedPath([B, R, RB, BB], RADIUS)} fill={url('right')} />
      <path
        d={roundedPath([L, B, BB, LB], RADIUS)}
        fill="none"
        stroke="#394244"
        strokeOpacity="0.75"
        strokeWidth="1"
      />
      <path
        d={roundedPath([B, R, RB, BB], RADIUS)}
        fill="none"
        stroke="#2c3435"
        strokeOpacity="0.7"
        strokeWidth="1"
      />
      <path d={roundedPath([T, R, B, L], RADIUS)} fill={url('top')} />
      <path
        d={roundedPath([T, R, B, L], RADIUS)}
        fill="none"
        stroke="#535d60"
        strokeOpacity="0.9"
        strokeWidth="1.1"
      />

      {/* Recessed lid panel with the small brand mark. */}
      <g transform={FACE_TOP}>
        <rect
          x="0.13"
          y="0.13"
          width="0.74"
          height="0.74"
          rx="0.06"
          fill="#161c1d"
          stroke="#404a4c"
          strokeWidth="0.009"
        />
      </g>
      <g transform={FACE_TOP} filter={url('glow')}>
        <path d={CHEVRON} transform="translate(0.3 0.3) scale(0.4)" fill="#b5ff00" />
      </g>

      {/* Neon rim under the top edge — the reference's defining highlight. */}
      <g filter={url('glow')}>
        <path
          d={`M ${L[0] + 6} ${L[1] + 10} Q ${CX} ${B[1] + 12} ${R[0] - 6} ${R[1] + 10}`}
          fill="none"
          stroke="#a8f000"
          strokeWidth="2.4"
          strokeLinecap="round"
        />
      </g>

      {/* Left face: illuminated logo, panel seam, fasteners. */}
      <g transform={FACE_LEFT}>
        <rect
          x="0.09"
          y="0.12"
          width="0.82"
          height="0.74"
          rx="0.05"
          fill="none"
          stroke="#2f3739"
          strokeWidth="0.011"
        />
        {[0.15, 0.85].map((fx) =>
          [0.19, 0.81].map((fy) => (
            <circle key={`${fx}-${fy}`} cx={fx} cy={fy} r="0.019" fill="#3d4749" />
          )),
        )}
      </g>
      <g transform={FACE_LEFT} filter={url('glow')}>
        <path d={CHEVRON} transform="translate(0.27 0.24) scale(0.5)" fill="#b5ff00" />
      </g>

      {/* Right face: ventilation bank and status light. */}
      <g transform={FACE_RIGHT}>
        <rect
          x="0.12"
          y="0.14"
          width="0.52"
          height="0.66"
          rx="0.04"
          fill="#0a0e0f"
          stroke="#242b2c"
          strokeWidth="0.01"
        />
        {[0.24, 0.37, 0.5, 0.63].map((vy) => (
          <rect key={vy} x="0.18" y={vy} width="0.4" height="0.04" rx="0.02" fill="#2e3637" />
        ))}
        <circle cx="0.8" cy="0.7" r="0.038" fill="#43dd82" />
      </g>

      {/* Contact bloom where the chassis meets the platform. */}
      <ellipse
        cx={CX}
        cy={BB[1] - 6}
        rx={62}
        ry={16}
        fill="#a8f000"
        fillOpacity="0.5"
        filter={url('soft')}
      />
    </svg>
  );
}
