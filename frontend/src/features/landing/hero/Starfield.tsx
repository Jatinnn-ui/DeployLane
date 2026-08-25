import { useFrame } from '@react-three/fiber';
import { useEffect, useMemo, useRef } from 'react';
import * as THREE from 'three';

const LIME_RGB = new THREE.Color('#a8f000');
const STAR_RGB = new THREE.Color('#e8efe4');

/**
 * Deep-space backdrop behind the cube.
 *
 * One `<points>` object for the whole field, with per-vertex colour so the occasional lime
 * mote mixes in without needing a second draw call. Points are pushed behind the cube on Z
 * and spread wide on X/Y, which is what makes the hero read as depth rather than as a flat
 * dark panel.
 */
export function Starfield({
  reducedMotion = false,
  count = 260,
}: {
  reducedMotion?: boolean;
  count?: number;
}) {
  const points = useRef<THREE.Points>(null);

  const geometry = useMemo(() => {
    const positions = new Float32Array(count * 3);
    const colors = new Float32Array(count * 3);
    const color = new THREE.Color();

    for (let i = 0; i < count; i += 1) {
      positions[i * 3] = (Math.random() - 0.5) * 18;
      positions[i * 3 + 1] = (Math.random() - 0.5) * 11;
      // Kept behind the subject so stars never punch through the cube.
      positions[i * 3 + 2] = -3 - Math.random() * 9;

      // Roughly one in six is lime; the rest are near-white at varying brightness.
      color.copy(Math.random() < 0.16 ? LIME_RGB : STAR_RGB);
      color.multiplyScalar(0.35 + Math.random() * 0.65);

      colors[i * 3] = color.r;
      colors[i * 3 + 1] = color.g;
      colors[i * 3 + 2] = color.b;
    }

    const buffer = new THREE.BufferGeometry();
    buffer.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    buffer.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    return buffer;
  }, [count]);

  useEffect(() => () => geometry.dispose(), [geometry]);

  useFrame((_, delta) => {
    if (reducedMotion || !points.current) return;
    // A very slow drift. Enough to feel alive, slow enough not to pull the eye.
    points.current.rotation.z += Math.min(delta, 0.05) * 0.006;
  });

  return (
    <points ref={points} geometry={geometry}>
      <pointsMaterial
        size={0.045}
        sizeAttenuation
        vertexColors
        transparent
        opacity={0.9}
        depthWrite={false}
        toneMapped={false}
      />
    </points>
  );
}
