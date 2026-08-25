import { Box, CheckCircle2, Globe, Layers, Rocket } from 'lucide-react';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { cn } from '@/lib/utils';
import type { IconComponent } from './icon-type';

interface Point {
  x: number;
  y: number;
}

interface Stage {
  id: string;
  label: string;
  detail: string;
  meta: string;
  icon: IconComponent;
  at: Point;
  tilt: string;
  delay: number;
  /** Kept on small screens, where the overlay reduces to the three key stages. */
  essential?: boolean;
}

/**
 * Coordinates hug the central server rather than claiming the whole right column. The
 * projected server occupies roughly x 30–72 / y 25–70, so every card sits just outside that
 * footprint while remaining part of one compact product composition.
 */
const STAGES: Stage[] = [
  {
    id: 'repository',
    label: 'Repository',
    detail: 'main',
    meta: 'a1b2c3d',
    icon: GithubIcon,
    at: { x: 25, y: 15 },
    tilt: 'perspective(700px) rotateX(2deg) rotateY(4deg)',
    delay: 1.25,
    essential: true,
  },
  {
    id: 'build',
    label: 'Build',
    detail: 'Completed',
    meta: '30s',
    icon: Box,
    at: { x: 78, y: 16 },
    tilt: 'perspective(700px) rotateX(2deg) rotateY(-5deg)',
    delay: 1.48,
  },
  {
    id: 'container',
    label: 'Container',
    detail: 'Ready',
    meta: 'Node 22 · 84 MB',
    icon: Layers,
    at: { x: 84, y: 43 },
    tilt: 'perspective(700px) rotateX(1deg) rotateY(-6deg)',
    delay: 1.71,
  },
  {
    id: 'deploy',
    label: 'Deploy',
    detail: 'Success',
    meta: 'ap-mumbai-1',
    icon: Rocket,
    at: { x: 78, y: 71 },
    tilt: 'perspective(700px) rotateX(-2deg) rotateY(-5deg)',
    delay: 1.94,
    essential: true,
  },
  {
    id: 'live',
    label: 'Live',
    detail: 'deploylane.online',
    meta: 'HTTPS · serving',
    icon: Globe,
    at: { x: 49, y: 88 },
    tilt: 'perspective(700px) rotateX(-3deg) rotateY(1deg)',
    delay: 2.17,
    essential: true,
  },
];

const LOGS_AT: Point = { x: 17, y: 56 };

const LOG_LINES = [
  { text: 'Installing dependencies', done: false },
  { text: 'Building project', done: false },
  { text: 'Optimizing assets', done: false },
  { text: 'Build completed', done: true },
];

/**
 * One sequential pipeline around the server — never a hub or spider web. Each segment starts
 * and ends at the edge of its adjacent card, curving around (not through) the central model.
 */
const FLOW: Array<{ from: Point; to: Point; control: Point; delay: number }> = [
  {
    from: { x: 34, y: 15 },
    to: { x: 69, y: 16 },
    control: { x: 51, y: 10 },
    delay: 2.4,
  },
  {
    from: { x: 79, y: 22 },
    to: { x: 84, y: 36 },
    control: { x: 88, y: 27 },
    delay: 2.85,
  },
  {
    from: { x: 84, y: 50 },
    to: { x: 79, y: 64 },
    control: { x: 88, y: 58 },
    delay: 3.3,
  },
  {
    from: { x: 71, y: 74 },
    to: { x: 57, y: 84 },
    control: { x: 66, y: 83 },
    delay: 3.75,
  },
];

/** viewBox is 1000×700 and stretched, so strokes use `non-scaling-stroke`. */
function curve(from: Point, to: Point, control: Point): string {
  return `M ${from.x * 10} ${from.y * 7} Q ${control.x * 10} ${control.y * 7} ${to.x * 10} ${to.y * 7}`;
}

const CARD_SHELL =
  'rounded-2xl border border-[rgba(255,255,255,0.12)] bg-[#171b1c]/95 shadow-[0_14px_34px_-22px_rgba(0,0,0,0.95)] backdrop-blur-sm transition-colors duration-200 hover:border-[rgba(168,240,0,0.28)]';

function StageCard({ stage, reducedMotion }: { stage: Stage; reducedMotion: boolean }) {
  const Icon = stage.icon;

  return (
    <li
      className={cn(
        'pipeline-stage pointer-events-auto min-w-0 lg:absolute lg:w-[164px] lg:-translate-x-1/2 lg:-translate-y-1/2',
        !stage.essential && 'hidden lg:block',
      )}
      style={{
        left: `${stage.at.x}%`,
        top: `${stage.at.y}%`,
        animationDelay: reducedMotion ? '0ms' : `${stage.delay}s`,
      }}
    >
      <div
        className={cn(CARD_SHELL, 'pipeline-card-float p-3')}
        style={
          {
            '--card-tilt': stage.tilt,
            '--float-delay': `${stage.at.x / -19}s`,
          } as React.CSSProperties
        }
      >
        <div className="flex items-start gap-2.5">
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-border-subtle bg-canvas-secondary text-content-secondary">
            <Icon className="h-3.5 w-3.5" strokeWidth={2} aria-hidden="true" />
          </span>

          <div className="min-w-0 flex-1">
            <p className="truncate text-[12.5px] font-semibold leading-tight text-content-primary">
              {stage.label}
            </p>
            <p className="mt-1 truncate text-[10px] font-medium leading-tight text-content-secondary">
              {stage.detail}
            </p>
            <p className="mt-0.5 truncate font-mono text-[9px] leading-tight text-content-muted">
              {stage.meta}
            </p>
          </div>

          <CheckCircle2 className="h-4 w-4 shrink-0 text-success" aria-hidden="true" />
        </div>
      </div>
    </li>
  );
}

function BuildLogsCard({ reducedMotion }: { reducedMotion: boolean }) {
  return (
    <li
      className="pipeline-stage pointer-events-auto absolute hidden w-[188px] -translate-x-1/2 -translate-y-1/2 xl:block"
      style={{
        left: `${LOGS_AT.x}%`,
        top: `${LOGS_AT.y}%`,
        animationDelay: reducedMotion ? '0ms' : '1.36s',
      }}
    >
      <div
        className={cn(CARD_SHELL, 'pipeline-card-float p-3')}
        style={
          {
            '--card-tilt': 'perspective(700px) rotateX(-1deg) rotateY(5deg)',
            '--float-delay': '-1.7s',
          } as React.CSSProperties
        }
      >
        <div className="flex items-center gap-2">
          <span className="h-1.5 w-1.5 rounded-full bg-accent" aria-hidden="true" />
          <p className="text-[12px] font-semibold leading-tight text-content-primary">Build Logs</p>
          <span className="ml-auto font-mono text-[8px] uppercase tracking-[0.08em] text-content-muted">
            live
          </span>
        </div>

        <div className="mt-2.5 space-y-1 font-mono">
          {LOG_LINES.map((line) => (
            <p
              key={line.text}
              className={cn(
                'truncate text-[9px] leading-[1.35]',
                line.done ? 'text-accent' : 'text-content-muted',
              )}
            >
              <span aria-hidden="true">›</span> {line.text}
            </p>
          ))}
        </div>
      </div>
    </li>
  );
}

/**
 * Crisp DOM cards and SVG infrastructure over the WebGL product render. The canvas owns the
 * physical server; this layer carries the readable deployment state without competing for
 * draw calls or becoming blurry at small sizes.
 */
export function PipelineOverlay({ reducedMotion = false }: { reducedMotion?: boolean }) {
  return (
    <div className="pointer-events-none absolute inset-0 flex flex-col justify-end lg:block">
      <svg
        className="pipeline-connections absolute inset-0 hidden h-full w-full lg:block"
        viewBox="0 0 1000 700"
        preserveAspectRatio="none"
        aria-hidden="true"
        style={{ animationDelay: reducedMotion ? '0ms' : '2.3s' }}
      >
        {FLOW.map((segment, index) => {
          const path = curve(segment.from, segment.to, segment.control);

          return (
            <g key={index}>
              <path
                d={path}
                fill="none"
                stroke="rgba(168,240,0,0.30)"
                strokeWidth={1.15}
                vectorEffect="non-scaling-stroke"
              />
              {/* Short dash acts as the moving data packet; one per sequential stage. */}
              <path
                d={path}
                fill="none"
                stroke="#a8f000"
                strokeWidth={1.8}
                strokeLinecap="round"
                strokeDasharray="18 982"
                pathLength={1000}
                vectorEffect="non-scaling-stroke"
                style={
                  reducedMotion
                    ? { opacity: 0 }
                    : { animation: `dash-flow 5.6s linear ${segment.delay}s infinite` }
                }
              />
            </g>
          );
        })}

        {/* Only actual route endpoints receive nodes — no random central junction. */}
        {FLOW.flatMap((segment) => [segment.from, segment.to]).map((point, index) => (
          <circle
            key={`${point.x}:${point.y}:${index}`}
            cx={point.x * 10}
            cy={point.y * 7}
            r={2.7}
            fill="#a8f000"
            fillOpacity={0.78}
          />
        ))}
      </svg>

      <ul
        aria-label="Deployment pipeline stages"
        className="grid list-none grid-cols-3 gap-2 lg:absolute lg:inset-0 lg:block lg:gap-0"
      >
        {STAGES.map((stage) => (
          <StageCard key={stage.id} stage={stage} reducedMotion={reducedMotion} />
        ))}
        <BuildLogsCard reducedMotion={reducedMotion} />
      </ul>
    </div>
  );
}
