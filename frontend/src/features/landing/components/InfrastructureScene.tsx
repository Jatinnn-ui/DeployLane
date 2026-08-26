import { useId } from 'react';
import { cn } from '@/lib/utils';
import { HERO_SERVER_BOX, HeroServer } from './HeroServer';

/**
 * Lower-section infrastructure scene.
 *
 * This is deliberately NOT a second appliance design. It renders the hero's `HeroServer` — the
 * same chassis, pedestal, seam, hardware and materials — and only changes what surrounds it, so
 * the two sections read as one physical product placed in a different set-up.
 *
 * COORDINATES - the scene is authored in HeroServer's own units, and `HERO_SERVER_BOX` is mapped
 * back into it as an exact sub-rectangle. The circular system is then authored one level deeper, in
 * an UNSQUASHED floor space: `FLOOR_TRANSFORM` places the origin at the pedestal's real footprint
 * centre and applies its foreshortening, so inside it a plain circle of radius r is a real circle
 * lying on the floor. That is what makes rotation honest — spinning a group in floor space is a
 * rotation about the vertical axis, whereas rotating an ellipse in screen space would tilt it.
 *
 * Two consequences of that space, both handled deliberately:
 *   strokes use `non-scaling-stroke`, or the anisotropic squash would thin every wire at the front
 *   and back of its arc;
 *   node discs are counter-scaled, so their position is foreshortened but their shape is not.
 *
 * DEPTH - three layers, not one flat drawing:
 *   mesh, technical marks, far ring halves and far cubes   (behind the appliance)
 *   the appliance itself
 *   near ring halves, near nodes and near cubes            (in front of it)
 * Each rotating layer is rendered twice and clipped to one half of the floor, so an arc travels
 * around, disappears behind the pedestal and reappears on the far side. The half-plane split is
 * fixed in floor space and does not rotate with the arcs, which is what keeps the occlusion
 * correct at every angle.
 */

/**
 * Scene frame, in HeroServer units. Taller than the appliance needs: the left column is roughly
 * twice as tall as it is wide, so the extra height is where the background depth lives. Width is
 * what fixes the appliance's on-screen size, and it is deliberately only a little wider than
 * `HERO_SERVER_BOX` so the chassis stays the dominant object.
 */
const SCENE = { x: 107, y: 46, width: 396, height: 316 };
const VIEW = `${SCENE.x} ${SCENE.y} ${SCENE.width} ${SCENE.height}`;

/**
 * The floor plane, taken from the pedestal rather than assumed: HeroServer's foundation disc is
 * rx 146 / ry 39 centred at x 304, and its underside sits at y 240.
 */
const FLOOR = { cx: 304, cy: 240, squash: 0.267 };
const FLOOR_TRANSFORM = `translate(${FLOOR.cx} ${FLOOR.cy}) scale(1 ${FLOOR.squash})`;

const RAD = Math.PI / 180;
const round = (n: number) => Math.round(n * 100) / 100;

/** Floor-space point at polar radius/angle. 90deg is toward the viewer, 270deg away from it. */
function polar(radius: number, degrees: number): [number, number] {
  const t = degrees * RAD;
  return [round(radius * Math.cos(t)), round(radius * Math.sin(t))];
}

/** A circular arc in floor space, swept the short way round unless it exceeds a half turn. */
function floorArc(radius: number, from: number, to: number): string {
  const [x0, y0] = polar(radius, from);
  const [x1, y1] = polar(radius, to);
  const sweep = (((to - from) % 360) + 360) % 360;
  return `M ${x0} ${y0} A ${radius} ${radius} 0 ${sweep > 180 ? 1 : 0} 1 ${x1} ${y1}`;
}

function floorRadial(degrees: number, from: number, to: number): string {
  const [x0, y0] = polar(from, degrees);
  const [x1, y1] = polar(to, degrees);
  return `M ${x0} ${y0} L ${x1} ${y1}`;
}

/**
 * A point on a ring, drawn as a near-zero-length round-capped stroke rather than as a circle.
 *
 * A `<circle>` here would be wrong: the floor squash flattens it, and the layer's rotation sits
 * between the squash and any counter-scale, so no static correction can cancel it - the disc would
 * smear differently at every angle. A round cap under `non-scaling-stroke` is resolved in viewport
 * units, so it stays a true screen-space circle however the group is transformed, and the marker
 * still travels with its ring.
 */
function floorDot(radius: number, degrees: number): string {
  const [x0, y0] = polar(radius, degrees - 0.02);
  const [x1, y1] = polar(radius, degrees + 0.02);
  return `M ${x0} ${y0} L ${x1} ${y1}`;
}

type Tier = 'primary' | 'secondary' | 'tiny';
/** Stroke widths, so these are node diameters in viewport units. */
const NODE_WIDTH: Record<Tier, number> = { primary: 5, secondary: 3.3, tiny: 2 };

interface RingGlow {
  glow: string;
  bloom: string;
}

interface RingLayer {
  id: string;
  r: number;
  /** Counter-clockwise layers get the mirrored keyframes; the mix is what creates the parallax. */
  reverse?: boolean;
  duration: string;
  track: { width: number; opacity: number };
  segments: Array<{ from: number; to: number; width: number; opacity: number; bloom?: boolean }>;
  /** Short radial stubs: the technical markings that make a rotation legible at all. */
  ticks: { at: number[]; length: number; inward: boolean; opacity: number };
  nodes: Array<{ a: number; tier: Tier }>;
}

/**
 * Three incomplete rings outside the pedestal's 146-unit footprint. Only a few arcs of each are
 * lit, the tracks between them stay dark, and the three rates are close enough that the system
 * reads as one slow mechanism rather than as three competing spinners.
 */
const RINGS: RingLayer[] = [
  {
    id: 'c',
    r: 164,
    duration: '18s',
    track: { width: 0.75, opacity: 0.16 },
    segments: [
      { from: -8, to: 62, width: 0.95, opacity: 0.5, bloom: true },
      { from: 96, to: 132, width: 0.9, opacity: 0.3 },
      { from: 186, to: 244, width: 0.85, opacity: 0.22 },
      { from: 296, to: 324, width: 0.9, opacity: 0.38, bloom: true },
    ],
    ticks: { at: [30, 118, 210, 300], length: 9, inward: true, opacity: 0.2 },
    nodes: [
      { a: 62, tier: 'primary' },
      { a: 132, tier: 'secondary' },
      { a: 244, tier: 'tiny' },
      { a: 296, tier: 'secondary' },
      { a: -8, tier: 'tiny' },
    ],
  },
  {
    id: 'b',
    r: 180,
    reverse: true,
    duration: '23s',
    track: { width: 0.7, opacity: 0.12 },
    segments: [
      { from: 20, to: 74, width: 0.8, opacity: 0.25 },
      { from: 110, to: 158, width: 0.78, opacity: 0.17 },
      { from: 200, to: 232, width: 0.78, opacity: 0.29, bloom: true },
      { from: 268, to: 340, width: 0.72, opacity: 0.15 },
    ],
    ticks: { at: [46, 140, 216, 304], length: 8, inward: false, opacity: 0.15 },
    nodes: [
      { a: 74, tier: 'secondary' },
      { a: 110, tier: 'tiny' },
      { a: 200, tier: 'primary' },
      { a: 232, tier: 'tiny' },
      { a: 340, tier: 'tiny' },
    ],
  },
  {
    id: 'a',
    r: 196,
    duration: '28s',
    track: { width: 0.6, opacity: 0.09 },
    segments: [
      { from: 8, to: 40, width: 0.7, opacity: 0.17 },
      { from: 70, to: 104, width: 0.68, opacity: 0.12 },
      { from: 148, to: 172, width: 0.68, opacity: 0.22, bloom: true },
      { from: 214, to: 262, width: 0.64, opacity: 0.13 },
      { from: 310, to: 336, width: 0.64, opacity: 0.15 },
    ],
    ticks: { at: [24, 88, 160, 238, 324], length: 7, inward: true, opacity: 0.12 },
    nodes: [
      { a: 40, tier: 'tiny' },
      { a: 104, tier: 'tiny' },
      { a: 160, tier: 'secondary' },
      { a: 262, tier: 'tiny' },
      { a: 336, tier: 'primary' },
    ],
  },
];

/** Static floor detail: the pedestal's own footprint outline, plus a few settled marks. */
const FOOTPRINT_R = 152;
const FLOOR_MARKS: Array<{ r: number; a: number; size: number; opacity: number }> = [
  { r: 158, a: 44, size: 0.9, opacity: 0.3 },
  { r: 172, a: 128, size: 0.7, opacity: 0.22 },
  { r: 188, a: 196, size: 0.8, opacity: 0.24 },
  { r: 156, a: 268, size: 0.7, opacity: 0.2 },
  { r: 190, a: 348, size: 0.9, opacity: 0.26 },
];

/**
 * Small dark geometry at four apparent depths, asymmetrically placed. These never orbit: a cube
 * swinging around the appliance would turn the scene into a solar system.
 */
const CUBES: Array<{ x: number; y: number; size: number; rotate: number; far: boolean }> = [
  { x: 152, y: 96, size: 21, rotate: -10, far: true },
  { x: 268, y: 68, size: 11, rotate: 7, far: true },
  { x: 420, y: 66, size: 15, rotate: -8, far: true },
  { x: 476, y: 124, size: 21, rotate: 12, far: true },
  { x: 124, y: 178, size: 13, rotate: -6, far: true },
  { x: 132, y: 284, size: 20, rotate: 9, far: false },
  { x: 470, y: 300, size: 17, rotate: -12, far: false },
  { x: 306, y: 336, size: 12, rotate: 11, far: false },
];

/* Background mesh: the hero plane's two crossing diagonal families, several steps quieter. This
   frame is roughly half the hero's width, so the hero's pitch and opacity would read as wallpaper. */
const PITCH = 32;
const RUN = 240;
const MESH_LINES = Math.ceil((SCENE.width + RUN) / PITCH) + 1;
const MESH_A = Array.from({ length: MESH_LINES }, (_, i) => SCENE.x - RUN + i * PITCH);
const MESH_B = Array.from({ length: MESH_LINES }, (_, i) => SCENE.x + i * PITCH);
const MESH_ROWS = Array.from({ length: Math.ceil(SCENE.height / 56) }, (_, i) => SCENE.y + 24 + i * 56);

/** Sparse intersection points, kept clear of the chassis so they never read as surface dirt. */
const MESH_DOTS = [
  { x: 196, y: 62, r: 0.9, o: 0.18 },
  { x: 132, y: 96, r: 1, o: 0.24 },
  { x: 232, y: 56, r: 1, o: 0.2 },
  { x: 344, y: 52, r: 0.8, o: 0.16 },
  { x: 446, y: 58, r: 1, o: 0.18 },
  { x: 488, y: 96, r: 0.8, o: 0.16 },
  { x: 176, y: 170, r: 0.8, o: 0.18 },
  { x: 494, y: 188, r: 1, o: 0.2 },
  { x: 118, y: 212, r: 0.9, o: 0.18 },
  { x: 486, y: 258, r: 0.9, o: 0.16 },
  { x: 152, y: 318, r: 1, o: 0.18 },
  { x: 216, y: 344, r: 0.8, o: 0.15 },
  { x: 344, y: 356, r: 0.8, o: 0.13 },
  { x: 392, y: 346, r: 1, o: 0.16 },
  { x: 462, y: 340, r: 0.9, o: 0.14 },
];

/** Faint survey marks: they add technical texture at almost no brightness. */
const CROSS_MARKS = [
  { x: 340, y: 74, s: 3 },
  { x: 178, y: 122, s: 3 },
  { x: 146, y: 148, s: 4 },
  { x: 452, y: 176, s: 4 },
  { x: 196, y: 240, s: 3 },
  { x: 494, y: 226, s: 4 },
  { x: 444, y: 330, s: 4 },
  { x: 258, y: 352, s: 3 },
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
  // Authored in a 40x44 box and scaled, so every cube keeps the hero's proportions.
  const scale = size / 40;

  return (
    <g transform={`translate(${x} ${y}) rotate(${rotate}) scale(${scale}) translate(-20 -22)`}>
      <polygon points="20,2 38,12 20,22 2,12" fill="#1b201c" />
      <polygon points="2,12 20,22 20,42 2,32" fill="#0b0e0c" />
      <polygon points="20,22 38,12 38,32 20,42" fill="#070908" />
      <path
        d="M2,12 L20,2 L38,12"
        fill="none"
        stroke="#ffffff"
        strokeOpacity="0.09"
        strokeWidth="0.9"
        vectorEffect="non-scaling-stroke"
      />
      <path
        d="M2,32 L20,42 L38,32"
        fill="none"
        stroke="#b7ff00"
        strokeOpacity="0.22"
        strokeWidth="1"
        vectorEffect="non-scaling-stroke"
      />
      <circle cx="20" cy="12" r="1.5" fill="#b7ff00" fillOpacity="0.22" />
    </g>
  );
}

/**
 * A node riding on a ring. Primaries get a blurred bloom and a pale core; everything else is a
 * plain point. The bloom is a filtered stroke rather than a wide translucent disc, because a flat
 * disc at this size reads as a blob with a visible edge instead of as light.
 */
function RingNode({
  r,
  a,
  tier,
  glow,
  bloom,
}: {
  r: number;
  a: number;
  tier: Tier;
  glow: string;
  bloom: string;
}) {
  const d = floorDot(r, a);
  const width = NODE_WIDTH[tier];
  const dot = (w: number, stroke: string, opacity: number, filter?: string) => (
    <path
      d={d}
      fill="none"
      stroke={stroke}
      strokeOpacity={opacity}
      strokeWidth={w}
      strokeLinecap="round"
      vectorEffect="non-scaling-stroke"
      filter={filter}
    />
  );

  if (tier === 'tiny') return dot(width, '#9ad400', 0.62);
  if (tier === 'secondary') return dot(width, '#b7ff00', 0.85, glow);

  return (
    <g>
      {dot(width * 1.3, '#b7ff00', 0.42, bloom)}
      {dot(width * 0.68, '#b7ff00', 0.95, glow)}
      {dot(width * 0.38, '#f2ffd0', 1)}
    </g>
  );
}

/**
 * One rotating ring layer. The leading circle is an invisible bound: it forces the group's object
 * bounding box to be symmetric about the floor origin, which is what makes `transform-box:
 * fill-box; transform-origin: center` resolve to the true centre of rotation rather than drifting
 * with wherever the lit arcs and nodes happen to sit.
 */
function Ring({ layer, paint }: { layer: RingLayer; paint: RingGlow }) {
  const { r, ticks } = layer;
  const { glow, bloom } = paint;

  return (
    <g
      className={cn('infra-ring infra-ring-detail', layer.reverse && 'infra-ring-reverse')}
      style={{ '--infra-ring-duration': layer.duration } as React.CSSProperties}
    >
      <circle r={r + 40} fill="none" />

      <circle
        r={r}
        fill="none"
        stroke="#5f7d00"
        strokeOpacity={layer.track.opacity}
        strokeWidth={layer.track.width}
        vectorEffect="non-scaling-stroke"
      />

      {ticks.at.map((a) => (
        <path
          key={`t${a}`}
          d={floorRadial(a, r, ticks.inward ? r - ticks.length : r + ticks.length)}
          fill="none"
          stroke="#8fbf00"
          strokeOpacity={ticks.opacity}
          strokeWidth="0.7"
          vectorEffect="non-scaling-stroke"
        />
      ))}

      {layer.segments.map((segment) => (
        <path
          key={`s${segment.from}`}
          d={floorArc(r, segment.from, segment.to)}
          fill="none"
          stroke="#b7ff00"
          strokeOpacity={segment.opacity}
          strokeWidth={segment.width}
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
          filter={segment.bloom ? glow : undefined}
        />
      ))}

      {layer.nodes.map((node) => (
        <RingNode key={`n${node.a}`} r={r} a={node.a} tier={node.tier} glow={glow} bloom={bloom} />
      ))}
    </g>
  );
}

/**
 * One depth layer of the scene. The far layer also carries the mesh, the survey marks and the
 * ambient floor light; both layers share the scene viewBox, so they register exactly with the
 * appliance between them.
 */
function SceneLayer({ far }: { far: boolean }) {
  const uid = useId().replace(/:/g, '');
  const ref = (name: string) => `${name}-${uid}`;
  const url = (name: string) => `url(#${ref(name)})`;

  return (
    <svg
      className="pointer-events-none absolute inset-0 h-full w-full"
      viewBox={VIEW}
      focusable="false"
    >
      <defs>
        {/* Falls off along the floor plane, so ring extremes fade instead of hitting the frame. */}
        <radialGradient
          id={ref('fade')}
          gradientUnits="userSpaceOnUse"
          cx={FLOOR.cx}
          cy={FLOOR.cy}
          r="286"
          gradientTransform={`${FLOOR_TRANSFORM} translate(${-FLOOR.cx} ${-FLOOR.cy})`}
        >
          <stop offset="0.45" stopColor="#ffffff" stopOpacity="1" />
          <stop offset="0.72" stopColor="#ffffff" stopOpacity="0.84" />
          <stop offset="1" stopColor="#ffffff" stopOpacity="0" />
        </radialGradient>
        <mask id={ref('fadeMask')} maskUnits="userSpaceOnUse" {...SCENE}>
          <rect {...SCENE} fill={url('fade')} />
        </mask>

        {/* Half of the floor plane, in floor space. Fixed while the arcs above it turn. */}
        <clipPath id={ref('half')} clipPathUnits="userSpaceOnUse">
          <rect x={-520} y={far ? -520 : 0} width={1040} height={far ? 520.8 : 520} />
        </clipPath>

        {/* Sharp core, tight halo - the same restraint the appliance itself uses. */}
        <filter id={ref('neon')} x="-200%" y="-200%" width="500%" height="500%">
          <feGaussianBlur stdDeviation="1.1" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        {/* Blur only, no core: this is the falloff under an active node, not the node itself. */}
        <filter id={ref('nodeBloom')} x="-300%" y="-300%" width="700%" height="700%">
          <feGaussianBlur stdDeviation="2.3" />
        </filter>

        {far && (
          <radialGradient id={ref('mesh')} cx="0.5" cy="0.54" r="0.52">
            <stop offset="0.18" stopColor="#ffffff" stopOpacity="1" />
            <stop offset="0.56" stopColor="#ffffff" stopOpacity="0.38" />
            <stop offset="0.95" stopColor="#ffffff" stopOpacity="0" />
          </radialGradient>
        )}
        {far && (
          <mask id={ref('meshMask')} maskUnits="userSpaceOnUse" {...SCENE}>
            <rect {...SCENE} fill={url('mesh')} />
          </mask>
        )}
        {far && (
          <radialGradient id={ref('floorLight')}>
            <stop offset="0" stopColor="#b7ff00" stopOpacity="0.1" />
            <stop offset="0.5" stopColor="#5d8500" stopOpacity="0.04" />
            <stop offset="1" stopColor="#5d8500" stopOpacity="0" />
          </radialGradient>
        )}
        {far && (
          <radialGradient id={ref('separation')}>
            <stop offset="0" stopColor="#b7ff00" stopOpacity="0.07" />
            <stop offset="1" stopColor="#b7ff00" stopOpacity="0" />
          </radialGradient>
        )}
      </defs>

      {far && (
        <>
          {/* Technical mesh: ultra-thin, very low opacity, gone well before the frame. */}
          <g mask={url('meshMask')} opacity="0.055">
            {MESH_A.map((x) => (
              <line
                key={`a${x}`}
                x1={x}
                y1={SCENE.y - 20}
                x2={x + RUN}
                y2={SCENE.y + SCENE.height + 20}
                stroke="#7fbf00"
                strokeWidth="0.5"
              />
            ))}
            {MESH_B.map((x) => (
              <line
                key={`b${x}`}
                x1={x}
                y1={SCENE.y - 20}
                x2={x - RUN}
                y2={SCENE.y + SCENE.height + 20}
                stroke="#7fbf00"
                strokeWidth="0.5"
              />
            ))}
            {MESH_ROWS.map((y) => (
              <line
                key={`r${y}`}
                x1={SCENE.x - 20}
                y1={y}
                x2={SCENE.x + SCENE.width + 20}
                y2={y}
                stroke="#7fbf00"
                strokeWidth="0.35"
              />
            ))}
          </g>
          <g mask={url('meshMask')}>
            {CROSS_MARKS.map((mark) => (
              <path
                key={`x${mark.x}`}
                d={`M ${mark.x - mark.s} ${mark.y} H ${mark.x + mark.s} M ${mark.x} ${mark.y - mark.s} V ${mark.y + mark.s}`}
                stroke="#8fbf00"
                strokeOpacity="0.16"
                strokeWidth="0.5"
              />
            ))}
            {MESH_DOTS.map((dot) => (
              <circle
                key={`${dot.x}:${dot.y}`}
                cx={dot.x}
                cy={dot.y}
                r={dot.r}
                fill="#a8f000"
                fillOpacity={dot.o}
              />
            ))}
          </g>

          {/* Two-stage ambient light: a wide floor wash, plus a tighter pool that lifts the
              appliance off the black without becoming a visible blob. */}
          <ellipse cx={FLOOR.cx} cy={FLOOR.cy + 14} rx={214} ry={62} fill={url('floorLight')} />
          <ellipse cx={FLOOR.cx} cy={FLOOR.cy - 26} rx={132} ry={96} fill={url('separation')} />

          {CUBES.filter((cube) => cube.far).map((cube) => (
            <FloorCube key={`${cube.x}:${cube.y}`} {...cube} />
          ))}
        </>
      )}

      <g mask={url('fadeMask')}>
        <g transform={FLOOR_TRANSFORM}>
          <g clipPath={url('half')}>
            {/* Static: the pedestal's footprint and settled floor marks do not turn. */}
            <circle
              r={FOOTPRINT_R}
              fill="none"
              stroke="#5f7d00"
              strokeOpacity="0.12"
              strokeWidth="0.6"
              vectorEffect="non-scaling-stroke"
            />
            {FLOOR_MARKS.map((mark) => (
              <path
                key={`m${mark.a}`}
                d={floorDot(mark.r, mark.a)}
                fill="none"
                stroke="#8fbf00"
                strokeOpacity={mark.opacity}
                strokeWidth={mark.size * 2}
                strokeLinecap="round"
                vectorEffect="non-scaling-stroke"
              />
            ))}

            {RINGS.map((layer) => (
              <Ring
                key={layer.id}
                layer={layer}
                paint={{ glow: url('neon'), bloom: url('nodeBloom') }}
              />
            ))}
          </g>
        </g>
      </g>

      {!far &&
        CUBES.filter((cube) => !cube.far).map((cube) => (
          <FloorCube key={`${cube.x}:${cube.y}`} {...cube} />
        ))}
    </svg>
  );
}

export function InfrastructureScene({ className }: { className?: string }) {
  return (
    // Hover anywhere in the scene freezes the ring system; every child is pointer-transparent, so
    // this container is the only hit target and the cursor stays default.
    <div
      data-scene="infra"
      aria-hidden="true"
      className={cn('infra-scene relative w-full', className)}
      style={{ aspectRatio: `${SCENE.width} / ${SCENE.height}` }}
    >
      <SceneLayer far />

      {/* The appliance: the hero's component, mapped to its exact sub-rectangle. Deliberately
          outside every rotating group - the chassis, its symbols, its pedestal, its shadow and
          its lighting must all stay locked while the network turns around them. */}
      <div className="pointer-events-none absolute" style={SERVER_FRAME}>
        <HeroServer />
      </div>

      <SceneLayer far={false} />
    </div>
  );
}
