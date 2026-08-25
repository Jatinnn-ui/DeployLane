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
  lines: string[];
  icon: IconComponent;
  at: Point;
  /** Kept on small screens, where the overlay reduces to the three key stages. */
  essential?: boolean;
}

/**
 * Where the cube sits in the visual, in the same percentage space as the cards.
 * Every connector is drawn to or from this point.
 */
const HUB: Point = { x: 44, y: 48 };

const STAGES: Stage[] = [
  {
    id: 'repository',
    label: 'Repository',
    lines: ['main', 'a1b2c3d'],
    icon: GithubIcon,
    at: { x: 17, y: 15 },
    essential: true,
  },
  { id: 'build', label: 'Build', lines: ['Completed', '30s'], icon: Box, at: { x: 81, y: 13 } },
  {
    id: 'container',
    label: 'Container',
    lines: ['Ready', '84MB · 2.1.9'],
    icon: Layers,
    at: { x: 84, y: 43 },
  },
  {
    id: 'deploy',
    label: 'Deploy',
    lines: ['Success', 'v0-abc-1'],
    icon: Rocket,
    at: { x: 79, y: 74 },
    essential: true,
  },
  {
    id: 'live',
    label: 'Live',
    lines: ['https://chessleam.app'],
    icon: Globe,
    at: { x: 37, y: 88 },
    essential: true,
  },
];

const LOGS_AT: Point = { x: 11, y: 64 };

const LOG_LINES = [
  { text: 'Installing dependencies', done: false },
  { text: 'Building project', done: false },
  { text: 'Optimizing assets', done: false },
  { text: 'Build completed', done: true },
];

/**
 * Connectors run from the cube out to every card, plus one along the top between Repository
 * and Build. The result reads as infrastructure wiring rather than as spokes on a wheel.
 */
const FLOW: Array<{ from: Point; to: Point; bend: number; delay: number }> = [
  { from: HUB, to: STAGES[0].at, bend: -4, delay: 0 },
  { from: STAGES[0].at, to: STAGES[1].at, bend: 0, delay: 0.5 },
  { from: HUB, to: STAGES[1].at, bend: 4, delay: 1 },
  { from: HUB, to: STAGES[2].at, bend: 3, delay: 1.5 },
  { from: HUB, to: STAGES[3].at, bend: -3, delay: 2 },
  { from: HUB, to: STAGES[4].at, bend: -4, delay: 2.5 },
  { from: HUB, to: LOGS_AT, bend: 4, delay: 3 },
];

/** viewBox is 1000×700 and stretched, so strokes are pinned with `non-scaling-stroke`. */
function curve(from: Point, to: Point, bend: number): string {
  const [x1, y1] = [from.x * 10, from.y * 7];
  const [x2, y2] = [to.x * 10, to.y * 7];
  const mid = { x: (x1 + x2) / 2 + bend * 10, y: (y1 + y2) / 2 - bend * 7 };

  return `M ${x1} ${y1} Q ${mid.x} ${mid.y} ${x2} ${y2}`;
}

/**
 * Below lg a card is a grid item in normal flow; from lg it becomes an absolutely placed
 * satellite around the cube. `left`/`top` are supplied inline but ignored until the element
 * is actually positioned, so one style object serves both layouts.
 */
const CARD_BASE =
  'pointer-events-auto min-w-0 rounded-xl border border-border-subtle bg-surface/95 backdrop-blur-sm transition-colors duration-200 hover:border-border-strong lg:absolute lg:-translate-x-1/2 lg:-translate-y-1/2';

function StageCard({ stage }: { stage: Stage }) {
  const Icon = stage.icon;

  return (
    <li
      className={cn(CARD_BASE, 'p-2.5 lg:w-[172px]', !stage.essential && 'hidden lg:block')}
      style={{ left: `${stage.at.x}%`, top: `${stage.at.y}%` }}
    >
      <div className="flex items-start gap-2">
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-border-subtle bg-canvas-secondary text-content-secondary">
          <Icon className="h-3.5 w-3.5" strokeWidth={2} aria-hidden="true" />
        </span>

        <div className="min-w-0 flex-1">
          <p className="truncate text-[12.5px] font-semibold leading-tight text-content-primary">
            {stage.label}
          </p>
          {stage.lines.map((line) => (
            <p key={line} className="truncate font-mono text-[9.5px] leading-[1.5] text-content-muted">
              {line}
            </p>
          ))}
        </div>

        <CheckCircle2 className="h-4 w-4 shrink-0 text-success" aria-hidden="true" />
      </div>
    </li>
  );
}

function BuildLogsCard() {
  return (
    <li
      className={cn(CARD_BASE, 'hidden p-2.5 lg:block lg:w-[168px]')}
      style={{ left: `${LOGS_AT.x}%`, top: `${LOGS_AT.y}%` }}
    >
      <p className="text-[12.5px] font-semibold leading-tight text-content-primary">Build Logs</p>

      <div className="mt-2 space-y-1">
        {LOG_LINES.map((line) => (
          <p
            key={line.text}
            className={cn(
              'truncate font-mono text-[9.5px] leading-[1.4]',
              line.done ? 'text-accent' : 'text-content-muted',
            )}
          >
            <span aria-hidden="true">›</span> {line.text}
          </p>
        ))}
      </div>
    </li>
  );
}

/**
 * DOM/SVG overlay for the hero, layered above the WebGL canvas.
 *
 * Deliberately not rendered inside the 3D scene: small type rasterised by WebGL looks soft,
 * cannot be selected or read by assistive tech, and would cost a draw call per label. As DOM
 * it stays crisp at any DPR, inherits the dashboard's card styling, and the stage names end
 * up in the accessibility tree — which is where the meaning of the visualisation lives.
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
                stroke="rgba(168, 240, 0, 0.28)"
                strokeWidth={1}
                vectorEffect="non-scaling-stroke"
              />
              {/* travelling packet */}
              <path
                d={path}
                fill="none"
                stroke="rgba(183, 255, 25, 0.95)"
                strokeWidth={1.5}
                strokeLinecap="round"
                strokeDasharray="30 970"
                vectorEffect="non-scaling-stroke"
                style={
                  reducedMotion
                    ? { opacity: 0 }
                    : { animation: `dash-flow 7s linear ${segment.delay}s infinite` }
                }
              />
            </g>
          );
        })}

        {/* junction nodes where a connector meets a card */}
        {[...STAGES.map((stage) => stage.at), LOGS_AT, HUB].map((point) => (
          <circle
            key={`${point.x}:${point.y}`}
            cx={point.x * 10}
            cy={point.y * 7}
            r={3.5}
            fill="#a8f000"
            fillOpacity={0.9}
          />
        ))}
      </svg>

      <ul
        aria-label="Deployment pipeline stages"
        className="grid list-none grid-cols-3 gap-2 lg:absolute lg:inset-0 lg:block lg:gap-0"
      >
        {STAGES.map((stage) => (
          <StageCard key={stage.id} stage={stage} />
        ))}
        <BuildLogsCard />
      </ul>
    </div>
  );
}
