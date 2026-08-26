import { useId } from 'react';
import { cn } from '@/lib/utils';

/**
 * The DeployLane deployment appliance: a rounded hardware unit on a lit circular platform.
 *
 * Projection matters here. An isometric box reads as a flat open-top cube, which is not what
 * the reference shows. This uses a shallow three-quarter perspective instead: a dominant front
 * face, a narrow right face carrying the vents, and a compressed top lid. The depth vector is
 * applied once and every face derives from it, so the solid stays geometrically consistent.
 */

type Point = [number, number];

/** Replaces each vertex with a quadratic through pulled-back edge points. */
function roundedPath(points: Point[], radius: number): string {
  const count = points.length;
  const parts: string[] = [];

  for (let i = 0; i < count; i += 1) {
    const current = points[i];
    const previous = points[(i - 1 + count) % count];
    const next = points[(i + 1) % count];
    const back = pullBack(current, previous, radius);
    const forward = pullBack(current, next, radius);

    parts.push(
      i === 0 ? `M ${round(back)}` : `L ${round(back)}`,
      `Q ${round(current)} ${round(forward)}`,
    );
  }

  return `${parts.join(' ')} Z`;
}

function pullBack(from: Point, toward: Point, radius: number): Point {
  const dx = toward[0] - from[0];
  const dy = toward[1] - from[1];
  const length = Math.hypot(dx, dy) || 1;
  // Clamped so a short edge cannot invert the curve.
  const distance = Math.min(radius, length / 2);
  return [from[0] + (dx / length) * distance, from[1] + (dy / length) * distance];
}

function round([x, y]: Point): string {
  return `${Math.round(x * 100) / 100} ${Math.round(y * 100) / 100}`;
}

/* Front face, then a single depth vector back-and-up for the top and right faces. */
const FTL: Point = [70, 108];
const FTR: Point = [232, 100];
const FBR: Point = [232, 218];
const FBL: Point = [70, 226];
const DEPTH: Point = [58, -26];

const offset = (p: Point): Point => [p[0] + DEPTH[0], p[1] + DEPTH[1]];
const BTL = offset(FTL);
const BTR = offset(FTR);
const BBR = offset(FBR);

const FRONT = [FTL, FTR, FBR, FBL];
const TOP = [FTL, FTR, BTR, BTL];
const RIGHT = [FTR, BTR, BBR, FBR];

/** Unit-square mapping per face, so decoration inherits the correct perspective. */
const M_FRONT = `matrix(162 -8 0 118 ${FTL[0]} ${FTL[1]})`;
const M_TOP = `matrix(162 -8 58 -26 ${FTL[0]} ${FTL[1]})`;
const M_RIGHT = `matrix(58 -26 0 118 ${FTR[0]} ${FTR[1]})`;

const CHEVRON = 'M 0.30 0.16 L 0.62 0.50 L 0.30 0.84 L 0.44 0.97 L 0.88 0.50 L 0.44 0.03 Z';

/* Platform, deliberately tight around the appliance rather than a wide orbital field. */
const BASE: Point = [180, 212];
const BASE_RX = 148;
const BASE_RY = 46;

export function ServerIllustration({ className }: { className?: string }) {
  // This component renders twice on the page, so paint-server ids are scoped per instance.
  const uid = useId().replace(/:/g, '');
  const ref = (name: string) => `${name}-${uid}`;
  const url = (name: string) => `url(#${ref(name)})`;

  return (
    <svg
      viewBox="0 40 420 300"
      className={cn('h-auto w-full', className)}
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={ref('lid')} x1="0.1" y1="0" x2="0.75" y2="1">
          <stop offset="0" stopColor="#41494c" />
          <stop offset="0.45" stopColor="#2b3234" />
          <stop offset="1" stopColor="#1b2122" />
        </linearGradient>
        <linearGradient id={ref('front')} x1="0" y1="0" x2="0.25" y2="1">
          <stop offset="0" stopColor="#23292b" />
          <stop offset="0.5" stopColor="#161b1d" />
          <stop offset="1" stopColor="#0d1112" />
        </linearGradient>
        <linearGradient id={ref('side')} x1="0" y1="0" x2="1" y2="0.6">
          <stop offset="0" stopColor="#12181a" />
          <stop offset="1" stopColor="#080b0c" />
        </linearGradient>
        <linearGradient id={ref('wall')} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#080b0c" />
          <stop offset="0.5" stopColor="#161b1c" />
          <stop offset="1" stopColor="#070a0b" />
        </linearGradient>
        <radialGradient id={ref('ground')}>
          <stop offset="0" stopColor="#a0ff00" stopOpacity="0.25" />
          <stop offset="0.35" stopColor="#6edc00" stopOpacity="0.09" />
          <stop offset="0.7" stopColor="#6edc00" stopOpacity="0" />
        </radialGradient>
        <filter id={ref('neon')} x="-120%" y="-120%" width="340%" height="340%">
          <feGaussianBlur stdDeviation="2.2" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <filter id={ref('bloom')} x="-140%" y="-140%" width="380%" height="380%">
          <feGaussianBlur stdDeviation="7" />
        </filter>
      </defs>

      {/* Ground bloom, brightest immediately beneath the unit. */}
      <ellipse cx={BASE[0]} cy={BASE[1] + 26} rx={196} ry={72} fill={url('ground')} />

      {/* Platform: cylinder wall, dark plate, neon outer rim, one inner ring. */}
      <path
        d={`M ${BASE[0] - BASE_RX} ${BASE[1]} A ${BASE_RX} ${BASE_RY} 0 0 0 ${BASE[0] + BASE_RX} ${BASE[1]} L ${BASE[0] + BASE_RX} ${BASE[1] + 19} A ${BASE_RX} ${BASE_RY} 0 0 1 ${BASE[0] - BASE_RX} ${BASE[1] + 19} Z`}
        fill={url('wall')}
      />
      <ellipse cx={BASE[0]} cy={BASE[1]} rx={BASE_RX} ry={BASE_RY} fill="#090d0e" />
      <g filter={url('neon')}>
        <ellipse
          cx={BASE[0]}
          cy={BASE[1]}
          rx={BASE_RX}
          ry={BASE_RY}
          fill="none"
          stroke="#a6ff00"
          strokeOpacity="0.9"
          strokeWidth="1.6"
        />
      </g>
      <ellipse
        cx={BASE[0]}
        cy={BASE[1]}
        rx={BASE_RX - 30}
        ry={BASE_RY - 9}
        fill="none"
        stroke="#a6ff00"
        strokeOpacity="0.3"
        strokeWidth="1"
      />

      {/* Contact shadow so the unit reads as resting on the plate, not floating. */}
      <ellipse cx={BASE[0]} cy={BASE[1] + 2} rx={92} ry={26} fill="#000" fillOpacity="0.55" />

      {/* Solid body. Right and front first, lid last so its rounding sits on top. */}
      <path d={roundedPath(RIGHT, 12)} fill={url('side')} />
      <path d={roundedPath(FRONT, 13)} fill={url('front')} />
      <path
        d={roundedPath(FRONT, 13)}
        fill="none"
        stroke="#3c4547"
        strokeOpacity="0.8"
        strokeWidth="1"
      />
      <path d={roundedPath(TOP, 12)} fill={url('lid')} />
      <path
        d={roundedPath(TOP, 12)}
        fill="none"
        stroke="#5b6568"
        strokeOpacity="0.85"
        strokeWidth="1"
      />

      {/* Inset lid panel with the small brand accent. */}
      <g transform={M_TOP}>
        <rect
          x="0.12"
          y="0.14"
          width="0.76"
          height="0.72"
          rx="0.07"
          fill="#1a2021"
          stroke="#464f52"
          strokeOpacity="0.75"
          strokeWidth="0.008"
        />
        {[0.2, 0.8].map((sx) =>
          [0.26, 0.74].map((sy) => (
            <circle key={`${sx}-${sy}`} cx={sx} cy={sy} r="0.015" fill="#4d5658" />
          )),
        )}
      </g>
      <g transform={M_TOP} filter={url('neon')}>
        <path d={CHEVRON} transform="translate(0.39 0.34) scale(0.26)" fill="#b2ff20" />
      </g>

      {/* The defining detail: a thin neon strip along the upper seam. */}
      <g filter={url('neon')}>
        <path
          d={`M ${FTL[0] + 8} ${FTL[1] + 4} L ${FTR[0] - 8} ${FTR[1] + 4} L ${BTR[0] - 6} ${BTR[1] + 5}`}
          fill="none"
          stroke="#9dff00"
          strokeWidth="1.9"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </g>

      {/* Front face: seams, indicator lights, glowing mark. */}
      <g transform={M_FRONT}>
        <rect
          x="0.07"
          y="0.12"
          width="0.86"
          height="0.76"
          rx="0.05"
          fill="none"
          stroke="#333b3d"
          strokeOpacity="0.85"
          strokeWidth="0.009"
        />
        <line x1="0.07" y1="0.25" x2="0.93" y2="0.25" stroke="#2a3133" strokeWidth="0.007" />
        <circle cx="0.13" cy="0.55" r="0.022" fill="#9dff00" fillOpacity="0.75" />
        {[0.68, 0.75, 0.82].map((dx) => (
          <circle key={dx} cx={dx} cy="0.85" r="0.012" fill="#39413f" />
        ))}
      </g>
      <g transform={M_FRONT} filter={url('neon')}>
        <path d={CHEVRON} transform="translate(0.33 0.32) scale(0.4)" fill="#b2ff20" />
      </g>

      {/* Right face: ventilation bank and a small status indicator. */}
      <g transform={M_RIGHT}>
        <rect
          x="0.14"
          y="0.16"
          width="0.7"
          height="0.56"
          rx="0.06"
          fill="#0a0e0f"
          stroke="#232a2b"
          strokeOpacity="0.9"
          strokeWidth="0.02"
        />
        {[0.24, 0.34, 0.44, 0.54, 0.64].map((vy) => (
          <rect key={vy} x="0.22" y={vy} width="0.54" height="0.028" rx="0.014" fill="#333b3c" />
        ))}
        <circle cx="0.5" cy="0.84" r="0.03" fill="#43dd82" />
      </g>

      {/* Tight bloom where the chassis meets the plate. */}
      <ellipse
        cx={BASE[0] - 8}
        cy={FBL[1] - 4}
        rx={70}
        ry={15}
        fill="#a6ff00"
        fillOpacity="0.45"
        filter={url('bloom')}
      />
    </svg>
  );
}
