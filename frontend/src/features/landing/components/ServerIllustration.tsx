import { useId } from 'react';
import { cn } from '@/lib/utils';

/**
 * The DeployLane deployment server, drawn as an inline SVG isometric model.
 *
 * Deliberately not WebGL. A canvas-based server depends on a GL context that can fail, gets
 * deferred behind a lazy chunk, and cannot be positioned against a CSS bounding box — all of
 * which made the previous version read as missing. An SVG renders on first paint, every time,
 * and its geometry is measurable against the reference.
 *
 * Construction: one unit square is mapped onto each visible face with a transform matrix, so
 * face decoration (logo, vents, seams) is authored in flat 0–1 coordinates and inherits the
 * correct isometric shear automatically.
 */

/* Cube corners. Top rhombus is 220 wide × 116 tall; body height is 78. */
const TOP = '270,54';
const LEFT = '160,112';
const FRONT = '270,170';
const RIGHT = '380,112';
const LEFT_B = '160,190';
const FRONT_B = '270,248';
const RIGHT_B = '380,190';

/** Maps the unit square onto each face: (0,0) is the face's upper-left corner. */
const FACE_LEFT = 'matrix(110 58 0 78 160 112)';
const FACE_RIGHT = 'matrix(110 -58 0 78 270 170)';
const FACE_TOP = 'matrix(110 58 110 -58 160 112)';

/** Thick chevron in unit space — the brand mark. */
const CHEVRON = 'M 0.30 0.20 L 0.60 0.50 L 0.30 0.80 L 0.43 0.92 L 0.85 0.50 L 0.43 0.08 Z';

const PLATFORM_CX = 270;
const PLATFORM_CY = 196;
/** Isometric foreshortening for a circle on the ground plane. */
const RY_RATIO = 0.527;

function SmallCube({ x, y, scale }: { x: number; y: number; scale: number }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${scale})`}>
      <polygon points="0,-14 24,0 0,14 -24,0" fill="#1e2426" />
      <polygon points="-24,0 0,14 0,34 -24,20" fill="#0d1112" />
      <polygon points="0,14 24,0 24,20 0,34" fill="#080b0c" />
      <path d="M-24,20 L0,34 L24,20" fill="none" stroke="#a8f000" strokeOpacity="0.5" strokeWidth="1.4" />
    </g>
  );
}

export function ServerIllustration({ className }: { className?: string }) {
  // The illustration appears twice on the page. Paint-server ids must therefore be scoped
  // per instance, otherwise both copies reference the first one's defs via duplicate ids.
  const uid = useId().replace(/:/g, '');
  const ref = (name: string) => `${name}-${uid}`;
  const url = (name: string) => `url(#${ref(name)})`;

  return (
    <svg
      viewBox="50 30 440 290"
      className={cn('h-auto w-full', className)}
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={ref('dl-face-top')} x1="0" y1="0" x2="0.7" y2="1">
          <stop offset="0" stopColor="#333b3d" />
          <stop offset="0.55" stopColor="#202728" />
          <stop offset="1" stopColor="#161b1c" />
        </linearGradient>
        <linearGradient id={ref('dl-face-left')} x1="0" y1="0" x2="0.35" y2="1">
          <stop offset="0" stopColor="#1b2122" />
          <stop offset="1" stopColor="#0d1112" />
        </linearGradient>
        <linearGradient id={ref('dl-face-right')} x1="0" y1="0" x2="0.6" y2="1">
          <stop offset="0" stopColor="#121718" />
          <stop offset="1" stopColor="#07090a" />
        </linearGradient>
        <radialGradient id={ref('dl-underglow')}>
          <stop offset="0" stopColor="#a8f000" stopOpacity="0.34" />
          <stop offset="0.45" stopColor="#a8f000" stopOpacity="0.1" />
          <stop offset="1" stopColor="#a8f000" stopOpacity="0" />
        </radialGradient>
        <radialGradient id={ref('dl-ambient')}>
          <stop offset="0" stopColor="#a8f000" stopOpacity="0.08" />
          <stop offset="0.55" stopColor="#a8f000" stopOpacity="0.02" />
          <stop offset="1" stopColor="#a8f000" stopOpacity="0" />
        </radialGradient>
        <filter id={ref('dl-glow')} x="-70%" y="-70%" width="240%" height="240%">
          <feGaussianBlur stdDeviation="3.4" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <filter id={ref('dl-glow-soft')} x="-70%" y="-70%" width="240%" height="240%">
          <feGaussianBlur stdDeviation="6" />
        </filter>
      </defs>

      {/* Contained ambient light. Kept small so the field stays black, not green. */}
      <ellipse cx={PLATFORM_CX} cy={190} rx={215} ry={125} fill={url('dl-ambient')} />

      {/* Deployment platform: dark base plus concentric lime rings on the ground plane. */}
      <ellipse cx={PLATFORM_CX} cy={PLATFORM_CY + 6} rx={205} ry={205 * RY_RATIO} fill={url('dl-underglow')} />
      {[
        { rx: 192, o: 0.16 },
        { rx: 162, o: 0.3 },
        { rx: 132, o: 0.5 },
      ].map((ring) => (
        <ellipse
          key={ring.rx}
          cx={PLATFORM_CX}
          cy={PLATFORM_CY}
          rx={ring.rx}
          ry={ring.rx * RY_RATIO}
          fill="none"
          stroke="#a8f000"
          strokeOpacity={ring.o}
          strokeWidth="1.3"
        />
      ))}
      <ellipse
        cx={PLATFORM_CX}
        cy={PLATFORM_CY}
        rx={116}
        ry={116 * RY_RATIO}
        fill="#070a0b"
        stroke="#a8f000"
        strokeOpacity="0.62"
        strokeWidth="1.5"
      />

      <SmallCube x={112} y={128} scale={0.72} />
      <SmallCube x={432} y={150} scale={0.56} />

      {/* Body: three visible faces, each its own gradient so the form reads as solid. */}
      <polygon points={`${LEFT} ${FRONT} ${RIGHT} ${TOP}`} fill={url('dl-face-top')} />
      <polygon points={`${LEFT} ${FRONT} ${FRONT_B} ${LEFT_B}`} fill={url('dl-face-left')} />
      <polygon points={`${FRONT} ${RIGHT} ${RIGHT_B} ${FRONT_B}`} fill={url('dl-face-right')} />

      {/* Thin machined edges. */}
      <polygon
        points={`${LEFT} ${FRONT} ${RIGHT} ${TOP}`}
        fill="none"
        stroke="#4c5658"
        strokeWidth="1.1"
      />
      <polyline points={`${LEFT_B} ${FRONT_B} ${RIGHT_B}`} fill="none" stroke="#3a4344" strokeWidth="1.1" />
      <line x1="160" y1="112" x2="160" y2="190" stroke="#3a4344" strokeWidth="1.1" />
      <line x1="380" y1="112" x2="380" y2="190" stroke="#2d3536" strokeWidth="1.1" />

      {/* Recessed top lid with its own chevron. */}
      <g transform={FACE_TOP}>
        <rect x="0.1" y="0.1" width="0.8" height="0.8" fill="#141a1b" stroke="#394243" strokeWidth="0.008" />
        <rect x="0.17" y="0.17" width="0.66" height="0.66" fill="#0d1213" />
      </g>
      <g transform={FACE_TOP} filter={url('dl-glow')}>
        <path d={CHEVRON} transform="translate(0.28 0.28) scale(0.44)" fill="#b5ff00" />
      </g>

      {/* Illuminated seam directly under the top edge — the reference's signature detail. */}
      <g filter={url('dl-glow')}>
        <polyline
          points="162,120 270,177 378,120"
          fill="none"
          stroke="#a8f000"
          strokeWidth="2.6"
          strokeLinecap="round"
        />
      </g>
      <polyline
        points="162,182 270,239 378,182"
        fill="none"
        stroke="#a8f000"
        strokeOpacity="0.55"
        strokeWidth="1.6"
        strokeLinecap="round"
      />

      {/* Left face: brand mark, panel seam and fasteners. */}
      <g transform={FACE_LEFT}>
        <rect x="0.08" y="0.14" width="0.84" height="0.72" fill="none" stroke="#2b3334" strokeWidth="0.012" />
        {[0.14, 0.86].map((fx) =>
          [0.2, 0.8].map((fy) => (
            <circle key={`${fx}-${fy}`} cx={fx} cy={fy} r="0.022" fill="#394243" />
          )),
        )}
      </g>
      <g transform={FACE_LEFT} filter={url('dl-glow')}>
        <path d={CHEVRON} transform="translate(0.26 0.26) scale(0.5)" fill="#b5ff00" />
      </g>

      {/* Right face: ventilation bank and status indicator. */}
      <g transform={FACE_RIGHT}>
        <rect x="0.1" y="0.16" width="0.5" height="0.62" fill="#0a0d0e" stroke="#232a2b" strokeWidth="0.01" />
        {[0.26, 0.38, 0.5, 0.62].map((vy) => (
          <rect key={vy} x="0.16" y={vy} width="0.38" height="0.045" rx="0.02" fill="#2a3132" />
        ))}
        <circle cx="0.78" cy="0.72" r="0.045" fill="#43dd82" />
      </g>

      {/* Base bloom where the chassis meets the platform. */}
      <ellipse
        cx={PLATFORM_CX}
        cy={244}
        rx={78}
        ry={20}
        fill="#a8f000"
        fillOpacity="0.5"
        filter={url('dl-glow-soft')}
      />
    </svg>
  );
}
