import { useId } from 'react';
import { cn } from '@/lib/utils';
import { roundedPath, type Point } from './rounded-path';

/**
 * The hero deployment appliance, reconstructed from the close-up product reference.
 *
 * PROJECTION - asymmetric three-quarter, not symmetric isometric: the front-left face is broad,
 * the right face narrower, and the two horizontal directions carry different slopes. That is what
 * makes the lid read as a rectangle in perspective; a symmetric splay reads as a diamond.
 *
 *   p = (133, 22)   front face width  - slopes DOWN to the right
 *   q = (92, -46)   depth             - slopes UP to the right
 *   drop = 94       body height
 *
 * Silhouette lands at 225x162 (aspect 1.39), matching the reference's ~222x163. The measured lid
 * extent includes the lid slab's own edge thickness, so the top SURFACE is shallower than the
 * whole lid - getting that wrong makes the base parallelogram too tall, which in turn forces a
 * fat round pedestal. The pedestal ratio (0.27) is taken from the reference, not derived from an
 * assumed square footprint: this chassis is wider than it is deep.
 *
 * CONSTRUCTION - stacked physical parts, never one rounded box:
 *   lid slab (top surface + visible edge thickness)
 *   -> recessed seam channel carrying a thin bright core
 *   -> lower chassis holding the deep-set panels
 *   -> bottom frame
 * with thick sculpted corner columns that the panels sit BETWEEN. Every surface is decorated
 * through its own unit-square matrix, so detail inherits the correct perspective.
 *
 * The object must still read as premium with all green light switched off, which is why the
 * graphite levels and edge speculars do the structural work rather than the glow.
 */

/** Front-left-top corner of the body; everything else derives from it. */
const A: Point = [190, 134];
const P: Point = [133, 22];
const Q: Point = [92, -46];
const DROP = 94;

const add = (a: Point, b: Point): Point => [a[0] + b[0], a[1] + b[1]];
const down = (p: Point): Point => [p[0], p[1] + DROP];

const B = add(A, P); // near vertical corner, top
const C = add(B, Q); // right corner, top
const D = add(A, Q); // back corner, top
const Ab = down(A);
const Bb = down(B);
const Cb = down(C);

const TOP = [A, D, C, B];
const FACE_F = [A, B, Bb, Ab];
const FACE_R = [B, C, Cb, Bb];

const TOP_PATH = roundedPath(TOP, 19);
const FACE_F_PATH = roundedPath(FACE_F, 14);
const FACE_R_PATH = roundedPath(FACE_R, 12);

/** Unit-square mappings so all decoration inherits each surface's perspective. */
const M_TOP = `matrix(${P[0]} ${P[1]} ${Q[0]} ${Q[1]} ${A[0]} ${A[1]})`;
const M_F = `matrix(${P[0]} ${P[1]} 0 ${DROP} ${A[0]} ${A[1]})`;
const M_R = `matrix(${Q[0]} ${Q[1]} 0 ${DROP} ${B[0]} ${B[1]})`;

/**
/**
 * The real DeployLane icon mark, transcribed verbatim from public/brand/deploylane-icon.svg:
 * three graded chevrons (dark → bright green) plus the three motion lines. It is authored in that
 * file's own 32x32 grid, then `brandMark()` maps it into the face's unit-square (0..1) space and
 * scales it, so it inherits each face's perspective shear exactly like the rest of the decoration.
 */
type MarkStroke = { d: string; stroke: string; width: number };

/** Icon geometry on its native 32-unit grid — kept identical to the shipped SVG. */
const ICON_GRID = 32;
const ICON_STROKES: MarkStroke[] = [
  { d: 'M5 11 L12 16 L5 21',      stroke: '#4d7500', width: 2.2 }, // back arrow
  { d: 'M9 9.5 L17.5 16 L9 22.5', stroke: '#7ab800', width: 2.6 }, // middle arrow
  { d: 'M13 8 L23 16 L13 24',     stroke: '#a8f000', width: 3.0 }, // front arrow
  { d: 'M2 13.5 L6 13.5',         stroke: 'rgba(168,240,0,0.45)', width: 1.2 }, // motion
  { d: 'M2.5 16 L7.5 16',         stroke: 'rgba(168,240,0,0.5)',  width: 1.2 },
  { d: 'M2 18.5 L6 18.5',         stroke: 'rgba(168,240,0,0.45)', width: 1.2 },
];

/**
 * Maps the 32-grid icon into unit-square space. `cx`/`cy` is where the icon's own centre (16,16)
 * lands, `s` is the on-face size in unit-square units. The mark's own strokes stay proportional
 * because they are drawn inside a nested group transform.
 */
function brandMark(cx: number, cy: number, s: number) {
  const scale = s / ICON_GRID;
  // translate icon centre (16,16) to (cx,cy), then scale the whole 32-grid down.
  return `translate(${cx} ${cy}) scale(${scale}) translate(${-ICON_GRID / 2} ${-ICON_GRID / 2})`;
}

/* Front face: mark sits in the recessed panel. */
const MARK_F_TRANSFORM = brandMark(0.5, 0.6, 0.62);
/* Top face: mark centred on the inset plate, a touch smaller. */
const MARK_TOP_TRANSFORM = brandMark(0.5, 0.5, 0.52);

/* Body bands, as fractions of the body height. The lid's visible edge thickness comes first,
   which is what pushes the seam far enough down to read as embedded rather than as an outline. */
const LID_EDGE = 0.1;
const SEAM_TOP = 0.24;
const SEAM_CORE = 0.256;
const SEAM_CORE_H = 0.019;
const SEAM_BOTTOM = 0.3;
const PANEL_TOP = 0.345;
const PANEL_BOTTOM = 0.86;
/** Corner columns, as a fraction of each face's width - heavy, not rails. */
const PILLAR = 0.165;

/**
 * Pedestal: tight around the chassis and clearly layered. Its top disc only just contains the
 * chassis footprint, so the platform hugs the object instead of floating out as a wide oval.
 */
const BASE_X = 304;
const PLATE = { cy: 216, rx: 130, ry: 35 };
const RING = { cy: 222, rx: 138, ry: 37 };
const DISC = { cy: 228, rx: 146, ry: 39, wall: 12 };

const GROOVES = [0, 1, 2, 3];

/** The SVG spans scene x 110-500 / y 55-330, leaving room for bloom and ground light. */
export const HERO_SERVER_BOX = { x: 110, y: 55, width: 390, height: 275 };

const frontArc = (o: { cy: number; rx: number; ry: number }) =>
  `M ${BASE_X - o.rx} ${o.cy} A ${o.rx} ${o.ry} 0 0 0 ${BASE_X + o.rx} ${o.cy}`;

export function HeroServer({ className }: { className?: string }) {
  const uid = useId().replace(/:/g, '');
  const ref = (name: string) => `${name}-${uid}`;
  const url = (name: string) => `url(#${ref(name)})`;

  return (
    <svg
      viewBox={`${HERO_SERVER_BOX.x} ${HERO_SERVER_BOX.y} ${HERO_SERVER_BOX.width} ${HERO_SERVER_BOX.height}`}
      className={cn('hero-server h-full w-full', className)}
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        {/* --- Graphite levels: lid lightest, recesses near-black --- */}
        <linearGradient id={ref('lid')} x1="0.1" y1="0" x2="0.8" y2="1">
          <stop offset="0" stopColor="#333a34" />
          <stop offset="0.4" stopColor="#222823" />
          <stop offset="1" stopColor="#141a16" />
        </linearGradient>
        <linearGradient id={ref('lidEdge')} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#2f3630" />
          <stop offset="0.55" stopColor="#1e241f" />
          <stop offset="1" stopColor="#121714" />
        </linearGradient>
        <linearGradient id={ref('lidPlate')} x1="0.1" y1="0" x2="0.8" y2="1">
          <stop offset="0" stopColor="#171d19" />
          <stop offset="0.5" stopColor="#111612" />
          <stop offset="1" stopColor="#0b0f0c" />
        </linearGradient>
        <linearGradient id={ref('frameF')} x1="0" y1="0" x2="0.25" y2="1">
          <stop offset="0" stopColor="#232a25" />
          <stop offset="0.6" stopColor="#151a17" />
          <stop offset="1" stopColor="#0a0e0b" />
        </linearGradient>
        <linearGradient id={ref('frameR')} x1="0.1" y1="0" x2="1" y2="0.85">
          <stop offset="0" stopColor="#181e1a" />
          <stop offset="1" stopColor="#070a08" />
        </linearGradient>
        {/* Five stops across the band: a sculpted, glossy round column rather than a flat rail. */}
        <linearGradient id={ref('column')} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#080b09" />
          <stop offset="0.16" stopColor="#1e241f" />
          <stop offset="0.34" stopColor="#3b423c" />
          <stop offset="0.52" stopColor="#252b26" />
          <stop offset="0.76" stopColor="#12171300" />
          <stop offset="0.76" stopColor="#121713" />
          <stop offset="1" stopColor="#060908" />
        </linearGradient>
        <linearGradient id={ref('columnR')} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#333a34" />
          <stop offset="0.28" stopColor="#1c221d" />
          <stop offset="0.7" stopColor="#0e120f" />
          <stop offset="1" stopColor="#060908" />
        </linearGradient>
        {/* Matte, genuinely deep front recess. */}
        <linearGradient id={ref('panel')} x1="0" y1="0" x2="0.35" y2="1">
          <stop offset="0" stopColor="#0a0e0b" />
          <stop offset="0.6" stopColor="#070a08" />
          <stop offset="1" stopColor="#040605" />
        </linearGradient>
        <linearGradient id={ref('spill')} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#b7ff00" stopOpacity="0.16" />
          <stop offset="1" stopColor="#b7ff00" stopOpacity="0" />
        </linearGradient>
        {/* Rim brightest across the front, gone by the ellipse extremes - not a full glowing oval. */}
        <linearGradient id={ref('rim')} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#b7ff00" stopOpacity="0" />
          <stop offset="0.18" stopColor="#b7ff00" stopOpacity="0.55" />
          <stop offset="0.5" stopColor="#c8ff16" stopOpacity="1" />
          <stop offset="0.82" stopColor="#b7ff00" stopOpacity="0.55" />
          <stop offset="1" stopColor="#b7ff00" stopOpacity="0" />
        </linearGradient>
        <linearGradient id={ref('discFill')} x1="0" y1="0" x2="0.2" y2="1">
          <stop offset="0" stopColor="#090d0b" />
          <stop offset="0.6" stopColor="#111713" />
          <stop offset="1" stopColor="#181f1a" />
        </linearGradient>
        <linearGradient id={ref('ringFill')} x1="0" y1="0" x2="0.2" y2="1">
          <stop offset="0" stopColor="#050706" />
          <stop offset="1" stopColor="#0e1310" />
        </linearGradient>
        <linearGradient id={ref('plateFill')} x1="0" y1="0" x2="0.25" y2="1">
          <stop offset="0" stopColor="#0d120f" />
          <stop offset="1" stopColor="#1c241e" />
        </linearGradient>
        <linearGradient id={ref('wall')} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#030504" />
          <stop offset="0.5" stopColor="#0d1210" />
          <stop offset="1" stopColor="#020403" />
        </linearGradient>
        <radialGradient id={ref('ground')}>
          <stop offset="0" stopColor="#b7ff00" stopOpacity="0.2" />
          <stop offset="0.45" stopColor="#5d8500" stopOpacity="0.07" />
          <stop offset="0.8" stopColor="#5d8500" stopOpacity="0" />
        </radialGradient>
        {/* Restrained bloom: sharp core, tight halo. */}
        <filter id={ref('neon')} x="-150%" y="-150%" width="400%" height="400%">
          <feGaussianBlur stdDeviation="1" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        {/* Logo mark: a tight, dimmed halo so the crisp chevrons stay clearly readable rather
            than washing out. The blurred copy is faded to ~35% before the sharp source sits on top. */}
        <filter id={ref('mark')} x="-120%" y="-120%" width="340%" height="340%">
          <feGaussianBlur stdDeviation="0.8" result="b" />
          <feComponentTransfer in="b" result="bDim">
            <feFuncA type="linear" slope="0.35" intercept="0" />
          </feComponentTransfer>
          <feMerge>
            <feMergeNode in="bDim" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <filter id={ref('soft')} x="-170%" y="-170%" width="440%" height="440%">
          <feGaussianBlur stdDeviation="5" />
        </filter>

        <clipPath id={ref('cf')}>
          <path d={FACE_F_PATH} />
        </clipPath>
        <clipPath id={ref('cr')}>
          <path d={FACE_R_PATH} />
        </clipPath>
        <clipPath id={ref('ct')}>
          <path d={TOP_PATH} />
        </clipPath>
      </defs>

      {/* Ground light under the pedestal. */}
      <ellipse className="server-ground-glow" cx={BASE_X} cy={244} rx={168} ry={50} fill={url('ground')} />

      {/* ---- Pedestal: foundation disc, recessed ring, top disc ---- */}
      <path
        d={`M ${BASE_X - DISC.rx} ${DISC.cy} A ${DISC.rx} ${DISC.ry} 0 0 0 ${BASE_X + DISC.rx} ${DISC.cy} L ${BASE_X + DISC.rx} ${DISC.cy + DISC.wall} A ${DISC.rx} ${DISC.ry} 0 0 1 ${BASE_X - DISC.rx} ${DISC.cy + DISC.wall} Z`}
        fill={url('wall')}
      />
      <ellipse cx={BASE_X} cy={DISC.cy} rx={DISC.rx} ry={DISC.ry} fill={url('discFill')} />
      <ellipse
        cx={BASE_X}
        cy={DISC.cy}
        rx={DISC.rx}
        ry={DISC.ry}
        fill="none"
        stroke="#2a312c"
        strokeOpacity="0.9"
        strokeWidth="0.9"
      />
      <g filter={url('neon')}>
        <path className="server-platform-rim" d={frontArc(DISC)} fill="none" stroke={url('rim')} strokeWidth="1.3" />
      </g>

      {/* Recessed middle ring: darker and set in, so the layering is legible. */}
      <ellipse cx={BASE_X} cy={RING.cy} rx={RING.rx} ry={RING.ry} fill={url('ringFill')} />
      <path
        d={frontArc(RING)}
        fill="none"
        stroke="#000"
        strokeOpacity="0.7"
        strokeWidth="1.6"
      />
      <ellipse
        cx={BASE_X}
        cy={RING.cy}
        rx={RING.rx}
        ry={RING.ry}
        fill="none"
        stroke="#39413b"
        strokeOpacity="0.55"
        strokeWidth="0.8"
      />
      <path d={frontArc(RING)} fill="none" stroke="#5b635c" strokeOpacity="0.4" strokeWidth="0.9" />

      <ellipse cx={BASE_X} cy={PLATE.cy} rx={PLATE.rx} ry={PLATE.ry} fill={url('plateFill')} />
      <ellipse
        cx={BASE_X}
        cy={PLATE.cy}
        rx={PLATE.rx}
        ry={PLATE.ry}
        fill="none"
        stroke="#525a54"
        strokeOpacity="0.6"
        strokeWidth="0.9"
      />
      {/* Lit front lip on each step, so the layering reads as physical thickness. */}
      <path d={frontArc(PLATE)} fill="none" stroke="#6b736c" strokeOpacity="0.5" strokeWidth="0.9" />
      <g filter={url('neon')}>
      <path className="server-platform-rim" d={frontArc(PLATE)} fill="none" stroke={url('rim')} strokeOpacity="0.3" strokeWidth="0.8" />
      </g>

      {/* Ambient occlusion, then a tight contact shadow. */}
      <ellipse cx={BASE_X} cy={220} rx={96} ry={27} fill="#000" fillOpacity="0.44" filter={url('soft')} />
      <ellipse cx={BASE_X - 4} cy={218} rx={76} ry={20} fill="#000" fillOpacity="0.46" />

      {/* ---- Body shells ---- */}
      <path d={FACE_R_PATH} fill={url('frameR')} />
      <path d={FACE_F_PATH} fill={url('frameF')} />

      {/* ---- Front face ---- */}
      <g clipPath={url('cf')}>
        <g transform={M_F}>
          {/* Lid slab edge, so the top reads as a thick chassis rather than a flat lamina. */}
          <rect x="0" y="0" width="1" height={LID_EDGE} fill={url('lidEdge')} />
          {/* Upper chassis band: separates the lid from the seam, as in the reference. */}
          <rect x="0" y={LID_EDGE} width="1" height={SEAM_TOP - LID_EDGE} fill={url('frameF')} />
          <rect x="0" y={LID_EDGE} width="1" height="0.014" fill="#000" fillOpacity="0.6" />
          {/* Seam channel. */}
          <rect x="0" y={SEAM_TOP} width="1" height={SEAM_BOTTOM - SEAM_TOP} fill="#040604" />
          <rect x="0" y={SEAM_BOTTOM} width="1" height="0.085" fill={url('spill')} />
          {/* Lower chassis and bottom frame. */}
          <rect x="0" y={PANEL_BOTTOM} width="1" height={1 - PANEL_BOTTOM} fill="#101511" />
          <rect
            x="0"
            y={PANEL_BOTTOM}
            width="1"
            height="0.012"
            fill="#ffffff"
            fillOpacity="0.07"
          />
          {/* Deep-set panel: outer shadow, then the matte face inset within it. */}
          <rect
            x={PILLAR - 0.02}
            y={PANEL_TOP - 0.03}
            width={1 - (PILLAR - 0.02) * 2}
            height={PANEL_BOTTOM - PANEL_TOP + 0.05}
            rx="0.04"
            fill="#000"
            fillOpacity="0.85"
          />
          <rect
            x={PILLAR}
            y={PANEL_TOP}
            width={1 - PILLAR * 2}
            height={PANEL_BOTTOM - PANEL_TOP}
            rx="0.032"
            fill={url('panel')}
          />
          <rect
            x={PILLAR}
            y={PANEL_TOP}
            width={1 - PILLAR * 2}
            height="0.014"
            fill="#000"
            fillOpacity="0.9"
          />
          {/* Sculpted corner columns the panel sits between. */}
          <rect x="0" y="0" width={PILLAR} height="1" fill={url('column')} />
          <rect x={1 - PILLAR} y="0" width={PILLAR} height="1" fill={url('column')} />
          <rect x="0.049" y="0.02" width="0.011" height="0.96" fill="#ffffff" fillOpacity="0.12" />
          <rect x="0.887" y="0.02" width="0.011" height="0.96" fill="#ffffff" fillOpacity="0.09" />
          {/* Fasteners and one indicator, kept secondary. */}
          {[0.078, 0.922].map((sx) =>
            [0.4, 0.8].map((sy) => (
              <circle
                key={`${sx}-${sy}`}
                cx={sx}
                cy={sy}
                r="0.011"
                fill="#1b211d"
                stroke="#5b635c"
                strokeOpacity="0.45"
                strokeWidth="0.005"
              />
            )),
          )}
          <circle className="server-led server-led-front" cx="0.215" cy="0.815" r="0.012" fill="#b7ff00" fillOpacity="0.9" />
        </g>
      </g>
      <g clipPath={url('cf')} filter={url('neon')}>
        <g transform={M_F}>
          <rect className="server-seam-core" x="0.008" y={SEAM_CORE} width="0.984" height={SEAM_CORE_H} fill="#d4ff3a" />
        </g>
      </g>
      <g clipPath={url('cf')} filter={url('mark')}>
        <g transform={M_F}>
          <g className="server-chevron" transform={MARK_F_TRANSFORM}>
            {ICON_STROKES.map((stroke, i) => (
              <path
                key={i}
                d={stroke.d}
                fill="none"
                stroke={stroke.stroke}
                strokeWidth={stroke.width}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            ))}
          </g>
        </g>
      </g>

      {/* ---- Right face: three distinct recessed modules ---- */}
      <g clipPath={url('cr')}>
        <g transform={M_R}>
          <rect x="0" y="0" width="1" height={LID_EDGE} fill={url('lidEdge')} />
          <rect x="0" y={LID_EDGE} width="1" height={SEAM_TOP - LID_EDGE} fill={url('frameR')} />
          <rect x="0" y={LID_EDGE} width="1" height="0.014" fill="#000" fillOpacity="0.6" />
          <rect x="0" y={SEAM_TOP} width="1" height={SEAM_BOTTOM - SEAM_TOP} fill="#030504" />
          <rect x="0" y={SEAM_BOTTOM} width="1" height="0.085" fill={url('spill')} />
          <rect x="0" y={PANEL_BOTTOM} width="1" height={1 - PANEL_BOTTOM} fill="#0c110e" />

          {/* Module 1: slot bank. */}
          <rect x="0.19" y="0.352" width="0.66" height="0.16" rx="0.018" fill="#000" fillOpacity="0.8" />
          <rect
            x="0.2"
            y="0.36"
            width="0.64"
            height="0.14"
            rx="0.016"
            fill="#070a08"
            stroke="#39413b"
            strokeOpacity="0.3"
            strokeWidth="0.009"
          />
          {GROOVES.map((g) => (
            <rect
              key={g}
              x="0.235"
              y={0.378 + g * 0.028}
              width="0.4"
              height="0.013"
              rx="0.006"
              fill="#0b0f0b"
            />
          ))}
          <circle cx="0.77" cy="0.385" r="0.011" fill="#161b17" />
          <circle cx="0.77" cy="0.47" r="0.011" fill="#161b17" />

          {/* Module 2: drive bay with a control and an indicator. */}
          <rect x="0.19" y="0.513" width="0.66" height="0.154" rx="0.018" fill="#000" fillOpacity="0.8" />
          <rect
            x="0.2"
            y="0.52"
            width="0.64"
            height="0.14"
            rx="0.016"
            fill="#060807"
            stroke="#39413b"
            strokeOpacity="0.28"
            strokeWidth="0.009"
          />
          <rect x="0.235" y="0.545" width="0.34" height="0.017" rx="0.007" fill="#0c110c" />
          <rect x="0.235" y="0.582" width="0.28" height="0.017" rx="0.007" fill="#0c110c" />
          <circle
            cx="0.73"
            cy="0.59"
            r="0.019"
            fill="#121813"
            stroke="#4b524d"
            strokeOpacity="0.4"
            strokeWidth="0.007"
          />
          <circle className="server-led server-led-upper" cx="0.79" cy="0.548" r="0.012" fill="#b7ff00" fillOpacity="0.6" />

          {/* Module 3: ports. */}
          <rect x="0.19" y="0.673" width="0.66" height="0.154" rx="0.018" fill="#000" fillOpacity="0.8" />
          <rect
            x="0.2"
            y="0.68"
            width="0.64"
            height="0.14"
            rx="0.016"
            fill="#070a08"
            stroke="#39413b"
            strokeOpacity="0.26"
            strokeWidth="0.009"
          />
          <rect x="0.235" y="0.708" width="0.16" height="0.042" rx="0.01" fill="#0d120f" />
          <rect x="0.42" y="0.708" width="0.16" height="0.042" rx="0.01" fill="#0d120f" />
          <circle cx="0.68" cy="0.73" r="0.01" fill="#161c18" />
          <circle cx="0.73" cy="0.73" r="0.01" fill="#161c18" />
          <circle className="server-led server-led-lower" cx="0.78" cy="0.73" r="0.01" fill="#b7ff00" fillOpacity="0.5" />

          {/* Columns: the near corner shares the front face's, the far edge gets its own. */}
          <rect x="0" y="0" width={PILLAR * 0.9} height="1" fill={url('columnR')} />
          <rect x={1 - PILLAR * 0.8} y="0" width={PILLAR * 0.8} height="1" fill={url('column')} />
          <rect x="0.014" y="0.02" width="0.011" height="0.96" fill="#ffffff" fillOpacity="0.11" />
        </g>
      </g>
      <g clipPath={url('cr')} filter={url('neon')}>
        <g transform={M_R}>
          <rect className="server-seam-core" x="0.008" y={SEAM_CORE} width="0.984" height={SEAM_CORE_H} fill="#d4ff3a" />
        </g>
      </g>

      {/* ---- Lid: thick rounded chassis, inner bevel, inset graphite plate ---- */}
      <path d={TOP_PATH} fill={url('lid')} />
      <path d={TOP_PATH} fill="none" stroke="#5b635c" strokeOpacity="0.9" strokeWidth="1.2" />
      <g clipPath={url('ct')}>
        <g transform={M_TOP}>
          {/* Broad curved perimeter, then the bevel that steps down to the plate. */}
          <rect
            x="0.075"
            y="0.085"
            width="0.85"
            height="0.83"
            rx="0.075"
            fill="none"
            stroke="#666e67"
            strokeOpacity="0.32"
            strokeWidth="0.009"
          />
          <rect
            x="0.125"
            y="0.135"
            width="0.75"
            height="0.73"
            rx="0.055"
            fill="#000"
            fillOpacity="0.55"
          />
          <rect
            x="0.135"
            y="0.145"
            width="0.73"
            height="0.71"
            rx="0.05"
            fill={url('lidPlate')}
            stroke="#5b635c"
            strokeOpacity="0.5"
            strokeWidth="0.008"
          />
          <rect
            x="0.135"
            y="0.145"
            width="0.73"
            height="0.014"
            fill="#000"
            fillOpacity="0.5"
          />
          {[0.048, 0.952].map((sx) =>
            [0.055, 0.945].map((sy) => (
              <circle
                key={`${sx}-${sy}`}
                cx={sx}
                cy={sy}
                r="0.016"
                fill="#252c26"
                stroke="#6b736c"
                strokeOpacity="0.55"
                strokeWidth="0.006"
              />
            )),
          )}
        </g>
      </g>
      <g clipPath={url('ct')} filter={url('mark')}>
        <g transform={M_TOP}>
          <g className="server-chevron" transform={MARK_TOP_TRANSFORM}>
            {ICON_STROKES.map((stroke, i) => (
              <path
                key={i}
                d={stroke.d}
                fill="none"
                stroke={stroke.stroke}
                strokeWidth={stroke.width}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            ))}
          </g>
        </g>
      </g>

      {/* Metallic edge speculars: these carry the form with the glow disabled. */}
      <path
        d={`M ${A[0] + 8} ${A[1] - 3} L ${D[0] - 7} ${D[1] - 1}`}
        fill="none"
        stroke="#ffffff"
        strokeOpacity="0.14"
        strokeWidth="1.2"
      />
      <path
        d={`M ${D[0] + 8} ${D[1] + 1} L ${C[0] - 7} ${C[1] - 2}`}
        fill="none"
        stroke="#ffffff"
        strokeOpacity="0.1"
        strokeWidth="1"
      />
      <path
        d={`M ${A[0]} ${A[1] + 9} L ${Ab[0]} ${Ab[1] - 9}`}
        stroke="#ffffff"
        strokeOpacity="0.12"
        strokeWidth="1.4"
      />
      <path
        d={`M ${B[0]} ${B[1] + 9} L ${Bb[0]} ${Bb[1] - 9}`}
        stroke="#ffffff"
        strokeOpacity="0.1"
        strokeWidth="1.3"
      />
      <path
        d={`M ${C[0]} ${C[1] + 9} L ${Cb[0]} ${Cb[1] - 9}`}
        stroke="#ffffff"
        strokeOpacity="0.07"
        strokeWidth="1.1"
      />
    </svg>
  );
}
