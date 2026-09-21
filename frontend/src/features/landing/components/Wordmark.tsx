import { cn } from '@/lib/utils';

/**
 * DeployLane lockup for the landing page — layered chevron arrow + wordmark.
 *
 * The landing page is always dark, so no theme switching is needed here.
 * The mark matches the BrandLogo component used in the app shell, keeping
 * the identity consistent across marketing and product surfaces.
 *
 * Sizing is controlled by `textClass` — the SVG scales proportionally.
 */
export function Wordmark({
  className,
  textClass = 'text-[16.5px]',
}: {
  className?: string;
  /** Controls the overall size. The SVG height is 1.1em relative to this. */
  textClass?: string;
}) {
  return (
    <span className={cn('inline-flex items-center gap-[0.35em]', textClass, className)}>
      {/* Layered chevron arrow mark */}
      <svg
        viewBox="0 0 34 40"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className="h-[1.1em] w-auto shrink-0"
        aria-hidden="true"
      >
        {/* Back arrow (darkest) */}
        <path
          d="M4 12 L14 20 L4 28"
          stroke="#4d7500"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {/* Middle arrow */}
        <path
          d="M10 10 L22 20 L10 30"
          stroke="#7ab800"
          strokeWidth="3.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {/* Front arrow (brightest) */}
        <path
          d="M16 8 L30 20 L16 32"
          stroke="#a8f000"
          strokeWidth="4"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {/* Motion/speed lines */}
        <line x1="1" y1="17" x2="8" y2="17" stroke="rgba(168,240,0,0.5)" strokeWidth="1.5" strokeLinecap="round" />
        <line x1="2" y1="20" x2="11" y2="20" stroke="rgba(168,240,0,0.5)" strokeWidth="1.5" strokeLinecap="round" />
        <line x1="1" y1="23" x2="8" y2="23" stroke="rgba(168,240,0,0.5)" strokeWidth="1.5" strokeLinecap="round" />
        <line x1="3" y1="14" x2="7" y2="14" stroke="rgba(168,240,0,0.35)" strokeWidth="1" strokeLinecap="round" />
        <line x1="3" y1="26" x2="7" y2="26" stroke="rgba(168,240,0,0.35)" strokeWidth="1" strokeLinecap="round" />
      </svg>

      {/* Wordmark text */}
      <span className="font-bold italic tracking-[-0.02em] leading-none">
        <span className="text-content-primary">Deploy</span>
        <span className="text-accent">Lane</span>
      </span>
    </span>
  );
}
