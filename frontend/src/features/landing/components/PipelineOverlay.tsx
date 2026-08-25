import {
  Box,
  CheckCircle2,
  GitBranch,
  Hammer,
  Terminal,
  UploadCloud,
  type LucideIcon,
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface Point {
  x: number;
  y: number;
}

interface Stage {
  id: string;
  label: string;
  meta: string;
  icon: LucideIcon;
  at: Point;
  /** Kept on small screens, where the overlay is reduced to the three key stages. */
  essential?: boolean;
  tone?: 'accent' | 'success';
}

/**
 * Where the server sits in the visual, in the same percentage space as the cards.
 * Every connector is drawn to or from this point.
 */
const HUB: Point = { x: 50, y: 47 };

const STAGES: Stage[] = [
  {
    id: 'repository',
    label: 'Repository',
    meta: 'main · 2f8c1ae',
    icon: GitBranch,
    at: { x: 16, y: 9 },
    essential: true,
  },
  { id: 'build', label: 'Build', meta: 'Nixpacks · 41s', icon: Hammer, at: { x: 9, y: 40 } },
  { id: 'container', label: 'Container', meta: 'Image · 84 MB', icon: Box, at: { x: 17, y: 72 } },
  {
    id: 'deploy',
    label: 'Deploy',
    meta: 'Rolling · 0 downtime',
    icon: UploadCloud,
    at: { x: 85, y: 18 },
    essential: true,
  },
  {
    id: 'live',
    label: 'Live',
    meta: 'app.deploylane.online',
    icon: CheckCircle2,
    at: { x: 90, y: 51 },
    essential: true,
    tone: 'success',
  },
  { id: 'logs', label: 'Build logs', meta: 'Streaming', icon: Terminal, at: { x: 79, y: 84 } },
];

/**
 * The pipeline runs down the left into the server, then back out to the right — so the
 * connectors read as one continuous path rather than six spokes on a wheel.
 */
const FLOW: Array<{ from: Point; to: Point; bend: number; delay: number }> = [
  { from: STAGES[0].at, to: STAGES[1].at, bend: -7, delay: 0 },
  { from: STAGES[1].at, to: STAGES[2].at, bend: -7, delay: 0.6 },
  { from: STAGES[2].at, to: HUB, bend: 5, delay: 1.2 },
  { from: HUB, to: STAGES[3].at, bend: -6, delay: 1.8 },
  { from: STAGES[3].at, to: STAGES[4].at, bend: 7, delay: 2.4 },
  { from: HUB, to: STAGES[5].at, bend: 6, delay: 1.5 },
];

/** viewBox is 1000×700 and stretched, so strokes are pinned with `non-scaling-stroke`. */
function curve(from: Point, to: Point, bend: number): string {
  const [x1, y1] = [from.x * 10, from.y * 7];
  const [x2, y2] = [to.x * 10, to.y * 7];
  const mid = { x: (x1 + x2) / 2 + bend * 10, y: (y1 + y2) / 2 };

  return `M ${x1} ${y1} Q ${mid.x} ${mid.y} ${x2} ${y2}`;
}

function StageCard({ stage, reducedMotion }: { stage: Stage; reducedMotion: boolean }) {
  const Icon = stage.icon;
  const isSuccess = stage.tone === 'success';

  return (
    <li
      className={cn(
        // Below lg the card is a grid item in normal flow; from lg it becomes an absolutely
        // placed satellite around the server. `left`/`top` are supplied inline but ignored
        // until the element is actually positioned, so one style object serves both layouts.
        'pointer-events-auto min-w-0 rounded-2xl border border-border-subtle bg-surface/95 p-2.5 backdrop-blur-sm transition-colors duration-200 hover:border-border-strong sm:p-3 lg:absolute lg:w-[168px] lg:-translate-x-1/2 lg:-translate-y-1/2',
        !stage.essential && 'hidden lg:block',
      )}
      style={{ left: `${stage.at.x}%`, top: `${stage.at.y}%` }}
    >
      <div className="flex items-center gap-1.5 sm:gap-2">
        <span
          className={cn(
            'flex h-6 w-6 shrink-0 items-center justify-center rounded-md border',
            isSuccess
              ? 'border-success-border bg-success-soft text-success'
              : 'border-accent-border bg-accent-soft text-accent',
          )}
        >
          <Icon className="h-3.5 w-3.5" aria-hidden="true" strokeWidth={2} />
        </span>

        <span className="truncate text-[12px] font-semibold leading-none text-content-primary sm:text-[13px]">
          {stage.label}
        </span>

        <span
          className={cn(
            'ml-auto h-1.5 w-1.5 shrink-0 rounded-full',
            isSuccess ? 'bg-success' : 'bg-accent',
          )}
          style={
            reducedMotion
              ? undefined
              : { animation: `status-pulse 2.4s ease-in-out ${stage.at.y / 40}s infinite` }
          }
        />
      </div>

      <p className="mt-2 truncate font-mono text-[10px] leading-none text-content-muted sm:text-[10.5px]">
        {stage.meta}
      </p>
    </li>
  );
}

/**
 * DOM/SVG overlay for the hero, layered above the WebGL canvas.
 *
 * Deliberately not rendered inside the 3D scene: small type rasterised by WebGL looks
 * soft, cannot be selected or read by assistive tech, and would cost a draw call per
 * label. As DOM it stays crisp at any DPR, inherits the dashboard's card styling, and the
 * stage names end up in the accessibility tree — which is where the meaning of the
 * visualisation actually lives.
 */
export function PipelineOverlay({ reducedMotion = false }: { reducedMotion?: boolean }) {
  return (
    <div className="pointer-events-none absolute inset-0 flex flex-col justify-end lg:block">
      {/* Connectors are desktop-only: on mobile half the stages are hidden, and paths
          running to absent nodes would read as broken rather than simplified. */}
      <svg
        className="absolute inset-0 hidden h-full w-full lg:block"
        viewBox="0 0 1000 700"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        {FLOW.map((segment, index) => {
          const path = curve(segment.from, segment.to, segment.bend);

          return (
            <g key={index}>
              {/* static rail */}
              <path
                d={path}
                fill="none"
                stroke="rgba(168, 240, 0, 0.16)"
                strokeWidth={1}
                vectorEffect="non-scaling-stroke"
              />
              {/* travelling packet */}
              <path
                d={path}
                fill="none"
                stroke="rgba(168, 240, 0, 0.85)"
                strokeWidth={1.5}
                strokeLinecap="round"
                strokeDasharray="26 974"
                vectorEffect="non-scaling-stroke"
                style={
                  reducedMotion
                    ? { opacity: 0 }
                    : {
                        animation: `dash-flow 7s linear ${segment.delay}s infinite`,
                      }
                }
              />
            </g>
          );
        })}
      </svg>

      <ul
        aria-label="Deployment pipeline stages"
        className="grid list-none grid-cols-3 gap-2 lg:absolute lg:inset-0 lg:block lg:gap-0"
      >
        {STAGES.map((stage) => (
          <StageCard key={stage.id} stage={stage} reducedMotion={reducedMotion} />
        ))}
      </ul>
    </div>
  );
}
