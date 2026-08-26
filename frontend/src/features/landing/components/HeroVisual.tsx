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
 * This is a composition rather than a layout, so every element is absolutely positioned as a
 * percentage of this container. The percentages come from measuring the reference at 1024×525,
 * where the scene occupies x 384–998 and y 58–498 — a 614×440 box. Sizes are percentages of
 * that same box and card internals use `cqw`, so the whole diagram scales as one unit.
 */

interface Card {
  id: string;
  label: string;
  status: string;
  meta?: string;
  icon: IconComponent | LucideIcon;
  /** Card centre, as a percentage of the scene. */
  at: { x: number; y: number };
  /** Width as a percentage of the scene: the reference's 135px of 614 becomes 22%. */
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
    at: { x: 21.7, y: 13.4 },
    width: 22,
    rotate: -4,
    essential: true,
  },
  {
    id: 'build',
    label: 'Build',
    status: 'Completed',
    meta: '32s',
    icon: Box,
    at: { x: 80.5, y: 13.9 },
    width: 21.2,
    rotate: 4,
  },
  {
    id: 'container',
    label: 'Container',
    status: 'Ready',
    meta: 'alpine:3.19',
    icon: Layers,
    at: { x: 86.9, y: 39.1 },
    width: 23.6,
    rotate: 6,
  },
  {
    id: 'deploy',
    label: 'Deploy',
    status: 'Success',
    meta: 'us-east-1',
    icon: Rocket,
    at: { x: 83, y: 65.2 },
    width: 26,
    rotate: 5,
    essential: true,
  },
  {
    id: 'live',
    label: 'Live',
    status: 'https://cheslearn.app',
    icon: Globe,
    at: { x: 39.7, y: 76.6 },
    width: 28.5,
    rotate: 2,
    essential: true,
  },
];

const LOGS = { x: 13.2, y: 55 };

const LOG_LINES = ['Installing dependencies', 'Building project', 'Optimizing assets'];

/** A couple of detached technical elements, kept deliberately unobtrusive. */
const DETACHED = [
  { x: 4, y: 26, size: 3.4, rotate: -12 },
  { x: 95.5, y: 29, size: 2.9, rotate: 14 },
];

/**
 * Connectors in scene coordinates (614×440). Each ends on a card edge and bends around the
 * chassis, so the network reads as organic wiring rather than radial spokes.
 */
const LINKS = [
  { d: 'M 200 66 Q 250 52 296 44 T 424 50', delay: 0 },
  { d: 'M 352 40 Q 366 78 334 104', delay: 0.5 },
  { d: 'M 492 108 Q 530 140 478 160', delay: 1 },
  { d: 'M 530 200 Q 542 240 508 258', delay: 1.5 },
  { d: 'M 424 300 Q 372 344 334 352', delay: 2 },
  { d: 'M 156 352 Q 110 342 84 300', delay: 2.5 },
  { d: 'M 150 236 Q 188 228 214 208', delay: 3 },
  { d: 'M 386 232 Q 420 258 428 302', delay: 3.5 },
];

/** Nodes only where a route actually bends or terminates. */
const NODES: Array<[number, number, number]> = [
  [200, 66, 2.6],
  [296, 44, 3.2],
  [424, 50, 2.6],
  [352, 40, 2.2],
  [334, 104, 2.6],
  [492, 108, 2.4],
  [478, 160, 2.8],
  [530, 200, 2.4],
  [508, 258, 2.8],
  [424, 300, 2.6],
  [334, 352, 3],
  [156, 352, 2.4],
  [84, 300, 2.6],
  [150, 236, 2.4],
  [214, 208, 2.8],
  [386, 232, 2.2],
];

/** HUD panel: dark, translucent, and quieter than a normal product card. */
const PANEL =
  'rounded-[10px] border border-[rgba(255,255,255,0.16)] bg-[linear-gradient(135deg,rgba(20,24,22,0.96),rgba(7,10,9,0.96))] shadow-[0_12px_30px_rgba(0,0,0,0.32),inset_0_1px_0_rgba(255,255,255,0.035)] backdrop-blur-[6px] lg:rounded-[1.6cqw]';

function DetachedCube({ rotate }: { rotate: number }) {
  return (
    <svg
      viewBox="0 0 40 44"
      className="h-auto w-full"
      style={{ transform: `rotate(${rotate}deg)` }}
      aria-hidden="true"
      focusable="false"
    >
      <polygon points="20,2 38,12 20,22 2,12" fill="#22282a" />
      <polygon points="2,12 20,22 20,42 2,32" fill="#0d1112" />
      <polygon points="20,22 38,12 38,32 20,42" fill="#080b0c" />
      <path d="M2,32 L20,42 L38,32" fill="none" stroke="#a6ff00" strokeOpacity="0.55" strokeWidth="1.4" />
    </svg>
  );
}

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
        className={cn(
          PANEL,
          'pipeline-card-float w-full px-2 py-1.5',
          'lg:w-[var(--card-w)] lg:px-[1.5cqw] lg:py-[1.2cqw]',
        )}
        style={
          {
            '--card-tilt': `rotate(${card.rotate}deg)`,
            '--float-delay': `${card.at.x / -26}s`,
            '--card-w': `${card.width}%`,
          } as React.CSSProperties
        }
      >
        <div className="flex items-center gap-1.5 lg:gap-[1cqw]">
          <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-[rgba(255,255,255,0.14)] bg-[#121617] text-white/80 lg:h-[3.1cqw] lg:w-[3.1cqw]">
            <Icon className="h-2.5 w-2.5 lg:h-[1.7cqw] lg:w-[1.7cqw]" strokeWidth={2} aria-hidden="true" />
          </span>
          <p className="min-w-0 flex-1 truncate text-[10px] font-semibold leading-none text-white/90 lg:text-[1.79cqw]">
            {card.label}
          </p>
          <CheckCircle2
            className="h-3 w-3 shrink-0 text-[#a6ff00] lg:h-[2.1cqw] lg:w-[2.1cqw]"
            aria-hidden="true"
          />
        </div>

        <p className="mt-1 truncate pl-6.5 text-[8px] leading-[1.4] text-white/50 lg:mt-[0.7cqw] lg:pl-[4.1cqw] lg:text-[1.3cqw]">
          {card.status}
        </p>
        {card.meta && (
          <p className="truncate pl-6.5 font-mono text-[7.5px] leading-[1.4] text-white/50 lg:pl-[4.1cqw] lg:text-[1.3cqw]">
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
        className={cn(PANEL, 'pipeline-card-float w-[23.6%] px-[1.5cqw] py-[1.2cqw]')}
        style={
          {
            '--card-tilt': 'rotate(-4deg)',
            '--float-delay': '-2.4s',
          } as React.CSSProperties
        }
      >
        <p className="text-[1.7cqw] font-semibold leading-none text-white/90">Build Logs</p>

        <div className="mt-[1cqw] space-y-[0.3cqw] font-mono">
          {LOG_LINES.map((line) => (
            <p key={line} className="truncate text-[1.14cqw] leading-[1.5] text-white/50">
              <span aria-hidden="true">›</span> {line}
            </p>
          ))}
          <p className="truncate text-[1.14cqw] leading-[1.5] text-[#a6ff00]">
            <span aria-hidden="true">›</span> Build completed
          </p>
          <p className="text-[1.14cqw] leading-[1.5] text-white/40" aria-hidden="true">
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
    // `@container` makes cqw resolve against this box; the reference 614×440 aspect lets the
    // composition scale without re-deriving a single coordinate.
    <div className="@container relative h-[330px] sm:h-[380px] lg:h-auto lg:aspect-[614/440]">
      <NetworkBackground />

      {/* Detached technical elements sit behind the connectors. */}
      {DETACHED.map((cube) => (
        <div
          key={`${cube.x}:${cube.y}`}
          aria-hidden="true"
          className="pointer-events-none absolute hidden -translate-x-1/2 -translate-y-1/2 opacity-90 lg:block"
          style={{ left: `${cube.x}%`, top: `${cube.y}%`, width: `${cube.size}%` }}
        >
          <DetachedCube rotate={cube.rotate} />
        </div>
      ))}

      {/* Connectors and nodes, layered beneath the platform and cards. */}
      <svg
        className="pointer-events-none absolute inset-0 hidden h-full w-full lg:block"
        viewBox="0 0 614 440"
        preserveAspectRatio="none"
        aria-hidden="true"
        focusable="false"
      >
        <defs>
          <filter id={glowId} x="-300%" y="-300%" width="700%" height="700%">
            <feGaussianBlur stdDeviation="2" />
          </filter>
        </defs>

        {LINKS.map((link) => (
          <g key={link.d}>
            <path
              d={link.d}
              fill="none"
              stroke="rgba(174,255,0,0.65)"
              strokeWidth="1"
              vectorEffect="non-scaling-stroke"
            />
            <path
              d={link.d}
              fill="none"
              stroke="#c5ff45"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeDasharray="10 990"
              pathLength={1000}
              vectorEffect="non-scaling-stroke"
              style={
                reducedMotion
                  ? { opacity: 0 }
                  : { animation: `dash-flow 5.4s linear ${link.delay}s infinite` }
              }
            />
          </g>
        ))}

        {NODES.map(([x, y, r]) => (
          <g key={`${x}:${y}`}>
            <circle cx={x} cy={y} r={r * 1.9} fill="#c5ff45" opacity="0.45" filter={`url(#${glowId})`} />
            <circle cx={x} cy={y} r={r} fill="#c5ff45" />
          </g>
        ))}
      </svg>

      {/* Appliance and platform. 67% of the scene puts the body near the reference's 215px. */}
      <div className="pointer-events-none absolute left-[18.7%] top-[18%] w-[67%]">
        <ServerIllustration />
      </div>

      <ul
        aria-label="Deployment pipeline stages"
        className="absolute inset-x-0 bottom-0 grid list-none grid-cols-3 items-end gap-1.5 lg:inset-0 lg:block lg:gap-0"
      >
        {CARDS.map((card) => (
          <StatusCard key={card.id} card={card} reducedMotion={reducedMotion} />
        ))}
        <BuildLogsCard reducedMotion={reducedMotion} />
      </ul>
    </div>
  );
}
