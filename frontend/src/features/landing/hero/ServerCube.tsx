import { RoundedBox } from '@react-three/drei';
import { useFrame } from '@react-three/fiber';
import { useEffect, useMemo, useRef } from 'react';
import * as THREE from 'three';
import { createServerMaterials } from './materials';

const W = 2.15;
const H = 1.5;
const D = 1.7;

/**
 * The DeployLane deployment server.
 *
 * A premium industrial object rather than a decorative cube: layered panels, ventilation
 * slots, recessed front inset, beveled trim, lime seams and a single live status light.
 * Detail is deliberately capped — every feature here is either structural or reads as
 * infrastructure, and nothing is added purely as ornament.
 */
export function ServerCube({
  reducedMotion = false,
  simplified = false,
}: {
  reducedMotion?: boolean;
  simplified?: boolean;
}) {
  const group = useRef<THREE.Group>(null);
  const status = useRef<THREE.Mesh>(null);
  const materials = useMemo(createServerMaterials, []);

  useEffect(() => materials.dispose, [materials]);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();

    if (group.current && !reducedMotion) {
      // Slow 4px-equivalent float, ~6s period.
      group.current.position.y = Math.sin(t / 3) * 0.045;
    }

    if (status.current && !reducedMotion) {
      // Soft breathing on the live indicator.
      const pulse = 0.55 + (Math.sin(t * 1.6) * 0.5 + 0.5) * 0.45;
      status.current.scale.setScalar(pulse);
    }
  });

  const ventSlots = simplified ? 4 : 7;

  return (
    <group ref={group}>
      {/* main chassis */}
      <RoundedBox args={[W, H, D]} radius={0.1} smoothness={4} material={materials.body} castShadow />

      {/* top and bottom trim plates, giving the body a layered look */}
      <RoundedBox
        args={[W * 0.94, 0.06, D * 0.94]}
        radius={0.02}
        smoothness={3}
        position={[0, H / 2 + 0.005, 0]}
        material={materials.trim}
      />
      <RoundedBox
        args={[W * 0.94, 0.06, D * 0.94]}
        radius={0.02}
        smoothness={3}
        position={[0, -H / 2 - 0.005, 0]}
        material={materials.trim}
      />

      {/* recessed smoked-glass inset on the front face */}
      <mesh position={[0, 0.06, D / 2 + 0.005]} material={materials.glass}>
        <boxGeometry args={[W * 0.62, H * 0.5, 0.03]} />
      </mesh>

      {/* DeployLane chevron, built from two angled bars so it needs no texture */}
      <group position={[0, 0.06, D / 2 + 0.035]}>
        <mesh position={[-0.07, 0.1, 0]} rotation={[0, 0, -Math.PI / 3.4]} material={materials.seam}>
          <boxGeometry args={[0.075, 0.42, 0.03]} />
        </mesh>
        <mesh position={[-0.07, -0.11, 0]} rotation={[0, 0, Math.PI / 3.4]} material={materials.seam}>
          <boxGeometry args={[0.075, 0.42, 0.03]} />
        </mesh>
      </group>

      {/* horizontal lime seams wrapping the chassis */}
      <mesh position={[0, -H * 0.3, D / 2 + 0.002]} material={materials.seam}>
        <boxGeometry args={[W * 0.78, 0.014, 0.02]} />
      </mesh>
      <mesh
        position={[W / 2 + 0.002, -H * 0.3, 0]}
        rotation={[0, Math.PI / 2, 0]}
        material={materials.seam}
      >
        <boxGeometry args={[D * 0.78, 0.014, 0.02]} />
      </mesh>

      {/* vertical accent seam on the right shoulder */}
      <mesh position={[W * 0.4, 0.18, D / 2 + 0.002]} material={materials.seam}>
        <boxGeometry args={[0.014, H * 0.34, 0.02]} />
      </mesh>

      {/* ventilation slots, recessed into the right side panel */}
      <group position={[W / 2 + 0.006, 0.12, 0]} rotation={[0, Math.PI / 2, 0]}>
        {Array.from({ length: ventSlots }).map((_, i) => (
          <mesh
            key={i}
            position={[(i - (ventSlots - 1) / 2) * 0.14, 0, 0]}
            material={materials.panel}
          >
            <boxGeometry args={[0.05, H * 0.42, 0.02]} />
          </mesh>
        ))}
      </group>

      {/* rear service panel */}
      <mesh position={[0, 0, -D / 2 - 0.004]} material={materials.panel}>
        <boxGeometry args={[W * 0.8, H * 0.66, 0.02]} />
      </mesh>

      {/* live status light */}
      <mesh ref={status} position={[W * 0.4, -0.42, D / 2 + 0.03]} material={materials.status}>
        <sphereGeometry args={[0.035, 12, 12]} />
      </mesh>

      {/* two dimmer indicators beside it */}
      {!simplified &&
        [0.24, 0.12].map((x) => (
          <mesh key={x} position={[W * x, -0.42, D / 2 + 0.025]} material={materials.trim}>
            <sphereGeometry args={[0.022, 8, 8]} />
          </mesh>
        ))}
    </group>
  );
}
