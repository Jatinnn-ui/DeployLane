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
 * Concentric deployment rings beneath the server.
 *
 * `ringGeometry` rather than a torus: a ring whose inner and outer radii are a hair
 * apart renders as a clean flat line for two triangles per segment, where a torus would
 * cost a full swept tube for the same visual result.
 *
 * Opacity falls off with radius so the rings read as fading outward rather than as a
 * hard target pattern.
 */
const RINGS: RingSpec[] = [
  { radius: 1.55, opacity: 0.55, thickness: 0.012 },
  { radius: 2.0, opacity: 0.32, thickness: 0.01 },
  { radius: 2.5, opacity: 0.18, thickness: 0.008 },
  { radius: 3.05, opacity: 0.09, thickness: 0.006 },
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
      // One slow revolution roughly every 25 seconds.
      sweep.current.rotation.z = clock.getElapsedTime() * 0.25;
    }
  });

  const rings = simplified ? RINGS.slice(0, 2) : RINGS;

  return (
    <group position={[0, -1.05, 0]} rotation={[-Math.PI / 2, 0, 0]}>
      {/* dark platform the server sits above */}
      <mesh position={[0, 0, -0.01]}>
        <circleGeometry args={[1.42, 64]} />
        <meshStandardMaterial color="#101314" metalness={0.4} roughness={0.7} />
      </mesh>

      {/* soft lime underglow */}
      <mesh position={[0, 0, 0.001]}>
        <circleGeometry args={[1.3, 48]} />
        <meshBasicMaterial color={LIME} transparent opacity={0.06} toneMapped={false} />
      </mesh>

      {rings.map((ring) => (
        <mesh key={ring.radius}>
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

      {/* a single brighter arc sweeping the inner ring, signalling active deployment */}
      {!simplified && (
        <mesh ref={sweep}>
          <ringGeometry args={[1.55, 1.572, 96, 1, 0, Math.PI / 3]} />
          <meshBasicMaterial color={LIME} transparent opacity={0.9} toneMapped={false} />
        </mesh>
      )}
    </group>
  );
}
