/**
 * Oversized line-art watermarks for the "How DeployLane works" cards.
 * Each SVG is 160×160, rendered with `overflow: visible` so they partially
 * bleed off-card and get clipped by the card's `overflow: hidden`.
 * Colour and opacity are controlled purely from CSS (.workflow-watermark).
 */

const BASE = {
  viewBox: '0 0 160 160',
  fill: 'none',
  stroke: 'currentColor',
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
  focusable: false as const,
  overflow: 'visible',
  width: '160',
  height: '160',
};

/** Card 1 — GitHub mark, drawn at a large readable scale. */
export function RepositoryWatermark({ className }: { className?: string }) {
  return (
    <svg {...BASE} className={className}>
      {/*
        GitHub mark path, originally on a 98×96 grid, scaled and centred.
        translate(4 4) scale(1.53) maps it to fill most of the 160×160 box.
      */}
      <g transform="translate(2 2) scale(1.6)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M49 0C21.9 0 0 22 0 49.2c0 21.7 14 40.1 33.5 46.6 2.5.5 3.4-1.1 3.4-2.4 0-1.2-.1-5.1-.1-9.2-12.3 2.3-15.5-3-16.5-5.8-.6-1.4-3-5.8-5-6.9-1.7-.9-4.2-3.2-.1-3.2 3.8 0 6.5 3.5 7.5 5 4.4 7.4 11.4 5.3 14.2 4 .4-3.2 1.7-5.3 3.1-6.5-10.9-1.2-22.3-5.5-22.3-24.3 0-5.4 1.9-9.8 5-13.2-.5-1.2-2.2-6.3.5-13 0 0 4.1-1.3 13.5 5a46.6 46.6 0 0 1 12.3-1.7c4.2 0 8.4.6 12.3 1.7 9.4-6.4 13.5-5 13.5-5 2.7 6.7 1 11.8.5 13 3.1 3.4 5 7.7 5 13.2 0 18.9-11.5 23.1-22.4 24.3 1.8 1.5 3.3 4.5 3.3 9.1 0 6.6-.1 11.9-.1 13.5 0 1.3.9 2.9 3.4 2.4C84 89.2 98 70.8 98 49.2 98 22 76.1 0 49 0Z" />
      </g>
    </svg>
  );
}

/** Card 2 — Wrench. */
export function BuildWatermark({ className }: { className?: string }) {
  return (
    <svg {...BASE} className={className}>
      <g transform="translate(10 10) scale(5.8)" strokeWidth="1.4">
        <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
      </g>
    </svg>
  );
}

/** Card 3 — Rocket. */
export function DeployWatermark({ className }: { className?: string }) {
  return (
    <svg {...BASE} className={className}>
      <g transform="translate(8 8) scale(6)" strokeWidth="1.4">
        <path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91 0z" />
        <path d="M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z" />
        <path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0" />
        <path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5" />
      </g>
    </svg>
  );
}

/** Card 4 — Globe. */
export function LiveWatermark({ className }: { className?: string }) {
  return (
    <svg {...BASE} className={className}>
      <g transform="translate(8 8) scale(6)" strokeWidth="1.4">
        <circle cx="12" cy="12" r="10" />
        <path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20" />
        <path d="M2 12h20" />
        <path d="M4.6 6.5h14.8M4.6 17.5h14.8" />
      </g>
    </svg>
  );
}
