/**
 * Oversized line-art watermarks for the "How DeployLane works" cards.
 *
 * One per step, each matching its own card's purpose: repository -> GitHub mark, build -> wrench,
 * deploy -> rocket, live -> globe. They are decorative only, sit behind the copy at very low
 * opacity, and are deliberately positioned so the card's `overflow: hidden` crops them.
 *
 * All four are authored in the same 200x200 space with the icon geometry scaled into it, so the
 * stroke weight reads identically across cards even though the source glyphs use different native
 * grids. Sizing and colour come from CSS; these only describe geometry.
 */

const SHARED = {
  viewBox: '0 0 200 200',
  fill: 'none',
  stroke: 'currentColor',
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: 'false',
} as const;

/**
 * Places a glyph authored on `grid` units into the shared 200 space at `size` units wide, and
 * pre-divides the stroke so every watermark ends up with the same ~2.4 unit line weight.
 */
function place(grid: number, size: number, x: number, y: number) {
  const scale = size / grid;
  return { transform: `translate(${x} ${y}) scale(${scale})`, strokeWidth: 2.4 / scale };
}

/** Repository: the GitHub silhouette traced as an outline, inside a wider orbit ring. */
export function RepositoryWatermark({ className }: { className?: string }) {
  const cat = place(16, 132, 30, 24);

  return (
    <svg {...SHARED} className={className}>
      <circle cx="112" cy="94" r="70" strokeWidth="2.2" strokeOpacity="0.5" />
      <g {...cat}>
        <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.07-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.42 7.42 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A7.995 7.995 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
      </g>
    </svg>
  );
}

/** Build: an open-ended wrench, angled the way the reference shows it. */
export function BuildWatermark({ className }: { className?: string }) {
  const g = place(24, 168, 16, 16);

  return (
    <svg {...SHARED} className={className}>
      <g {...g}>
        <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
      </g>
    </svg>
  );
}

/** Deploy: a rocket with its exhaust plume. */
export function DeployWatermark({ className }: { className?: string }) {
  const g = place(24, 172, 14, 14);

  return (
    <svg {...SHARED} className={className}>
      <g {...g}>
        <path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91 0z" />
        <path d="M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z" />
        <path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0" />
        <path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5" />
      </g>
    </svg>
  );
}

/** Go live: a wire-frame globe, with extra latitudes so it reads as a network at low opacity. */
export function LiveWatermark({ className }: { className?: string }) {
  const g = place(24, 176, 12, 12);

  return (
    <svg {...SHARED} className={className}>
      <g {...g}>
        <circle cx="12" cy="12" r="10" />
        <path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20" />
        <path d="M2 12h20" />
        <path d="M4.6 6.5h14.8M4.6 17.5h14.8" strokeOpacity="0.75" />
      </g>
    </svg>
  );
}
