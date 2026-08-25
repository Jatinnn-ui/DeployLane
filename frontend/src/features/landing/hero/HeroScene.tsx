import { ContactShadows, PerspectiveCamera } from '@react-three/drei';
import { Canvas, useFrame } from '@react-three/fiber';
import { useEffect, useMemo, useRef, type RefObject } from 'react';
import * as THREE from 'three';
import { DeployCube } from './DeployCube';
import { DeploymentRings } from './DeploymentRings';
import { DeploymentServer } from './DeploymentServer';
import { FloatingCubes } from './FloatingCubes';
import { createHeroMaterials, LIME, type HeroMaterials } from './materials';
import { ServerPlatform } from './ServerPlatform';
import { Starfield } from './Starfield';

interface Pointer {
  x: number;
  y: number;
}

/**
 * Pointer is tracked at window level because the DOM cards sit above the canvas. Normalising
 * against this visual's own bounds keeps the movement continuous as the cursor crosses a
 * card, while the low rotation cap means the server never turns away from its product pose.
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

function setOpacity(materials: HeroMaterials, opacity: number) {
  [
    materials.body,
    materials.bodyDark,
    materials.panel,
    materials.trim,
    materials.edge,
    materials.chevron,
    materials.status,
  ].forEach((material) => {
    material.opacity = opacity;
  });
}

function settleMaterials(materials: HeroMaterials) {
  setOpacity(materials, 1);
  materials.edge.emissiveIntensity = 2.2;
  materials.chevron.emissiveIntensity = 3.4;
  materials.status.emissiveIntensity = 2.8;
}

/**
 * Hero-only scene. This is intentionally separate from `FeatureVisual` below: the request is
 * to rebuild the right side of the first fold, not silently replace the 3D art elsewhere.
 */
function HeroVisual({
  pointer,
  reducedMotion,
  simplified,
}: {
  pointer: RefObject<Pointer>;
  reducedMotion: boolean;
  simplified: boolean;
}) {
  const serverRig = useRef<THREE.Group>(null);
  const platformRig = useRef<THREE.Group>(null);
  const introTime = useRef(reducedMotion ? 10 : 0);

  const materials = useMemo(() => {
    const next = createHeroMaterials();

    if (!reducedMotion) {
      [
        next.body,
        next.bodyDark,
        next.panel,
        next.trim,
        next.edge,
        next.chevron,
        next.status,
      ].forEach((material) => {
        material.transparent = true;
        material.opacity = 0;
      });
      next.edge.emissiveIntensity = 0;
      next.chevron.emissiveIntensity = 0;
      next.status.emissiveIntensity = 0;
    }

    return next;
    // The material set belongs to this Canvas for its full lifetime. Runtime motion changes
    // are handled by the settling effect rather than by allocating a second shader set.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => materials.dispose, [materials]);

  useEffect(() => {
    if (!reducedMotion) return;

    introTime.current = 10;
    settleMaterials(materials);
    if (serverRig.current) {
      serverRig.current.visible = true;
      serverRig.current.position.set(0, 0, 0);
      serverRig.current.rotation.set(0, 0, 0);
      serverRig.current.scale.setScalar(1);
    }
    if (platformRig.current) {
      platformRig.current.visible = true;
      platformRig.current.scale.setScalar(1);
    }
  }, [materials, reducedMotion]);

  useFrame((_, delta) => {
    if (!serverRig.current || !platformRig.current || reducedMotion) return;

    introTime.current = Math.min(10, introTime.current + Math.min(delta, 0.05));
    const t = introTime.current;

    // 1. Chassis appears first (0–0.9 s): fade plus a restrained scale/vertical settle.
    const serverProgress = THREE.MathUtils.smoothstep(t, 0, 0.9);
    serverRig.current.visible = serverProgress > 0;
    serverRig.current.scale.setScalar(THREE.MathUtils.lerp(0.84, 1, serverProgress));
    serverRig.current.position.y = THREE.MathUtils.lerp(-0.14, 0, serverProgress);
    setOpacity(materials, serverProgress);

    // 2. Perimeter and logo illumination switch on after the chassis is readable.
    const lightProgress = THREE.MathUtils.smoothstep(t, 0.5, 1.18);
    materials.edge.emissiveIntensity = THREE.MathUtils.lerp(0, 2.2, lightProgress);
    materials.chevron.emissiveIntensity = THREE.MathUtils.lerp(0, 3.4, lightProgress);
    materials.status.emissiveIntensity = THREE.MathUtils.lerp(0, 2.8, lightProgress);

    // 3. Platform expands into place (0.82–1.42 s), after the server owns the composition.
    const platformProgress = THREE.MathUtils.smoothstep(t, 0.82, 1.42);
    platformRig.current.visible = platformProgress > 0;
    platformRig.current.scale.setScalar(THREE.MathUtils.lerp(0.72, 1, platformProgress));

    // Once settled, cursor parallax is only a few degrees. The camera establishes the
    // three-quarter view; this is depth response, not continuous product rotation.
    if (t > 1.42) {
      const targetY = pointer.current.x * 0.035;
      const targetX = pointer.current.y * 0.022;
      serverRig.current.rotation.y = THREE.MathUtils.lerp(serverRig.current.rotation.y, targetY, 0.035);
      serverRig.current.rotation.x = THREE.MathUtils.lerp(serverRig.current.rotation.x, targetX, 0.035);
    }
  });

  return (
    <>
      {/* Key: upper-left/front. Strong enough to read graphite planes as solid geometry. */}
      <directionalLight position={[-4.5, 6.5, 5.5]} intensity={2.35} color="#f3f6f4" />

      {/* Soft neutral front fill keeps recesses visible rather than crushed to black. */}
      <directionalLight position={[0.5, 1.2, 6.5]} intensity={1.05} color="#cfd6d3" />
      <ambientLight intensity={0.46} color="#8f9894" />

      {/* Lime rim from behind/right and a restrained glow underneath the plinth. */}
      <pointLight position={[3.8, 2.2, -2.8]} intensity={4.2} distance={7} color={LIME} />
      <pointLight position={[0, -1.35, 0.5]} intensity={3.7} distance={5} color={LIME} />

      <group ref={platformRig} visible={reducedMotion}>
        <ServerPlatform reducedMotion={reducedMotion} simplified={simplified} />
      </group>

      <group ref={serverRig} visible={reducedMotion}>
        <DeploymentServer
          materials={materials}
          reducedMotion={reducedMotion}
          simplified={simplified}
          active
        />
      </group>

      <ContactShadows
        position={[0, -1.08, 0]}
        scale={7}
        opacity={0.72}
        blur={2.4}
        far={2.6}
        resolution={512}
        frames={1}
        color="#000000"
      />
    </>
  );
}

/**
 * The existing lower-page art, retained unchanged in behaviour. Keeping it separate prevents
 * a hero correction from becoming an unrequested redesign of the developer section.
 */
function FeatureVisual({
  reducedMotion,
  simplified,
}: {
  reducedMotion: boolean;
  simplified: boolean;
}) {
  const rig = useRef<THREE.Group>(null);
  const materials = useMemo(createHeroMaterials, []);

  useEffect(() => materials.dispose, [materials]);

  useFrame(({ clock }) => {
    if (!rig.current || reducedMotion) return;
    rig.current.rotation.y = 0.42 + Math.sin(clock.getElapsedTime() * 0.18) * 0.16;
  });

  return (
    <>
      <ambientLight intensity={0.42} color="#8e9793" />
      <directionalLight position={[4.5, 6.5, 4]} intensity={1.4} color="#ffffff" />
      <directionalLight position={[-5, 2, -3]} intensity={0.4} color="#aab4b0" />
      <pointLight position={[0, -0.8, 0.8]} intensity={3.4} distance={5} color={LIME} />
      <pointLight position={[0, 2.4, 0]} intensity={1.1} distance={4} color={LIME} />
      <Starfield reducedMotion={reducedMotion} count={simplified ? 120 : 260} />

      <group ref={rig} rotation={[0, 0.42, 0]}>
        <DeployCube materials={materials} reducedMotion={reducedMotion} simplified={simplified} />
        <DeploymentRings reducedMotion={reducedMotion} simplified={simplified} />
        {!simplified && (
          <FloatingCubes materials={materials} reducedMotion={reducedMotion} count={6} />
        )}
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

function SceneCanvas({
  reducedMotion,
  children,
  camera,
  fov,
}: {
  reducedMotion: boolean;
  children: React.ReactNode;
  camera: [number, number, number];
  fov: number;
}) {
  return (
    <Canvas
      dpr={[1, 2]}
      performance={{ min: 0.5 }}
      frameloop={reducedMotion ? 'demand' : 'always'}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      style={{ pointerEvents: 'none' }}
    >
      <PerspectiveCamera makeDefault fov={fov} position={camera} near={0.1} far={40} />
      {children}
    </Canvas>
  );
}

/** Primary first-fold hero: large server, platform, no starfield or satellite shapes. */
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
      <SceneCanvas
        reducedMotion={reducedMotion}
        camera={simplified ? [3.35, 2.15, 5.65] : [3.1, 2, 5.2]}
        fov={simplified ? 34 : 31}
      >
        <HeroVisual pointer={pointer} reducedMotion={reducedMotion} simplified={simplified} />
      </SceneCanvas>
    </div>
  );
}

/** Secondary scene used below the fold; intentionally preserves the existing cube artwork. */
export function FeatureScene({
  reducedMotion = false,
  simplified = false,
}: {
  reducedMotion?: boolean;
  simplified?: boolean;
}) {
  return (
    <div className="absolute inset-0" aria-hidden="true">
      <SceneCanvas reducedMotion={reducedMotion} camera={[2.6, 2.5, 6.6]} fov={32}>
        <FeatureVisual reducedMotion={reducedMotion} simplified={simplified} />
      </SceneCanvas>
    </div>
  );
}
