import { Box, Check, Globe, Layers, Rocket, Shuffle, type LucideIcon } from 'lucide-react';
import { useId } from 'react';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { cn } from '@/lib/utils';
import { HeroServer, HERO_SERVER_BOX } from './HeroServer';
import type { IconComponent } from './icon-type';
import { NetworkBackground } from './NetworkBackground';

/**
 * Art-directed deployment scene.
 *
 * ONE canonical coordinate system: a 620x410 box whose units come from the reference render
 * (1024x682, mapped at 0.6055). Chassis, pedestal, cards, wiring and mesh are all authored in
 * those units, so their relationships are fixed. The scene then fills the hero's existing visual
 * column, which keeps the artwork large without a global transform and without touching the
 * hero's dimensions.
 *
 * Depth is real: the wiring sits behind everything, Repository and Build sit behind the chassis,
 * and Container / Build Logs / Deploy / Live sit in front of it.
 */

const SCENE_W = 620;
const SCENE_H = 410;

/** Canonical scene pixels -> container-query units of the scene itself. */
const u = (n: number) => `${((n / SCENE_W) * 100).toFixed(3)}cqw`;

/** Type/padding scale shared by the status cards, so their content reads as one system. */
const CARD_SCALE = 0.9;
const LOGS_SCALE = 0.9;

interface Card {
  id: string;
  label: string;
  status: string;
  meta?: string;
  icon: IconComponent | LucideIcon;
  /** Top-left corner and box size, in canonical scene units. */
  x: number;
  y: number;
  w: number;
  h: number;
  rotate: number;
  /** Behind the chassis (1) or in front of it (10). */
  z: number;
  /** The URL on the Live card is monospaced in the reference. */
  monoStatus?: boolean;
  /** Kept in the simplified layout below lg. */
  essential?: boolean;
}

/* Boxes measured off the reference render, so each card is 48-69% of the chassis width. */
const CARDS: Card[] = [
  {
    id: 'repository',
    label: 'Repository',
    status: 'main',
    meta: 'a1b2c3d',
    icon: GithubIcon,
    x: 85,
    y: 19,
    w: 110,
    h: 63,
    rotate: -4,
    z: 1,
    essential: true,
  },
  {
    id: 'build',
    label: 'Build',
    status: 'Completed',
    meta: '32s',
    icon: Box,
    x: 422,
    y: 8,
    w: 136,
    h: 76,
    rotate: 5,
    z: 1,
  },
  {
    id: 'container',
    label: 'Container',
    status: 'Ready',
    meta: 'alpine:3.19',
    icon: Layers,
    x: 446,
    y: 109,
    w: 144,
    h: 86,
    rotate: 7,
    z: 10,
  },
  {
    id: 'deploy',
    label: 'Deploy',
    status: 'Success',
    meta: 'us-east-1',
    icon: Rocket,
    x: 414,
    y: 230,
    w: 172,
    h: 86,
    rotate: 6,
    z: 10,
    essential: true,
  },
  {
    id: 'live',
    label: 'Live',
    status: 'https://go.deploylane.app',
    icon: Globe,
    x: 159,
    y: 291,
    w: 167,
    h: 61,
    rotate: 2,
    z: 10,
    monoStatus: true,
    essential: true,
  },
];

const LOGS = { x: 10, y: 170, w: 146, h: 131, rotate: -4 };

const STAGE_TIMING = {
  repository: 0.8,
  build: 3.65,
  container: 6.5,
  deploy: 9.35,
  live: 12.2,
  logs: 3.85,
} as const;
type PipelineStage = keyof typeof STAGE_TIMING;

const STAGE_ACTIVITY_DELAY: Record<PipelineStage, string> = Object.fromEntries(
  Object.entries(STAGE_TIMING).map(([stage, seconds]) => [stage, `${seconds}s`]),
) as Record<PipelineStage, string>;

/** The chassis SVG carries scene coordinates, so it maps 1:1 into the scene. */
const SERVER = HERO_SERVER_BOX;

const LOG_LINES = ['Installing dependencies', 'Building project', 'Optimizing assets'];

/** Small dark geometry, distant and low contrast. */
const CUBES = [
  { x: 20, y: 120, size: 34, rotate: -12 },
  { x: 588, y: 122, size: 30, rotate: 14 },
  { x: 498, y: 378, size: 27, rotate: 8 },
];

/* ═══════════════════════════════════════════════════════════════════════
   HUB-AND-SPOKE CONNECTION SYSTEM

   The server is the hub. Every card connects to it with a single smooth
   curve that starts on the card's inner edge and ends at the hub. A pulse
   of light flows from the hub outward to each card in deploy order, so the
   relationship "server powers every stage" is instantly readable and no two
   lines ever cross. This is far clearer than an abstract orbital ring.
   ═══════════════════════════════════════════════════════════════════════ */

/** The hub: the top-centre of the pedestal, where every connector converges. */
const HUB: [number, number] = [304, 210];

/**
 * One connector per stage. `anchor` sits on the card edge nearest the hub. Each connector is a
 * smooth cubic whose control points lean out horizontally from both ends, giving a calm S-free
 * arc that clearly links card ↔ hub without crossing its neighbours.
 */
const PIPELINE: Array<{ stage: PipelineStage; anchor: [number, number] }> = [
  { stage: 'repository', anchor: [190, 74] },  // top-left card, bottom edge
  { stage: 'build', anchor: [438, 78] },       // top-right card, bottom edge
  { stage: 'container', anchor: [446, 150] },  // right card, left edge
  { stage: 'deploy', anchor: [414, 262] },     // lower-right card, left edge
  { stage: 'live', anchor: [268, 300] },       // bottom card, top edge
];

/**
 * A connector from a card anchor to the hub. The control points pull horizontally toward the hub's
 * x, so lines approach the hub fanned out like spokes and leave each card cleanly — no crossings,
 * no wobble. Vertical distance sets how much the curve bows, so near and far cards both look calm.
 */
function connectorPath(anchor: [number, number]): string {
  const [ax, ay] = anchor;
  const [hx, hy] = HUB;
  const midX = (ax + hx) / 2;
  return `M ${ax} ${ay} C ${midX} ${ay}, ${midX} ${hy}, ${hx} ${hy}`;
}

/** Connectors, in deploy order, tagged with their stage + 1-based step number for the badge. */
const LINKS: Array<{ d: string; stage: PipelineStage; step: number; anchor: [number, number] }> =
  PIPELINE.map((p, i) => ({
    d: connectorPath(p.anchor),
    stage: p.stage,
    step: i + 1,
    anchor: p.anchor,
  }));



/** Floating black glass panel: near-opaque, thin light edge, faint green bounce underneath. */
const PANEL =
  'border border-[rgba(180,200,190,0.26)] bg-[linear-gradient(145deg,rgba(23,27,24,0.96),rgba(7,10,9,0.98))] shadow-[0_14px_34px_rgba(0,0,0,0.5),inset_0_1px_0_rgba(255,255,255,0.12),inset_0_-1px_0_rgba(183,255,0,0.18)] backdrop-blur-[6px]';

function DetachedCube({ rotate }: { rotate: number }) {
  return (
    <svg
      viewBox="0 0 40 44"
      className="h-auto w-full"
      style={{ transform: `rotate(${rotate}deg)` }}
      aria-hidden="true"
      focusable="false"
    >
      <polygon points="20,2 38,12 20,22 2,12" fill="#1b201c" />
      <polygon points="2,12 20,22 20,42 2,32" fill="#0b0e0c" />
      <polygon points="20,22 38,12 38,32 20,42" fill="#070908" />
      <path
        d="M2,32 L20,42 L38,32"
        fill="none"
        stroke="#b7ff00"
        strokeOpacity="0.4"
        strokeWidth="1.4"
      />
    </svg>
  );
}

/** Desktop card: fixed canonical box, art-directed position, no flex sizing anywhere. */
function StatusCard({ card, reducedMotion }: { card: Card; reducedMotion: boolean }) {
  const Icon = card.icon;
  const s = (n: number) => u(n * CARD_SCALE);

  return (
    <li
      className="pipeline-stage absolute"
      style={{
        left: u(card.x),
        top: u(card.y),
        width: u(card.w),
        height: u(card.h),
        zIndex: card.z,
        flex: 'none',
        animationDelay: reducedMotion ? '0ms' : `${0.1 + card.y / 420}s`,
      }}
    >
      <div
        className={cn(
          PANEL,
          'pipeline-card-float pipeline-stage-card relative flex h-full w-full flex-col justify-center',
          reducedMotion && 'motion-reduced',
        )}
        style={
          {
            padding: `${s(8)} ${s(9)}`,
            borderRadius: s(10),
            '--card-tilt': `rotate(${card.rotate}deg)`,
            '--float-delay': `${card.x / -140}s`,
            '--stage-activity-delay': STAGE_ACTIVITY_DELAY[card.id as PipelineStage],
            ...(reducedMotion ? { animation: 'none' } : null),
          } as React.CSSProperties
        }
      >
        {/* Icon on the left, copy indented beside it — the reference's card anatomy. */}
        <div className="flex items-start" style={{ gap: s(7) }}>
          <Icon
            className="pipeline-card-icon shrink-0 text-white/85"
            style={{ width: s(16), height: s(16), flex: 'none' }}
            strokeWidth={1.6}
            aria-hidden="true"
          />
          <div className="min-w-0">
            <p
              className="whitespace-nowrap font-semibold leading-none text-white/92"
              style={{ fontSize: s(10) }}
            >
              {card.label}
            </p>
            <p
              className={cn('whitespace-nowrap text-white/72', card.monoStatus && 'font-mono')}
              style={{ fontSize: s(8), lineHeight: 1.6, marginTop: s(7), paddingRight: s(15) }}
            >
              {card.status}
            </p>
            {card.meta && (
              <p
                className="whitespace-nowrap font-mono text-white/60"
                style={{ fontSize: s(8), lineHeight: 1.6, paddingRight: s(15) }}
              >
                {card.meta}
              </p>
            )}
          </div>
        </div>

        <span
          className="pipeline-status-indicator absolute flex -translate-y-1/2 items-center justify-center rounded-full bg-[#6fbf00]"
          style={{ right: s(9), top: '62%', width: s(15), height: s(15) }}
          aria-hidden="true"
        >
          <Check style={{ width: s(10), height: s(10) }} strokeWidth={4} className="text-white" />
        </span>
      </div>
    </li>
  );
}

function BuildLogsCard({ reducedMotion }: { reducedMotion: boolean }) {
  const s = (n: number) => u(n * LOGS_SCALE);
  const lineStyle = { fontSize: s(7), lineHeight: 1.85 };

  return (
    <li
      className="pipeline-stage absolute"
      style={{
        left: u(LOGS.x),
        top: u(LOGS.y),
        width: u(LOGS.w),
        height: u(LOGS.h),
        zIndex: 10,
        flex: 'none',
        animationDelay: reducedMotion ? '0ms' : '0.1s',
      }}
    >
      <div
        className={cn(PANEL, 'pipeline-card-float pipeline-stage-card h-full w-full', reducedMotion && 'motion-reduced')}
        style={
          {
            padding: `${s(9)} ${s(10)}`,
            borderRadius: s(10),
            '--card-tilt': `rotate(${LOGS.rotate}deg)`,
            '--float-delay': '-2.4s',
            '--stage-activity-delay': STAGE_ACTIVITY_DELAY.logs,
            ...(reducedMotion ? { animation: 'none' } : null),
          } as React.CSSProperties
        }
      >
        <div className="flex items-center justify-between">
          <p className="font-semibold leading-none text-white/90" style={{ fontSize: s(9.5) }}>
            Build Logs
          </p>
          <Shuffle
            className="text-white/35"
            style={{ width: s(9), height: s(9), flex: 'none' }}
            aria-hidden="true"
          />
        </div>

        <div className="font-mono" style={{ marginTop: s(7) }}>
          {LOG_LINES.map((line, index) => (
            <p
              key={line}
              className="build-log-line whitespace-nowrap text-white/60"
              style={
                {
                  ...lineStyle,
                  '--log-stage-delay': `${STAGE_TIMING.build + 0.35 + index * 0.36}s`,
                } as React.CSSProperties
              }
            >
              <span aria-hidden="true">›</span> {line}
            </p>
          ))}
          <p
            className="build-log-line whitespace-nowrap text-[#b7ff00]"
            style={
              { ...lineStyle, '--log-stage-delay': `${STAGE_TIMING.build + 1.45}s` } as React.CSSProperties
            }
          >
            <span aria-hidden="true">›</span> Build completed
          </p>
          <p className="build-log-cursor text-white/40" style={lineStyle} aria-hidden="true">
            _
          </p>
        </div>
      </div>
    </li>
  );
}

export function HeroVisual({ reducedMotion = false }: { reducedMotion?: boolean }) {
  const uid = useId().replace(/:/g, '');
  const glowId = `hero-node-glow-${uid}`;
  const wireGlowId = `hero-wire-glow-${uid}`;

  return (
    // Outer box holds the scene at a fixed aspect ratio; the composition inside is authored in
    // container-query units, so it scales as one unit from the widest desktop down to a 320px phone.
    <div className="@container relative min-w-0 w-full aspect-[614/440]">
      {/* The full art-directed scene — identical at every breakpoint. */}
      <div
        className="@container absolute left-1/2 top-[49.5%] block -translate-x-1/2 -translate-y-1/2"
        style={{ width: '100%', aspectRatio: `${SCENE_W} / ${SCENE_H}` }}
      >
        <NetworkBackground />

        {CUBES.map((cube) => (
          <div
            key={`${cube.x}:${cube.y}`}
            aria-hidden="true"
            className="pointer-events-none absolute"
            style={{
              left: u(cube.x),
              top: u(cube.y),
              width: u(cube.size),
              zIndex: 0,
              flex: 'none',
            }}
          >
            <DetachedCube rotate={cube.rotate} />
          </div>
        ))}

        {/* Hub-and-spoke wiring: one clean connector per stage, converging on the server hub. */}
        <svg
          className="pointer-events-none absolute inset-0 h-full w-full"
          viewBox={`0 0 ${SCENE_W} ${SCENE_H}`}
          preserveAspectRatio="none"
          style={{ zIndex: 0 }}
          aria-hidden="true"
          focusable="false"
        >
          <defs>
            <filter id={wireGlowId} x="-40%" y="-40%" width="180%" height="180%">
              <feGaussianBlur stdDeviation="2.4" result="wireBlur" />
              <feMerge>
                <feMergeNode in="wireBlur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
            {/* Line gradient: brightest at the hub, fading toward the card, so the eye reads the
                server as the source of energy. Applied per-connector via userSpaceOnUse. */}
            <radialGradient
              id={`${wireGlowId}-grad`}
              gradientUnits="userSpaceOnUse"
              cx={HUB[0]}
              cy={HUB[1]}
              r="300"
            >
              <stop offset="0" stopColor="#c9ff5c" stopOpacity="0.6" />
              <stop offset="0.55" stopColor="#a8f000" stopOpacity="0.34" />
              <stop offset="1" stopColor="#5d8500" stopOpacity="0.14" />
            </radialGradient>
            <filter id={`${wireGlowId}-hub`} x="-300%" y="-300%" width="700%" height="700%">
              <feGaussianBlur stdDeviation="6" />
            </filter>
          </defs>

          {/* Soft emitter glow at the hub — reads as the server dispatching energy. */}
          <circle
            className={cn('connection-hub-glow', reducedMotion && 'motion-reduced')}
            cx={HUB[0]}
            cy={HUB[1]}
            r="26"
            fill="#a8f000"
            opacity="0.14"
            filter={`url(#${wireGlowId}-hub)`}
          />

          {LINKS.map((link) => (
            <g
              key={link.d}
              className={cn('pipeline-connection-group', reducedMotion && 'motion-reduced')}
              data-stage={link.stage}
              style={
                {
                  '--link-stage-delay': `${STAGE_TIMING[link.stage]}s`,
                } as React.CSSProperties
              }
            >
              {/* Conduit: a faint dashed rail underneath, giving the line a technical texture. */}
              <path
                d={link.d}
                fill="none"
                stroke="rgba(183,255,0,0.1)"
                strokeWidth="3.2"
                strokeLinecap="round"
                vectorEffect="non-scaling-stroke"
              />
              {/* Calm base line — gradient from bright hub to dim card, always visible. */}
              <path
                className="connection-wire"
                d={link.d}
                fill="none"
                stroke={`url(#${wireGlowId}-grad)`}
                strokeWidth="1.3"
                strokeLinecap="round"
                strokeLinejoin="round"
                vectorEffect="non-scaling-stroke"
                filter={`url(#${wireGlowId})`}
              />
              {/* Bright pulse travelling hub → card (outward), in deploy order. The path is drawn
                  card → hub, so the -reverse keyframe makes the dash flow outward from the server. */}
              <path
                className="connection-packet connection-packet-reverse"
                d={link.d}
                fill="none"
                stroke="#eaffbe"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeDasharray="12 74"
                pathLength={1000}
                vectorEffect="non-scaling-stroke"
                filter={`url(#${wireGlowId})`}
                style={
                  (reducedMotion
                    ? { opacity: 0, animation: 'none' }
                    : {
                        '--signal-stage-delay': `${STAGE_TIMING[link.stage]}s`,
                      }) as React.CSSProperties
                }
              />
            </g>
          ))}
        </svg>

        {/* A neutral tilt lets the chassis reuse the shared float: ~3px over 6s, nothing more. */}
        <div
          className={cn('pointer-events-none absolute', !reducedMotion && 'pipeline-card-float')}
          style={
            {
              left: u(SERVER.x),
              top: u(SERVER.y),
              width: u(SERVER.width),
              height: u(SERVER.height),
              zIndex: 5,
              flex: 'none',
              '--card-tilt': 'translate3d(0,0,0)',
              '--float-delay': '-1s',
            } as React.CSSProperties
          }
        >
          <HeroServer className={reducedMotion ? 'motion-reduced' : undefined} />
        </div>

        <ul aria-label="Deployment pipeline stages" className="absolute inset-0 list-none">
          {CARDS.map((card) => (
            <StatusCard key={card.id} card={card} reducedMotion={reducedMotion} />
          ))}
          <BuildLogsCard reducedMotion={reducedMotion} />
        </ul>

        {/* Illuminated terminals read above the hardware they attach to. */}
        <svg
          className="pointer-events-none absolute inset-0 h-full w-full"
          viewBox={`0 0 ${SCENE_W} ${SCENE_H}`}
          preserveAspectRatio="none"
          style={{ zIndex: 20 }}
          aria-hidden="true"
          focusable="false"
        >
          <defs>
            <filter id={glowId} x="-300%" y="-300%" width="700%" height="700%">
              <feGaussianBlur stdDeviation="1.1" />
            </filter>
          </defs>

          {/* A clean glowing dot where each connector meets its card, pulsing in deploy order. */}
          {LINKS.map((link) => {
            const [x, y] = link.anchor;
            return (
              <g
                key={`dot-${link.step}`}
                className={cn('pipeline-node-stage', reducedMotion && 'motion-reduced')}
                style={
                  {
                    '--node-stage-delay': `${STAGE_TIMING[link.stage]}s`,
                  } as React.CSSProperties
                }
              >
                <circle
                  className="connection-node-glow"
                  cx={x}
                  cy={y}
                  r="5"
                  fill="#b7ff00"
                  opacity="0.22"
                  filter={`url(#${glowId})`}
                />
                <circle className="connection-node-core" cx={x} cy={y} r="2.4" fill="#eaffbe" />
              </g>
            );
          })}

          {/* Hub node — the brightest point, where every connector converges. */}
          <g className={cn('pipeline-node-stage', reducedMotion && 'motion-reduced')}>
            <circle
              className="connection-node-glow"
              cx={HUB[0]}
              cy={HUB[1]}
              r="7"
              fill="#b7ff00"
              opacity="0.4"
              filter={`url(#${glowId})`}
            />
            <circle className="connection-node-core" cx={HUB[0]} cy={HUB[1]} r="3" fill="#f2ffc4" />
          </g>
        </svg>
      </div>
    </div>
  );
}
