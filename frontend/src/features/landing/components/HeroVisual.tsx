import { Box, CheckCircle2, Globe, Layers, Rocket, type LucideIcon } from 'lucide-react';
import { useId } from 'react';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { cn } from '@/lib/utils';
import type { IconComponent } from './icon-type';
import { NetworkBackground } from './NetworkBackground';
import { ServerIllustration } from './ServerIllustration';

/**
 * Art-directed deployment scene.
 *
 * The reference is a composition, not a layout, so everything inside this container is
 * absolutely positioned as a percentage of the container itself. Percentages were derived by
 * measuring the reference at 1024×525: the scene column occupies x 384–998, y 58–470, and each
 * card centre and size below is that measurement converted into this box.
 */

interface Card {
  id: string;
  label: string;
  status: string;
  meta?: string;
  icon: IconComponent | LucideIcon;
  /** Centre of the card, as a percentage of the scene. */
  at: { x: number; y: number };
  width: number;
  rotate: number;
  essential?: boolean;
}

const CARDS: Card[] = [
  {
    id: 'repository',
    label: 'Repository',
    status: 'main',
    meta: 'a1b2c3d',
    icon: GithubIcon,
    at: { x: 21.7, y: 14.3 },
    width: 135,
    rotate: -4,
    essential: true,
  },
  {
    id: 'build',
    label: 'Build',
    status: 'Completed',
    meta: '32s',
    icon: Box,
    at: { x: 80.5, y: 14.8 },
    width: 130,
    rotate: 3,
  },
  {
    id: 'container',
    label: 'Container',
    status: 'Ready',
    meta: 'alpine:3.19',
    icon: Layers,
    at: { x: 86.9, y: 41.7 },
    width: 145,
    rotate: 2,
  },
  {
    id: 'deploy',
    label: 'Deploy',
    status: 'Success',
    meta: 'us-east-1',
    icon: Rocket,
    at: { x: 83, y: 69.7 },
    width: 160,
    rotate: 3,
    essential: true,
  },
  {
    id: 'live',
    label: 'Live',
    status: 'https://cheslearn.app',
    icon: Globe,
    at: { x: 39.7, y: 81.8 },
    width: 175,
    rotate: -2,
    essential: true,
  },
];

const LOGS = { x: 13.2, y: 58.7 };

const LOG_LINES = ['Installing dependencies', 'Building project', 'Optimizing assets'];

/**
 * Connectors in scene coordinates (614×412). Endpoints sit on card edges and the curves bend
 * around the chassis, so the network reads as organic wiring rather than radial spokes.
 */
const LINKS = [
  { d: 'M 201 62 Q 320 30 428 47', delay: 0 },
  { d: 'M 356 37 Q 364 72 330 96', delay: 0.5 },
  { d: 'M 494 102 Q 528 138 480 168', delay: 1 },
  { d: 'M 533 214 Q 543 238 512 244', delay: 1.5 },
  { d: 'M 428 287 Q 378 332 331 337', delay: 2 },
  { d: 'M 156 337 Q 108 328 81 292', delay: 2.5 },
  { d: 'M 151 242 Q 186 234 212 212', delay: 3 },
  { d: 'M 390 230 Q 421 252 430 292', delay: 3.5 },
];

const JOINTS = [
  [201, 62],
  [428, 47],
  [356, 37],
  [330, 96],
  [494, 102],
  [480, 168],
  [533, 214],
  [512, 244],
  [428, 287],
  [331, 337],
  [156, 337],
  [81, 292],
  [151, 242],
  [212, 212],
  [390, 230],
  [430, 292],
];

/** HUD panel: darker and smaller than a normal product card. */
const PANEL =
  'rounded-[11px] border border-[rgba(255,255,255,0.17)] bg-[linear-gradient(135deg,rgba(19,23,21,0.94),rgba(7,10,9,0.96))] shadow-[0_12px_35px_rgba(0,0,0,0.38),inset_0_1px_rgba(255,255,255,0.035)]';

function StatusCard({ card, reducedMotion }: { card: Card; reducedMotion: boolean }) {
  const Icon = card.icon;

  return (
    <li
      className={cn(
        'pipeline-stage min-w-0 lg:absolute lg:-translate-x-1/2 lg:-translate-y-1/2',
        !card.essential && 'hidden lg:block',
      )}
      style={{
        left: `${card.at.x}%`,
        top: `${card.at.y}%`,
        animationDelay: reducedMotion ? '0ms' : `${0.1 + card.at.y / 140}s`,
      }}
    >
      <div
        className={cn(PANEL, 'pipeline-card-float px-2 py-1.5 lg:px-2.5 lg:py-2')}
        style={
          {
            '--card-tilt': `perspective(900px) rotateY(-4deg) rotate(${card.rotate}deg)`,
            '--float-delay': `${card.at.x / -26}s`,
            width: `${card.width}px`,
            maxWidth: '100%',
          } as React.CSSProperties
        }
      >
        <div className="flex items-center gap-1.5">
          <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-[6px] border border-[rgba(255,255,255,0.12)] bg-[#111516] text-content-secondary">
            <Icon className="h-2.5 w-2.5" strokeWidth={2} aria-hidden="true" />
          </span>
          <p className="min-w-0 flex-1 truncate text-[10px] font-semibold leading-none text-content-primary">
            {card.label}
          </p>
          <CheckCircle2 className="h-3 w-3 shrink-0 text-success" aria-hidden="true" />
        </div>

        <p className="mt-1 truncate pl-6.5 text-[7.5px] leading-tight text-content-secondary">
          {card.status}
        </p>
        {card.meta && (
          <p className="truncate pl-6.5 font-mono text-[7px] leading-tight text-content-muted">
            {card.meta}
          </p>
        )}
      </div>
    </li>
  );
}

function BuildLogsCard({ reducedMotion }: { reducedMotion: boolean }) {
  return (
    <li
      className="pipeline-stage absolute hidden -translate-x-1/2 -translate-y-1/2 lg:block"
      style={{
        left: `${LOGS.x}%`,
        top: `${LOGS.y}%`,
        animationDelay: reducedMotion ? '0ms' : '0.1s',
      }}
    >
      <div
        className={cn(PANEL, 'pipeline-card-float px-2.5 py-2')}
        style={
          {
            '--card-tilt': 'perspective(900px) rotateY(3deg) rotate(-2deg)',
            '--float-delay': '-2.4s',
            width: '145px',
          } as React.CSSProperties
        }
      >
        <p className="text-[9.5px] font-semibold leading-none text-content-primary">Build Logs</p>

        <div className="mt-1.5 space-y-[2px] font-mono">
          {LOG_LINES.map((line) => (
            <p key={line} className="truncate text-[7px] leading-tight text-content-muted">
              <span aria-hidden="true">›</span> {line}
            </p>
          ))}
          <p className="truncate text-[7px] leading-tight text-accent">
            <span aria-hidden="true">›</span> Build completed
          </p>
          <p className="text-[7px] leading-tight text-content-muted" aria-hidden="true">
            _
          </p>
        </div>
      </div>
    </li>
  );
}

export function HeroVisual({ reducedMotion = false }: { reducedMotion?: boolean }) {
  const uid = useId().replace(/:/g, '');
  const glowId = `link-glow-${uid}`;

  return (
    <div className="relative h-[330px] sm:h-[380px] lg:h-[412px]">
      <NetworkBackground />

      {/* Server: 67% of the scene width lands the cube near 215px, matching the reference. */}
      <div className="pointer-events-none absolute left-[15%] top-[4%] w-[67%]">
        <ServerIllustration />
      </div>

      <svg
        className="pointer-events-none absolute inset-0 hidden h-full w-full lg:block"
        viewBox="0 0 614 412"
        preserveAspectRatio="none"
        aria-hidden="true"
        focusable="false"
      >
        <defs>
          <filter id={glowId} x="-300%" y="-300%" width="700%" height="700%">
            <feGaussianBlur stdDeviation="2.2" />
          </filter>
        </defs>

        {LINKS.map((link) => (
          <g key={link.d}>
            <path
              d={link.d}
              fill="none"
              stroke="rgba(170,255,0,0.42)"
              strokeWidth="1"
              vectorEffect="non-scaling-stroke"
            />
            <path
              d={link.d}
              fill="none"
              stroke="#b6ff00"
              strokeWidth="1.4"
              strokeLinecap="round"
              strokeDasharray="10 990"
              pathLength={1000}
              vectorEffect="non-scaling-stroke"
              style={
                reducedMotion
                  ? { opacity: 0 }
                  : { animation: `dash-flow 4.8s linear ${link.delay}s infinite` }
              }
            />
          </g>
        ))}

        {JOINTS.map(([x, y]) => (
          <g key={`${x}:${y}`}>
            <circle cx={x} cy={y} r="4" fill="#b6ff00" opacity="0.55" filter={`url(#${glowId})`} />
            <circle cx={x} cy={y} r="1.9" fill="#d9ff85" />
          </g>
        ))}
      </svg>

      <ul
        aria-label="Deployment pipeline stages"
        className="absolute inset-x-0 bottom-0 grid list-none grid-cols-3 gap-1.5 lg:inset-0 lg:block lg:gap-0"
      >
        {CARDS.map((card) => (
          <StatusCard key={card.id} card={card} reducedMotion={reducedMotion} />
        ))}
        <BuildLogsCard reducedMotion={reducedMotion} />
      </ul>
    </div>
  );
}
