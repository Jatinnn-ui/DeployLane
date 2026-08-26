import { cn } from '@/lib/utils';

/**
 * DeployLane lockup as text plus a geometry mark.
 *
 * The dashboard uses a raster logo, but the reference header renders the wordmark in two
 * colours — white "Deploy", lime "Lane" — which a flat PNG cannot express. Building it from
 * type also keeps it crisp at any DPR and lets the mark inherit the accent token.
 */
export function Wordmark({
  className,
  textClass = 'text-[16.5px]',
}: {
  className?: string;
  /** Sizes the whole lockup: the mark is sized in `em`, so it tracks the wordmark. */
  textClass?: string;
}) {
  return (
    <span className={cn('inline-flex items-center gap-[0.4em]', textClass, className)}>
      <svg
        viewBox="0 0 24 24"
        aria-hidden="true"
        className="h-[0.95em] w-[0.95em] shrink-0"
      >
        {/* Directional chevron: the brand mark, and the same shape used on the 3D server. */}
        <path
          d="M7.5 4.5 L17 12 L7.5 19.5"
          fill="none"
          stroke="#a8f000"
          strokeWidth="3.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>

      <span className={cn('font-semibold tracking-[-0.02em] leading-none', textClass)}>
        <span className="text-content-primary">Deploy</span>
        <span className="text-accent">Lane</span>
      </span>
    </span>
  );
}
