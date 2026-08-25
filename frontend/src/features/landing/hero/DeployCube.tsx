import { RoundedBox } from '@react-three/drei';
import { useFrame } from '@react-three/fiber';
import { useRef } from 'react';
import * as THREE from 'three';
import type { HeroMaterials } from './materials';

/** Cube edge length. Near-cubic on purpose — this reads as a deployment unit, not a rack. */
const S = 1.9;
const HALF = S / 2;

/**
 * The DeployLane chevron, built from two mirrored bars.
 *
 * Deliberately geometry rather than a texture: no canvas to rasterise, no image to upload,
 * and it stays perfectly sharp at any camera distance. A unit chevron is roughly 0.5 units
 * tall, so `scale` maps predictably onto the face it sits on.
 */
function Chevron({
  scale = 1,
  material,
  ...props
}: {
  scale?: number;
  material: THREE.Material;
} & React.ComponentProps<'group'>) {
  return (
    <group scale={scale} {...props}>
      <mesh position={[-0.07, 0.11, 0]} rotation={[0, 0, -Math.PI / 3.4]} material={material}>
        <boxGeometry args={[0.08, 0.44, 0.04]} />
      </mesh>
      <mesh position={[-0.07, -0.11, 0]} rotation={[0, 0, Math.PI / 3.4]} material={material}>
        <boxGeometry args={[0.08, 0.44, 0.04]} />
      </mesh>
    </group>
  );
}

/** Four lime bars tracing the perimeter of a horizontal face. */
function EdgeFrame({
  y,
  material,
  thickness,
}: {
  y: number;
  material: THREE.Material;
  thickness: number;
}) {
  const span = S * 0.86;

  return (
    <group position={[0, y, 0]}>
      {[HALF - 0.02, -HALF + 0.02].map((z) => (
        <mesh key={`x${z}`} position={[0, 0, z]} material={material}>
          <boxGeometry args={[span, thickness, thickness]} />
        </mesh>
      ))}
      {[HALF - 0.02, -HALF + 0.02].map((x) => (
        <mesh key={`z${x}`} position={[x, 0, 0]} material={material}>
          <boxGeometry args={[thickness, thickness, span]} />
        </mesh>
      ))}
    </group>
  );
}

/**
 * The DeployLane deployment unit.
 *
 * A dark machined cube with the chevron on its front and top faces, lime edge lighting
 * along the top and bottom seams, and short vertical seams at the corners. Every element is
 * either structural or the brand mark — nothing is added as ornament.
 */
export function DeployCube({
  materials,
  reducedMotion = false,
  simplified = false,
}: {
  materials: HeroMaterials;
  reducedMotion?: boolean;
  simplified?: boolean;
}) {
  const group = useRef<THREE.Group>(null);
  const status = useRef<THREE.Mesh>(null);

  useFrame(({ clock }) => {
    if (reducedMotion) return;
    const t = clock.getElapsedTime();

    if (group.current) {
      group.current.position.y = Math.sin(t / 3) * 0.05;
    }
    if (status.current) {
      status.current.scale.setScalar(0.6 + (Math.sin(t * 1.7) * 0.5 + 0.5) * 0.4);
    }
  });

  return (
    <group ref={group}>
      <RoundedBox args={[S, S, S]} radius={0.14} smoothness={4} material={materials.body} />

      {/* Recessed top panel, so the top face reads as a lid rather than a flat side. */}
      <mesh position={[0, HALF + 0.008, 0]} rotation={[-Math.PI / 2, 0, 0]} material={materials.bodyDark}>
        <boxGeometry args={[S * 0.78, S * 0.78, 0.03]} />
      </mesh>

      {/* Chevron on the top lid, lying flat. */}
      <Chevron
        scale={1.05}
        material={materials.chevron}
        position={[0, HALF + 0.03, 0]}
        rotation={[-Math.PI / 2, 0, 0]}
      />

      {/* Chevron on the front face, the hero's focal point. */}
      <Chevron scale={1.5} material={materials.chevron} position={[0.04, -0.02, HALF + 0.02]} />

      {/* Lime edge lighting along the bottom and top seams. */}
      <EdgeFrame y={-HALF + 0.05} material={materials.edge} thickness={0.03} />
      <EdgeFrame y={HALF - 0.05} material={materials.edge} thickness={0.018} />

      {/* Short vertical seams at the corners, tying the two frames together. */}
      {!simplified &&
        (
          [
            [HALF - 0.03, HALF - 0.03],
            [-HALF + 0.03, HALF - 0.03],
            [HALF - 0.03, -HALF + 0.03],
            [-HALF + 0.03, -HALF + 0.03],
          ] as const
        ).map(([x, z]) => (
          <mesh key={`${x}:${z}`} position={[x, -HALF * 0.45, z]} material={materials.edge}>
            <boxGeometry args={[0.022, S * 0.3, 0.022]} />
          </mesh>
        ))}

      {/* Live indicator on the front face. */}
      <mesh ref={status} position={[HALF * 0.66, HALF * 0.62, HALF + 0.02]} material={materials.status}>
        <sphereGeometry args={[0.04, 12, 12]} />
      </mesh>
    </group>
  );
}
