import { ContactShadows, PerspectiveCamera } from '@react-three/drei';
import { Canvas, useFrame } from '@react-three/fiber';
import { useEffect, useRef, type RefObject } from 'react';
import * as THREE from 'three';
import { DeploymentRings } from './DeploymentRings';
import { LIME } from './materials';
import { Particles } from './Particles';
import { ServerCube } from './ServerCube';

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

function Scene({
  pointer,
  reducedMotion,
  simplified,
}: {
  pointer: RefObject<Pointer>;
  reducedMotion: boolean;
  simplified: boolean;
}) {
  const rig = useRef<THREE.Group>(null);
  // Reduced motion skips straight to the settled pose: the scene still renders, it just
  // arrives there instead of animating in.
  const intro = useRef(reducedMotion ? 1 : 0);

  useFrame((_, delta) => {
    if (!rig.current) return;

    // Intro: ease the whole rig up into place and settle its rotation.
    if (intro.current < 1) {
      intro.current = Math.min(1, intro.current + delta / INTRO_DURATION);
      const eased = 1 - (1 - intro.current) ** 3;

      rig.current.position.y = THREE.MathUtils.lerp(-0.6, 0, eased);
      rig.current.scale.setScalar(THREE.MathUtils.lerp(0.82, 1, eased));
      rig.current.rotation.y = THREE.MathUtils.lerp(0.55, 0.16, eased);
      return;
    }

    if (reducedMotion) return;

    // Parallax: a few degrees at most, eased so the object never snaps to the cursor.
    const targetY = 0.16 + pointer.current.x * 0.1;
    const targetX = pointer.current.y * 0.05;

    rig.current.rotation.y = THREE.MathUtils.lerp(rig.current.rotation.y, targetY, 0.045);
    rig.current.rotation.x = THREE.MathUtils.lerp(rig.current.rotation.x, targetX, 0.045);
  });

  return (
    <>
      <PerspectiveCamera makeDefault fov={34} position={[3.3, 2.15, 5.6]} near={0.1} far={40} />

      {/* Neutral key light from above-front keeps the chassis reading as graphite, not green. */}
      <ambientLight intensity={0.4} color="#8e9793" />
      <directionalLight position={[4.5, 6.5, 4]} intensity={1.35} color="#ffffff" />
      <directionalLight position={[-5, 2, -3]} intensity={0.35} color="#aab4b0" />

      {/* The only coloured light: a lime source at the base, motivating the underglow. */}
      <pointLight position={[0, -0.85, 0.6]} intensity={3.2} distance={4.5} color={LIME} />

      <group ref={rig} rotation={[0, reducedMotion ? 0.16 : 0.55, 0]}>
        <ServerCube reducedMotion={reducedMotion} simplified={simplified} />
        <DeploymentRings reducedMotion={reducedMotion} simplified={simplified} />
        {!simplified && <Particles reducedMotion={reducedMotion} count={90} />}

        {/* Baked once: the float is subtle enough that a static contact shadow holds up. */}
        <ContactShadows
          position={[0, -1.06, 0]}
          scale={9}
          opacity={0.6}
          blur={2.8}
          far={2.4}
          resolution={512}
          frames={1}
          color="#000000"
        />
      </group>
    </>
  );
}

export function HeroScene({
  reducedMotion = false,
  simplified = false,
}: {
  reducedMotion?: boolean;
  simplified?: boolean;
}) {
  const container = useRef<HTMLDivElement>(null);
  const pointer = useHeroPointer(container, !reducedMotion && !simplified);

  return (
    <div ref={container} className="absolute inset-0" aria-hidden="true">
      <Canvas
        dpr={[1, 2]}
        performance={{ min: 0.5 }}
        frameloop={reducedMotion ? 'demand' : 'always'}
        gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
        style={{ pointerEvents: 'none' }}
      >
        <Scene pointer={pointer} reducedMotion={reducedMotion} simplified={simplified} />
      </Canvas>
    </div>
  );
}
