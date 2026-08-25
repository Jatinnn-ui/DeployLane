import { useFrame } from '@react-three/fiber';
import { useRef } from 'react';
import * as THREE from 'three';
import { LIME } from './materials';

interface ServerPlatformProps {
  reducedMotion?: boolean;
  simplified?: boolean;
  active?: boolean;
}

const RINGS = [
  { radius: 1.5, width: 0.035, opacity: 0.82 },
  { radius: 1.78, width: 0.024, opacity: 0.48 },
  { radius: 2.08, width: 0.018, opacity: 0.29 },
  { radius: 2.4, width: 0.014, opacity: 0.16 },
  { radius: 2.72, width: 0.011, opacity: 0.08 },
];

/**
 * Grounding platform for the hero server.
 *
 * The physical plinth is 3.65 units wide — 135% of the server's 2.7-unit width. The radar
 * rings extend beyond it, but decrease quickly in intensity, giving the object a clear base
 * without turning the entire right column lime.
 */
export function ServerPlatform({
  reducedMotion = false,
  simplified = false,
  active = true,
}: ServerPlatformProps) {
  const pulse = useRef<THREE.Group>(null);
  const sweep = useRef<THREE.Mesh>(null);

  useFrame(({ clock }) => {
    if (reducedMotion || !active) return;
    const t = clock.getElapsedTime();

    if (pulse.current) {
      const scale = 1 + (Math.sin(t * 0.75) * 0.5 + 0.5) * 0.012;
      pulse.current.scale.setScalar(scale);
    }
    if (sweep.current) {
      sweep.current.rotation.z = t * 0.16;
    }
  });

  const rings = simplified ? RINGS.slice(0, 3) : RINGS;

  return (
    <group position={[0, -1.07, 0]} rotation={[-Math.PI / 2, 0, 0]}>
      {/* Three physical layers create a machined deployment plinth. */}
      <mesh position={[0, 0, -0.09]} rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[1.82, 1.9, 0.14, 96]} />
        <meshStandardMaterial color="#0b0e0f" metalness={0.72} roughness={0.38} />
      </mesh>
      <mesh position={[0, 0, -0.035]} rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[1.72, 1.8, 0.08, 96]} />
        <meshStandardMaterial color="#15191a" metalness={0.6} roughness={0.46} />
      </mesh>
      <mesh position={[0, 0, 0.012]}>
        <circleGeometry args={[1.66, 96]} />
        <meshStandardMaterial color="#101415" metalness={0.52} roughness={0.52} />
      </mesh>

      {/* Soft radial wash directly under the server. */}
      <mesh position={[0, 0, 0.018]}>
        <circleGeometry args={[1.5, 96]} />
        <meshBasicMaterial
          color={LIME}
          transparent
          opacity={0.075}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
          toneMapped={false}
        />
      </mesh>

      {/* Selected physical rims, not every layer, keep the hierarchy controlled. */}
      <mesh position={[0, 0, 0.02]}>
        <ringGeometry args={[1.63, 1.655, 128]} />
        <meshBasicMaterial color={LIME} transparent opacity={0.8} toneMapped={false} />
      </mesh>
      <mesh position={[0, 0, -0.008]}>
        <ringGeometry args={[1.78, 1.79, 128]} />
        <meshBasicMaterial color={LIME} transparent opacity={0.24} toneMapped={false} />
      </mesh>

      <group ref={pulse}>
        {rings.map((ring) => (
          <mesh key={ring.radius} position={[0, 0, 0.004]}>
            <ringGeometry args={[ring.radius, ring.radius + ring.width, 128]} />
            <meshBasicMaterial
              color={LIME}
              transparent
              opacity={ring.opacity}
              depthWrite={false}
              side={THREE.DoubleSide}
              toneMapped={false}
            />
          </mesh>
        ))}

        {!simplified && (
          <>
            {/* Four quiet cardinal nodes make the rings read as an interface, not decoration. */}
            {[0, Math.PI / 2, Math.PI, Math.PI * 1.5].map((angle) => (
              <mesh
                key={angle}
                position={[Math.cos(angle) * 2.08, Math.sin(angle) * 2.08, 0.008]}
              >
                <circleGeometry args={[0.035, 14]} />
                <meshBasicMaterial color={LIME} transparent opacity={0.75} toneMapped={false} />
              </mesh>
            ))}

            {/* A restrained active arc moves much more slowly than the pipeline packets. */}
            <mesh ref={sweep} position={[0, 0, 0.01]}>
              <ringGeometry args={[1.78, 1.815, 128, 1, 0, Math.PI / 2.8]} />
              <meshBasicMaterial color={LIME} transparent opacity={0.9} toneMapped={false} />
            </mesh>
          </>
        )}
      </group>
    </group>
  );
}
