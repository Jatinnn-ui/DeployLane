import { Box, CheckCircle2, Globe, Layers, Rocket } from 'lucide-react';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { cn } from '@/lib/utils';
import type { IconComponent } from './icon-type';
import { ServerIllustration } from './ServerIllustration';

interface Node {
  x: number;
  y: number;
}

interface Stage {
  id: string;
  label: string;
  status: string;
  meta?: string;
  icon: IconComponent;
  at: Node;
  width: number;
  /** Kept on small screens, where the diagram reduces to the three key stages. */
  essential?: boolean;
}

/**
 * Every coordinate is a percentage of this component's own box, so the whole diagram scales
 * and repositions as one unit instead of drifting against the viewport.
 */
const SERVER: Node = { x: 42, y: 43 };

const STAGES: Stage[] = [
  {
    id: 'repository',
    label: 'Repository',
    status: 'main',
    meta: 'a1b2c3d',
    icon: GithubIcon,
    at: { x: 21, y: 16 },
    width: 190,
    essential: true,
  },
  {
    id: 'build',
    label: 'Build',
    status: 'Completed',
    meta: '32s',
    icon: Box,
    at: { x: 82, y: 13 },
    width: 180,
  },
  {
    id: 'container',
    label: 'Container',
    status: 'Ready',
    meta: 'alpine:3.19',
    icon: Layers,
    at: { x: 87, y: 43 },
    width: 190,
  },
  {
    id: 'deploy',
    label: 'Deploy',
    status: 'Success',
    meta: 'us-east-1',
    icon: Rocket,
    at: { x: 81, y: 76 },
    width: 190,
    essential: true,
  },
  {
    id: 'live',
    label: 'Live',
    status: 'https://cheslearn.app',
    icon: Globe,
    at: { x: 40, y: 88 },
    width: 190,
    essential: true,
  },
];

const LOGS: Node = { x: 13, y: 65 };

const LOG_LINES = [
  'Installing dependencies',
  'Building project',
  'Optimizing assets',
];

/**
 * Sequential route: Repository → server → Build → Container → Deploy → Live, plus the log
 * feed into the server. Endpoints sit on card edges rather than centres, and control points
 * push each curve clear of the chassis.
 */
const PATHS: Array<{ d: string; delay: number }> = [
  { d: 'M 275 128 Q 320 176 352 214', delay: 0 },
  { d: 'M 498 198 Q 640 128 752 92', delay: 0.7 },
  { d: 'M 822 112 Q 862 168 860 226', delay: 1.4 },
  { d: 'M 862 294 Q 858 372 826 420', delay: 2.1 },
  { d: 'M 742 470 Q 604 528 492 522', delay: 2.8 },
  { d: 'M 208 382 Q 288 340 350 300', delay: 3.5 },
];

const NODES: Node[] = [
  { x: 275, y: 128 },
  { x: 352, y: 214 },
  { x: 498, y: 198 },
  { x: 752, y: 92 },
  { x: 822, y: 112 },
  { x: 860, y: 226 },
  { x: 862, y: 294 },
  { x: 826, y: 420 },
  { x: 742, y: 470 },
  { x: 492, y: 522 },
  { x: 208, y: 382 },
  { x: 350, y: 300 },
];

/** Miniature system-status widget, not a general-purpose card. */
const WIDGET =
  'rounded-[13px] border border-[rgba(255,255,255,0.12)] bg-[rgba(12,15,15,0.94)] shadow-[0_10px_26px_-16px_rgba(0,0,0,0.95)] backdrop-blur-[2px]';

function StageWidget({ stage, reducedMotion }: { stage: Stage; reducedMotion: boolean }) {
  const Icon = stage.icon;

  return (
    <li
      className={cn(
        'pipeline-stage min-w-0 lg:absolute lg:-translate-x-1/2 lg:-translate-y-1/2',
        !stage.essential && 'hidden lg:block',
      )}
      style={{
        left: `${stage.at.x}%`,
        top: `${stage.at.y}%`,
        animationDelay: reducedMotion ? '0ms' : `${0.15 + stage.at.y / 120}s`,
      }}
    >
      <div
        className={cn(WIDGET, 'pipeline-card-float px-2.5 py-2')}
        style={
          {
            '--card-tilt': 'perspective(760px) rotateY(-3deg)',
            '--float-delay': `${stage.at.x / -22}s`,
            width: `${stage.width}px`,
            maxWidth: '100%',
          } as React.CSSProperties
        }
      >
        <div className="flex items-center gap-2">
          <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md border border-[rgba(255,255,255,0.1)] bg-[#121617] text-content-secondary">
            <Icon className="h-3 w-3" strokeWidth={2} aria-hidden="true" />
          </span>
          <p className="min-w-0 flex-1 truncate text-[13px] font-semibold leading-none text-content-primary">
            {stage.label}
          </p>
          <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-success" aria-hidden="true" />
        </div>

        <p className="mt-1.5 truncate pl-8 text-[10.5px] leading-tight text-content-secondary">
          {stage.status}
        </p>
        {stage.meta && (
          <p className="truncate pl-8 font-mono text-[9.5px] leading-tight text-content-muted">
            {stage.meta}
          </p>
        )}
      </div>
    </li>
  );
}

function BuildLogsWidget({ reducedMotion }: { reducedMotion: boolean }) {
  return (
    <li
      className="pipeline-stage absolute hidden -translate-x-1/2 -translate-y-1/2 xl:block"
      style={{
        left: `${LOGS.x}%`,
        top: `${LOGS.y}%`,
        animationDelay: reducedMotion ? '0ms' : '0.15s',
      }}
    >
      <div
        className={cn(WIDGET, 'pipeline-card-float px-2.5 py-2')}
        style={
          {
            '--card-tilt': 'perspective(760px) rotateY(4deg)',
            '--float-delay': '-2.2s',
            width: '200px',
          } as React.CSSProperties
        }
      >
        <p className="text-[13px] font-semibold leading-none text-content-primary">Build Logs</p>

        <div className="mt-2 space-y-[3px] font-mono">
          {LOG_LINES.map((line) => (
            <p key={line} className="truncate text-[9.5px] leading-tight text-content-muted">
              <span aria-hidden="true">›</span> {line}
            </p>
          ))}
          <p className="truncate text-[9.5px] leading-tight text-accent">
            <span aria-hidden="true">›</span> Build completed
          </p>
        </div>
      </div>
    </li>
  );
}

/**
 * The hero diagram: one relative container holding the technical grid, the server, the
 * connection network and the status widgets. Nothing here is positioned against the viewport.
 */
export function HeroVisual({ reducedMotion = false }: { reducedMotion?: boolean }) {
  return (
    <div className="relative h-[380px] sm:h-[440px] lg:h-[532px]">
      {/* Perspective engineering grid, confined to the diagram and kept very low contrast. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 overflow-hidden [mask-image:radial-gradient(ellipse_58%_52%_at_46%_48%,#000_0%,transparent_100%)]"
      >
        <div className="hero-grid absolute inset-x-[-20%] bottom-[-18%] top-[18%] origin-bottom opacity-[0.5] [transform:perspective(560px)_rotateX(66deg)]" />
      </div>

      {/* Contained radial glow behind the server only. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute h-[380px] w-[380px] -translate-x-1/2 -translate-y-1/2 rounded-full"
        style={{
          left: `${SERVER.x}%`,
          top: `${SERVER.y}%`,
          background: 'radial-gradient(circle, rgba(160,255,0,0.08), transparent 55%)',
        }}
      />

      {/* The server itself: ~50% of this SVG's width, so it lands near 300px on desktop. */}
      <div className="pointer-events-none absolute left-[6%] top-[1%] w-[72%]">
        <ServerIllustration />
      </div>

      <svg
        className="pointer-events-none absolute inset-0 hidden h-full w-full lg:block"
        viewBox="0 0 1000 600"
        preserveAspectRatio="none"
        aria-hidden="true"
        style={{ animationDelay: reducedMotion ? '0ms' : '0.9s' }}
      >
        {PATHS.map((path) => (
          <g key={path.d}>
            <path
              d={path.d}
              fill="none"
              stroke="rgba(168,240,0,0.3)"
              strokeWidth="1"
              vectorEffect="non-scaling-stroke"
            />
            <path
              d={path.d}
              fill="none"
              stroke="#b5ff00"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeDasharray="14 986"
              pathLength={1000}
              vectorEffect="non-scaling-stroke"
              style={
                reducedMotion
                  ? { opacity: 0 }
                  : { animation: `dash-flow 5.2s linear ${path.delay}s infinite` }
              }
            />
          </g>
        ))}

        {NODES.map((node) => (
          <circle
            key={`${node.x}:${node.y}`}
            cx={node.x}
            cy={node.y}
            r="2.4"
            fill="#a8f000"
            fillOpacity="0.85"
          />
        ))}
      </svg>

      <ul
        aria-label="Deployment pipeline stages"
        className="absolute inset-x-0 bottom-0 grid list-none grid-cols-3 gap-2 lg:inset-0 lg:block lg:gap-0"
      >
        {STAGES.map((stage) => (
          <StageWidget key={stage.id} stage={stage} reducedMotion={reducedMotion} />
        ))}
        <BuildLogsWidget reducedMotion={reducedMotion} />
      </ul>

    </div>
  );
}
