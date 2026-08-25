import { ContactShadows, PerspectiveCamera } from '@react-three/drei';
import { Canvas, useFrame } from '@react-three/fiber';
import { useEffect, useMemo, useRef, type RefObject } from 'react';
import * as THREE from 'three';
import { DeployCube } from './DeployCube';
import { DeploymentRings } from './DeploymentRings';
import { FloatingCubes } from './FloatingCubes';
import { createHeroMaterials, LIME } from './materials';
import { Starfield } from './Starfield';

interface Pointer {
  x: number;
  y: number;
}

/**
 * Pointer tracked at the window level rather than through R3F's `state.pointer`.
 *
 * The pipeline cards sit above the canvas and capture pointer events, so canvas-scoped
 * tracking would freeze the parallax whenever the cursor crossed a card. Listening on the
 * window and normalising against the hero's own bounds keeps the motion continuous.
 */
function useHeroPointer(container: RefObject<HTMLDivElement | null>, enabled: boolean) {
  const pointer = useRef<Pointer>({ x: 0, y: 0 });

  useEffect(() => {
    if (!enabled) return;

    function handleMove(event: PointerEvent) {
      const bounds = container.current?.getBoundingClientRect();
      if (!bounds) return;

      pointer.current = {
        x: THREE.MathUtils.clamp(((event.clientX - bounds.left) / bounds.width) * 2 - 1, -1, 1),
        y: THREE.MathUtils.clamp(((event.clientY - bounds.top) / bounds.height) * 2 - 1, -1, 1),
      };
    }

    window.addEventListener('pointermove', handleMove, { passive: true });
    return () => window.removeEventListener('pointermove', handleMove);
  }, [container, enabled]);

  return pointer;
}

const INTRO_DURATION = 1.8;
const RESTING_YAW = 0.42;

function Scene({
  pointer,
  reducedMotion,
  simplified,
  stars,
  drift,
}: {
  pointer: RefObject<Pointer> | null;
  reducedMotion: boolean;
  simplified: boolean;
  stars: boolean;
  drift: boolean;
}) {
  const rig = useRef<THREE.Group>(null);
  // Reduced motion skips straight to the settled pose: the scene still renders, it just
  // arrives there instead of animating in.
  const intro = useRef(reducedMotion ? 1 : 0);

  const materials = useMemo(createHeroMaterials, []);
  useEffect(() => materials.dispose, [materials]);

  useFrame(({ clock }, delta) => {
    if (!rig.current) return;

    if (intro.current < 1) {
      intro.current = Math.min(1, intro.current + delta / INTRO_DURATION);
      const eased = 1 - (1 - intro.current) ** 3;

      rig.current.position.y = THREE.MathUtils.lerp(-0.7, 0, eased);
      rig.current.scale.setScalar(THREE.MathUtils.lerp(0.8, 1, eased));
      rig.current.rotation.y = THREE.MathUtils.lerp(RESTING_YAW + 0.4, RESTING_YAW, eased);
      return;
    }

    if (reducedMotion) return;

    if (pointer) {
      // Parallax: a few degrees at most, eased so the cube never snaps to the cursor.
      const targetY = RESTING_YAW + pointer.current.x * 0.1;
      const targetX = pointer.current.y * 0.05;

      rig.current.rotation.y = THREE.MathUtils.lerp(rig.current.rotation.y, targetY, 0.045);
      rig.current.rotation.x = THREE.MathUtils.lerp(rig.current.rotation.x, targetX, 0.045);
    } else if (drift) {
      // No cursor to follow, so the secondary scene turns on its own instead.
      rig.current.rotation.y = RESTING_YAW + Math.sin(clock.getElapsedTime() * 0.18) * 0.16;
    }
  });

  return (
    <>
      {/* Neutral key light from above-front keeps the cube reading as graphite, not green. */}
      <ambientLight intensity={0.42} color="#8e9793" />
      <directionalLight position={[4.5, 6.5, 4]} intensity={1.4} color="#ffffff" />
      <directionalLight position={[-5, 2, -3]} intensity={0.4} color="#aab4b0" />

      {/* The only coloured lights: lime at the base, motivating the pedestal wash. */}
      <pointLight position={[0, -0.8, 0.8]} intensity={3.4} distance={5} color={LIME} />
      <pointLight position={[0, 2.4, 0]} intensity={1.1} distance={4} color={LIME} />

      {stars && <Starfield reducedMotion={reducedMotion} count={simplified ? 120 : 260} />}

      <group ref={rig} rotation={[0, RESTING_YAW + (reducedMotion ? 0 : 0.4), 0]}>
        <DeployCube materials={materials} reducedMotion={reducedMotion} simplified={simplified} />
        <DeploymentRings reducedMotion={reducedMotion} simplified={simplified} />
        {!simplified && (
          <FloatingCubes materials={materials} reducedMotion={reducedMotion} count={6} />
        )}

        {/* Baked once: the float is subtle enough that a static contact shadow holds up. */}
        <ContactShadows
          position={[0, -1.04, 0]}
          scale={10}
          opacity={0.65}
          blur={2.8}
          far={2.6}
          resolution={512}
          frames={1}
          color="#000000"
        />
      </group>
    </>
  );
}

function CubeCanvas({
  reducedMotion,
  simplified,
  stars,
  parallax,
  drift,
  camera,
  fov,
}: {
  reducedMotion: boolean;
  simplified: boolean;
  stars: boolean;
  parallax: boolean;
  drift: boolean;
  camera: [number, number, number];
  fov: number;
}) {
  const container = useRef<HTMLDivElement>(null);
  const pointer = useHeroPointer(container, parallax && !reducedMotion);

  return (
    <div ref={container} className="absolute inset-0" aria-hidden="true">
      <Canvas
        dpr={[1, 2]}
        performance={{ min: 0.5 }}
        frameloop={reducedMotion ? 'demand' : 'always'}
        gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
        style={{ pointerEvents: 'none' }}
      >
        <PerspectiveCamera makeDefault fov={fov} position={camera} near={0.1} far={40} />
        <Scene
          pointer={parallax && !reducedMotion ? pointer : null}
          reducedMotion={reducedMotion}
          simplified={simplified}
          stars={stars}
          drift={drift}
        />
      </Canvas>
    </div>
  );
}

/** Primary hero scene: starfield, satellite cubes, cursor parallax. */
export function HeroScene({
  reducedMotion = false,
  simplified = false,
}: {
  reducedMotion?: boolean;
  simplified?: boolean;
}) {
  return (
    <CubeCanvas
      reducedMotion={reducedMotion}
      simplified={simplified}
      stars
      parallax={!simplified}
      drift={false}
      camera={[3.6, 2.3, 6.2]}
      fov={34}
    />
  );
}

/**
 * Secondary scene for the developer section. Same object, framed tighter and turning on its
 * own — there are no pipeline cards over it, so there is nothing for a cursor to relate to.
 */
export function FeatureScene({
  reducedMotion = false,
  simplified = false,
}: {
  reducedMotion?: boolean;
  simplified?: boolean;
}) {
  return (
    <CubeCanvas
      reducedMotion={reducedMotion}
      simplified={simplified}
      stars
      parallax={false}
      drift
      camera={[2.6, 2.5, 6.6]}
      fov={32}
    />
  );
}
