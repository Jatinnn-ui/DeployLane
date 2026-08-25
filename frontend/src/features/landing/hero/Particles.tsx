import { useFrame } from '@react-three/fiber';
import { useEffect, useMemo, useRef } from 'react';
import * as THREE from 'three';
import { LIME } from './materials';

/**
 * Sparse data particles drifting around the server.
 *
 * A single `<points>` draw call for the whole field. Positions live in one Float32Array
 * that gets mutated in place each frame — for a field this small that is far cheaper than
 * giving every particle its own mesh, and it lets each one drift at its own speed instead
 * of the whole cloud rotating as a rigid body.
 */
export function Particles({
  reducedMotion = false,
  count = 90,
}: {
  reducedMotion?: boolean;
  count?: number;
}) {
  const points = useRef<THREE.Points>(null);

  const { geometry, speeds, spread } = useMemo(() => {
    const positions = new Float32Array(count * 3);
    const drift = new Float32Array(count);
    const height = 5;

    for (let i = 0; i < count; i += 1) {
      // Distribute in an annulus so particles surround the server without sitting inside it.
      const angle = Math.random() * Math.PI * 2;
      const radius = 1.9 + Math.random() * 3.2;

      positions[i * 3] = Math.cos(angle) * radius;
      positions[i * 3 + 1] = (Math.random() - 0.5) * height;
      positions[i * 3 + 2] = Math.sin(angle) * radius * 0.75;

      drift[i] = 0.055 + Math.random() * 0.12;
    }

    const buffer = new THREE.BufferGeometry();
    buffer.setAttribute('position', new THREE.BufferAttribute(positions, 3));

    return { geometry: buffer, speeds: drift, spread: height };
  }, [count]);

  useEffect(() => () => geometry.dispose(), [geometry]);

  useFrame((_, delta) => {
    if (reducedMotion || !points.current) return;

    const attribute = geometry.getAttribute('position') as THREE.BufferAttribute;
    const array = attribute.array as Float32Array;
    // Clamp delta so a backgrounded tab does not teleport the field on return.
    const step = Math.min(delta, 0.05);

    for (let i = 0; i < count; i += 1) {
      const y = i * 3 + 1;
      array[y] += speeds[i] * step;

      if (array[y] > spread / 2) {
        array[y] = -spread / 2;
      }
    }

    attribute.needsUpdate = true;
    points.current.rotation.y += step * 0.02;
  });

  return (
    <points ref={points} geometry={geometry}>
      <pointsMaterial
        color={LIME}
        size={0.028}
        sizeAttenuation
        transparent
        opacity={0.55}
        depthWrite={false}
        blending={THREE.AdditiveBlending}
        toneMapped={false}
      />
    </points>
  );
}
