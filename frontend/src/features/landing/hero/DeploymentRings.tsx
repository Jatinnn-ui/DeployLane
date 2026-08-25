import { useFrame } from '@react-three/fiber';
import { useRef } from 'react';
import * as THREE from 'three';
import { LIME } from './materials';

interface RingSpec {
  radius: number;
  opacity: number;
  thickness: number;
}

/**
 * Concentric deployment rings on the ground plane.
 *
 * `ringGeometry` rather than a torus: a ring whose inner and outer radii are a hair apart
 * renders as a clean flat line for two triangles per segment, where a torus would cost a
 * full swept tube for the same visual result.
 *
 * Opacity falls off with radius so the field reads as radiating outward rather than as a
 * hard target pattern.
 */
const RINGS: RingSpec[] = [
  { radius: 1.7, opacity: 0.75, thickness: 0.02 },
  { radius: 2.15, opacity: 0.42, thickness: 0.014 },
  { radius: 2.7, opacity: 0.26, thickness: 0.011 },
  { radius: 3.35, opacity: 0.15, thickness: 0.009 },
  { radius: 4.1, opacity: 0.08, thickness: 0.007 },
];

/** Small nodes riding the rings, echoing the connector dots in the overlay. */
const NODES: Array<{ radius: number; angle: number }> = [
  { radius: 2.15, angle: 0.4 },
  { radius: 2.15, angle: 3.6 },
  { radius: 2.7, angle: 1.9 },
  { radius: 2.7, angle: 5.1 },
  { radius: 3.35, angle: 0.9 },
];

export function DeploymentRings({
  reducedMotion = false,
  simplified = false,
}: {
  reducedMotion?: boolean;
  simplified?: boolean;
}) {
  const sweep = useRef<THREE.Mesh>(null);

  useFrame(({ clock }) => {
    if (sweep.current && !reducedMotion) {
      sweep.current.rotation.z = clock.getElapsedTime() * 0.3;
    }
  });

  const rings = simplified ? RINGS.slice(0, 3) : RINGS;

  return (
    <group position={[0, -1.02, 0]} rotation={[-Math.PI / 2, 0, 0]}>
      {/* pedestal the cube rests on */}
      <mesh position={[0, 0, -0.02]}>
        <circleGeometry args={[1.5, 64]} />
        <meshStandardMaterial color="#0e1112" metalness={0.5} roughness={0.6} />
      </mesh>

      {/* lime wash across the pedestal, motivated by the point light above it */}
      <mesh position={[0, 0, 0.001]}>
        <circleGeometry args={[1.42, 48]} />
        <meshBasicMaterial color={LIME} transparent opacity={0.07} toneMapped={false} />
      </mesh>

      {/* bright rim right at the pedestal edge */}
      <mesh position={[0, 0, 0.002]}>
        <ringGeometry args={[1.47, 1.5, 96]} />
        <meshBasicMaterial color={LIME} transparent opacity={0.85} toneMapped={false} />
      </mesh>

      {rings.map((ring) => (
        <mesh key={ring.radius} position={[0, 0, 0.002]}>
          <ringGeometry args={[ring.radius, ring.radius + ring.thickness, 96]} />
          <meshBasicMaterial
            color={LIME}
            transparent
            opacity={ring.opacity}
            side={THREE.DoubleSide}
            toneMapped={false}
          />
        </mesh>
      ))}

      {!simplified &&
        NODES.map((node) => (
          <mesh
            key={`${node.radius}:${node.angle}`}
            position={[
              Math.cos(node.angle) * node.radius,
              Math.sin(node.angle) * node.radius,
              0.004,
            ]}
          >
            <circleGeometry args={[0.045, 12]} />
            <meshBasicMaterial color={LIME} transparent opacity={0.9} toneMapped={false} />
          </mesh>
        ))}

      {/* one brighter arc sweeping the inner ring, signalling an active deployment */}
      {!simplified && (
        <mesh ref={sweep} position={[0, 0, 0.003]}>
          <ringGeometry args={[1.7, 1.726, 96, 1, 0, Math.PI / 2.6]} />
          <meshBasicMaterial color={LIME} transparent opacity={0.95} toneMapped={false} />
        </mesh>
      )}
    </group>
  );
}
