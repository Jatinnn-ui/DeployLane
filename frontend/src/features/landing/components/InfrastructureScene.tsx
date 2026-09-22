import { useId } from 'react';
import { memo } from 'react';
import { cn } from '@/lib/utils';
import { HERO_SERVER_BOX, HeroServer } from './HeroServer';

/**
 * Lower-section infrastructure scene.
 *
 * Renders the hero's `HeroServer` sitting on a set of clean Saturn-style orbital rings. The rings
 * are complete, smooth ellipses (not broken arcs), so the composition reads instantly: a server on
 * a lit platform, orbited by concentric energy tracks with a few bright nodes gliding around them.
 *
 * COORDINATES - authored in HeroServer's own units. `HERO_SERVER_BOX` maps the appliance back in as
 * an exact sub-rectangle. The orbits are drawn as flat ellipses in scene space (already
 * foreshortened), and the far/near halves are split by the pedestal so an orbit passes *behind* the
 * server and reappears in front — that is what sells the 3D depth.
 */

const SCENE = { x: 107, y: 46, width: 396, height: 316 };
const VIEW = `${SCENE.x} ${SCENE.y} ${SCENE.width} ${SCENE.height}`;

/** Orbit centre = the pedestal centre, and the ellipse squash matches the platform's foreshorten. */
const CENTER = { cx: 304, cy: 236 };
const SQUASH = 0.3;

const RAD = Math.PI / 180;
const round = (n: number) => Math.round(n * 100) / 100;

/**
 * The three orbital rings. Each is a full ellipse with a soft base track and a brighter front-arc
 * highlight, plus a couple of nodes that ride it. Radii sit just outside the pedestal footprint.
 * Durations are close but unequal, so the system drifts like real orbits rather than a spinner.
 */
interface Orbit {
  id: string;
  radius: number;
  duration: string;
  reverse?: boolean;
  trackOpacity: number;
  /** Nodes gliding on this ring, by starting angle + tier. */
  nodes: Array<{ a: number; tier: 'primary' | 'secondary' }>;
}

const ORBITS: Orbit[] = [
  {
    id: 'inner',
    radius: 168,
    duration: '26s',
    trackOpacity: 0.5,
    nodes: [
      { a: 20, tier: 'primary' },
      { a: 205, tier: 'secondary' },
    ],
  },
  {
    id: 'mid',
    radius: 202,
    duration: '34s',
    reverse: true,
    trackOpacity: 0.34,
    nodes: [
      { a: 130, tier: 'secondary' },
      { a: 320, tier: 'primary' },
    ],
  },
  {
    id: 'outer',
    radius: 236,
    duration: '46s',
    trackOpacity: 0.2,
    nodes: [{ a: 260, tier: 'secondary' }],
  },
];

/** Small dark geometry at a few depths — quiet accents, never orbiting. */
const CUBES: Array<{ x: number; y: number; size: number; rotate: number; far: boolean }> = [
  { x: 150, y: 92, size: 20, rotate: -10, far: true },
  { x: 424, y: 70, size: 15, rotate: -8, far: true },
  { x: 478, y: 128, size: 20, rotate: 12, far: true },
  { x: 126, y: 286, size: 19, rotate: 9, far: false },
  { x: 470, y: 300, size: 16, rotate: -12, far: false },
  { x: 308, y: 338, size: 12, rotate: 11, far: false },
];

/** Sparse ambient dots for depth, kept clear of the chassis. */
const MESH_DOTS = [
  { x: 150, y: 74, r: 1, o: 0.22 },
  { x: 236, y: 58, r: 0.9, o: 0.18 },
  { x: 452, y: 60, r: 1, o: 0.2 },
  { x: 494, y: 150, r: 0.9, o: 0.18 },
  { x: 122, y: 200, r: 0.9, o: 0.18 },
  { x: 486, y: 250, r: 0.9, o: 0.16 },
  { x: 168, y: 330, r: 1, o: 0.16 },
  { x: 392, y: 348, r: 0.9, o: 0.16 },
];

/** HeroServer's own box as a share of the scene, so the two can never drift apart. */
const SERVER_FRAME = {
  left: `${((HERO_SERVER_BOX.x - SCENE.x) / SCENE.width) * 100}%`,
  top: `${((HERO_SERVER_BOX.y - SCENE.y) / SCENE.height) * 100}%`,
  width: `${(HERO_SERVER_BOX.width / SCENE.width) * 100}%`,
  height: `${(HERO_SERVER_BOX.height / SCENE.height) * 100}%`,
};

/** Matches the hero's detached cubes: graphite faces, one lit lower edge, a single top indicator. */
function FloorCube({ x, y, size, rotate }: { x: number; y: number; size: number; rotate: number }) {
  const scale = size / 40;
  return (
    <g transform={`translate(${x} ${y}) rotate(${rotate}) scale(${scale}) translate(-20 -22)`}>
      <polygon points="20,2 38,12 20,22 2,12" fill="#1b201c" />
      <polygon points="2,12 20,22 20,42 2,32" fill="#0b0e0c" />
      <polygon points="20,22 38,12 38,32 20,42" fill="#070908" />
      <path d="M2,12 L20,2 L38,12" fill="none" stroke="#ffffff" strokeOpacity="0.09" strokeWidth="0.9" vectorEffect="non-scaling-stroke" />
      <path d="M2,32 L20,42 L38,32" fill="none" stroke="#b7ff00" strokeOpacity="0.22" strokeWidth="1" vectorEffect="non-scaling-stroke" />
      <circle cx="20" cy="12" r="1.5" fill="#b7ff00" fillOpacity="0.22" />
    </g>
  );
}

/**
 * One orbit ring. The base is a full, smooth ellipse. The nodes are placed on a nested group that
 * rotates in ground-plane space (translate to centre → squash → rotate), so each node glides
 * cleanly around the true ellipse. The whole ring group is clipped to one half so the far side
 * passes behind the server.
 */
function OrbitRing({ orbit, glow }: { orbit: Orbit; glow: string }) {
  const { radius } = orbit;
  const rx = radius;
  const ry = round(radius * SQUASH);

  return (
    <g>
      {/* Base track — a complete, smooth ellipse. */}
      <ellipse
        cx={CENTER.cx}
        cy={CENTER.cy}
        rx={rx}
        ry={ry}
        fill="none"
        stroke="#7bd000"
        strokeOpacity={orbit.trackOpacity * 0.5}
        strokeWidth="1"
        vectorEffect="non-scaling-stroke"
      />
      {/* Brighter front-of-ring highlight (lower half faces the viewer). */}
      <path
        d={`M ${CENTER.cx - rx} ${CENTER.cy} A ${rx} ${ry} 0 0 0 ${CENTER.cx + rx} ${CENTER.cy}`}
        fill="none"
        stroke="#b7ff00"
        strokeOpacity={orbit.trackOpacity}
        strokeWidth="1.3"
        strokeLinecap="round"
        vectorEffect="non-scaling-stroke"
        filter={glow}
      />

      {/* Gliding nodes. Each rides a nested group that maps the ground plane to screen space:
          translate to centre → squash Y → rotate (the animated part) → the node sits on a plain
          circle of `radius`. Rotating inside the squash keeps the node exactly on the ellipse. */}
      {orbit.nodes.map((node) => {
        const size = node.tier === 'primary' ? 3 : 2.1;
        // Node position on the unsquashed circle at its start angle; the group squash flattens it.
        const cos = round(Math.cos(node.a * RAD));
        const sin = round(Math.sin(node.a * RAD));
        const px = round(radius * cos);
        const py = round(radius * sin);
        return (
          <g
            key={`${orbit.id}-${node.a}`}
            className={cn('infra-orbit', orbit.reverse && 'infra-orbit-reverse')}
            style={{ '--infra-orbit-duration': orbit.duration } as React.CSSProperties}
          >
            {/* ground-plane frame: centre + squash. The animated rotate() is applied by CSS. */}
            <g transform={`translate(${CENTER.cx} ${CENTER.cy}) scale(1 ${SQUASH})`}>
              <g className="infra-orbit-spin">
                {/* Counter-squash the node itself so it stays a round dot, not a flat sliver.
                    No blur filter here: these nodes are continuously rotated by CSS, and a
                    filtered subtree re-rasterizes every frame. A soft halo circle gives the
                    glow look on the GPU compositor instead. */}
                <g transform={`translate(${px} ${py}) scale(1 ${round(1 / SQUASH)})`}>
                  <circle r={size * 3} fill="#b7ff00" opacity="0.12" />
                  {node.tier === 'primary' && (
                    <circle r={size * 2} fill="#b7ff00" opacity="0.22" />
                  )}
                  <circle r={size} fill={node.tier === 'primary' ? '#f2ffc4' : '#b7ff00'} />
                </g>
              </g>
            </g>
          </g>
        );
      })}
    </g>
  );
}

/**
 * One depth layer. `far` draws the ambient light, cubes behind the server, and the back halves of
 * the orbit rings; the near layer draws the front halves and the near cubes. The server sits
 * between the two layers, so orbits pass behind it and re-emerge in front.
 */
function SceneLayer({ far }: { far: boolean }) {
  const uid = useId().replace(/:/g, '');
  const ref = (name: string) => `${name}-${uid}`;
  const url = (name: string) => `url(#${ref(name)})`;

  return (
    <svg className="pointer-events-none absolute inset-0 h-full w-full" viewBox={VIEW} focusable="false">
      <defs>
        {/* Soft glow shared by the ring highlights and nodes. */}
        <filter id={ref('glow')} x="-120%" y="-120%" width="340%" height="340%">
          <feGaussianBlur stdDeviation="1.4" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>

        {/* Fades ring extremes so they melt into the panel instead of hitting the frame. */}
        <radialGradient
          id={ref('fade')}
          gradientUnits="userSpaceOnUse"
          cx={CENTER.cx}
          cy={CENTER.cy}
          r="252"
          gradientTransform={`translate(${CENTER.cx} ${CENTER.cy}) scale(1 ${SQUASH}) translate(${-CENTER.cx} ${-CENTER.cy})`}
        >
          <stop offset="0.5" stopColor="#fff" stopOpacity="1" />
          <stop offset="0.82" stopColor="#fff" stopOpacity="0.7" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </radialGradient>
        <mask id={ref('fadeMask')} maskUnits="userSpaceOnUse" {...SCENE}>
          <rect {...SCENE} fill={url('fade')} />
        </mask>

        {/* Clip to one half of the ground plane, split at the pedestal centre. */}
        <clipPath id={ref('half')} clipPathUnits="userSpaceOnUse">
          {far ? (
            <rect x={SCENE.x} y={SCENE.y} width={SCENE.width} height={CENTER.cy - SCENE.y} />
          ) : (
            <rect x={SCENE.x} y={CENTER.cy} width={SCENE.width} height={SCENE.y + SCENE.height - CENTER.cy} />
          )}
        </clipPath>

        {far && (
          <radialGradient id={ref('floorLight')}>
            <stop offset="0" stopColor="#b7ff00" stopOpacity="0.12" />
            <stop offset="0.55" stopColor="#5d8500" stopOpacity="0.05" />
            <stop offset="1" stopColor="#5d8500" stopOpacity="0" />
          </radialGradient>
        )}
      </defs>

      {far && (
        <>
          {/* Ambient floor light beneath the appliance. */}
          <ellipse cx={CENTER.cx} cy={CENTER.cy + 12} rx={224} ry={72} fill={url('floorLight')} />

          {/* Ambient dots for depth. */}
          {MESH_DOTS.map((dot) => (
            <circle key={`${dot.x}:${dot.y}`} cx={dot.x} cy={dot.y} r={dot.r} fill="#a8f000" fillOpacity={dot.o} />
          ))}

          {CUBES.filter((c) => c.far).map((c) => (
            <FloorCube key={`${c.x}:${c.y}`} {...c} />
          ))}
        </>
      )}

      {/* Ring halves — clipped to this layer's side of the pedestal, faded at the extremes. */}
      <g mask={url('fadeMask')}>
        <g clipPath={url('half')}>
          {ORBITS.map((orbit) => (
            <OrbitRing key={orbit.id} orbit={orbit} glow={url('glow')} />
          ))}
        </g>
      </g>

      {!far && CUBES.filter((c) => !c.far).map((c) => <FloorCube key={`${c.x}:${c.y}`} {...c} />)}
    </svg>
  );
}

/**
 * Memoized: this scene is a large, purely-decorative SVG tree with continuous CSS
 * animations. Its only prop is `className`, so it never needs to re-render when the
 * parent (BuiltForDevelopers) re-renders on its 250ms deployment ticker. Memoizing
 * stops the whole SVG subtree from reconciling 4× per second.
 */
export const InfrastructureScene = memo(function InfrastructureScene({
  className,
}: {
  className?: string;
}) {
  return (
    <div
      data-scene="infra"
      aria-hidden="true"
      className={cn('infra-scene relative w-full', className)}
      style={{ aspectRatio: `${SCENE.width} / ${SCENE.height}` }}
    >
      <SceneLayer far />

      {/* The appliance sits between the two ring layers, so orbits pass behind and in front of it. */}
      <div className="pointer-events-none absolute" style={SERVER_FRAME}>
        <HeroServer />
      </div>

      <SceneLayer far={false} />
    </div>
  );
});
