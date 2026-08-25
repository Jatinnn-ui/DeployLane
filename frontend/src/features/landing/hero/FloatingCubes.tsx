import { RoundedBox } from '@react-three/drei';
import { useFrame } from '@react-three/fiber';
import { useRef } from 'react';
import * as THREE from 'three';
import type { HeroMaterials } from './materials';

/**
 * Satellite units orbiting the main cube.
 *
 * Positions are hand-placed rather than random so the composition is the same on every
 * load — a marketing hero that reshuffles itself between visits looks unfinished, and a
 * fixed layout lets each cube be tucked into negative space around the pipeline cards.
 */
const CUBES: Array<{ at: [number, number, number]; size: number; spin: number }> = [
  { at: [-2.6, 0.9, 0.8], size: 0.34, spin: 0.22 },
  { at: [2.5, 1.35, -0.6], size: 0.26, spin: -0.3 },
  { at: [-2.1, -0.55, -1.5], size: 0.2, spin: 0.35 },
  { at: [2.15, -0.3, 1.4], size: 0.3, spin: -0.18 },
  { at: [-1.35, 1.75, -1.1], size: 0.18, spin: 0.4 },
  { at: [1.5, 1.9, 1.0], size: 0.22, spin: 0.26 },
];

export function FloatingCubes({
  materials,
  reducedMotion = false,
  count = CUBES.length,
}: {
  materials: HeroMaterials;
  reducedMotion?: boolean;
  count?: number;
}) {
  const group = useRef<THREE.Group>(null);

  useFrame(({ clock }) => {
    if (reducedMotion || !group.current) return;
    const t = clock.getElapsedTime();

    group.current.children.forEach((cube, i) => {
      const spec = CUBES[i];
      cube.rotation.y = t * spec.spin;
      cube.rotation.x = t * spec.spin * 0.6;
      // Offset each bob by index so they never move as one block.
      cube.position.y = spec.at[1] + Math.sin(t * 0.7 + i * 1.4) * 0.14;
    });
  });

  return (
    <group ref={group}>
      {CUBES.slice(0, count).map((cube) => (
        <group key={cube.at.join(',')} position={cube.at}>
          <RoundedBox
            args={[cube.size, cube.size, cube.size]}
            radius={cube.size * 0.16}
            smoothness={3}
            material={materials.body}
          />
          {/* single lime seam so the small cubes still read as DeployLane units */}
          <mesh position={[0, -cube.size * 0.42, 0]} material={materials.edge}>
            <boxGeometry args={[cube.size * 0.82, 0.012, cube.size * 0.82]} />
          </mesh>
        </group>
      ))}
    </group>
  );
}
