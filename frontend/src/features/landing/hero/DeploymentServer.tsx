import { RoundedBox } from '@react-three/drei';
import { useFrame } from '@react-three/fiber';
import { useRef } from 'react';
import * as THREE from 'three';
import type { HeroMaterials } from './materials';

/**
 * The hero chassis is intentionally wider than it is tall: 2.7 : 2 : 2.3 maps to the
 * requested 1.35 : 1 : 1.15 proportions. The front is +Z and the visible right side is +X.
 */
const W = 2.7;
const H = 2;
const D = 2.3;
const FRONT = D / 2;
const RIGHT = W / 2;

interface DeploymentServerProps {
  materials: HeroMaterials;
  reducedMotion?: boolean;
  simplified?: boolean;
  active?: boolean;
}

/** Brand chevron constructed from geometry, so it remains sharp at every DPR. */
function Chevron({ material }: { material: THREE.Material }) {
  return (
    <group position={[-0.08, 0.06, FRONT + 0.102]} scale={1.7}>
      <mesh position={[-0.07, 0.11, 0]} rotation={[0, 0, -Math.PI / 3.45]} material={material}>
        <boxGeometry args={[0.075, 0.43, 0.045]} />
      </mesh>
      <mesh position={[-0.07, -0.11, 0]} rotation={[0, 0, Math.PI / 3.45]} material={material}>
        <boxGeometry args={[0.075, 0.43, 0.045]} />
      </mesh>
    </group>
  );
}

/** Tiny recessed fastener with a metallic centre and dark collar. */
function Fastener({ x, y, materials }: { x: number; y: number; materials: HeroMaterials }) {
  return (
    <group position={[x, y, FRONT + 0.105]} rotation={[Math.PI / 2, 0, 0]}>
      <mesh material={materials.bodyDark}>
        <cylinderGeometry args={[0.055, 0.055, 0.025, 16]} />
      </mesh>
      <mesh position={[0, 0.016, 0]} material={materials.trim}>
        <cylinderGeometry args={[0.022, 0.022, 0.012, 12]} />
      </mesh>
    </group>
  );
}

/** Front intake slots grouped into a deliberately quiet lower-left bank. */
function FrontVents({ material }: { material: THREE.Material }) {
  return (
    <group position={[-0.84, -0.55, FRONT + 0.104]}>
      {[-0.16, -0.08, 0, 0.08, 0.16].map((y) => (
        <mesh key={y} position={[0, y, 0]} material={material}>
          <boxGeometry args={[0.5, 0.025, 0.025]} />
        </mesh>
      ))}
    </group>
  );
}

/** Right-side thermal exhaust. A deep backing plate makes the slots read as recessed. */
function SideVents({ materials }: { materials: HeroMaterials }) {
  return (
    <group position={[RIGHT + 0.068, 0.02, 0]}>
      <mesh material={materials.bodyDark}>
        <boxGeometry args={[0.035, 0.92, 1.24]} />
      </mesh>
      {[-0.43, -0.215, 0, 0.215, 0.43].map((z) => (
        <mesh key={z} position={[0.024, 0, z]} material={materials.trim}>
          <boxGeometry args={[0.025, 0.64, 0.055]} />
        </mesh>
      ))}
    </group>
  );
}

/** Upper and lower illuminated seams wrap around the front and visible right side. */
function LightSeams({ material }: { material: THREE.Material }) {
  return (
    <>
      {[0.62, -0.78].map((y, index) => (
        <group key={y}>
          <mesh position={[0, y, FRONT + 0.098]} material={material}>
            <boxGeometry args={[W * 0.91, index === 0 ? 0.035 : 0.026, 0.03]} />
          </mesh>
          <mesh position={[RIGHT + 0.096, y, 0]} material={material}>
            <boxGeometry args={[0.03, index === 0 ? 0.035 : 0.026, D * 0.88]} />
          </mesh>
        </group>
      ))}
    </>
  );
}

/**
 * Premium DeployLane deployment server used only in the landing-page hero.
 *
 * It is a layered model rather than a decorated primitive: beveled structural shell,
 * separate top/front/right panels, two recessed bays, ventilation, fasteners, seams,
 * luminous perimeter strips, a geometry logo, and independent status indicators.
 */
export function DeploymentServer({
  materials,
  reducedMotion = false,
  simplified = false,
  active = true,
}: DeploymentServerProps) {
  const server = useRef<THREE.Group>(null);
  const status = useRef<THREE.Mesh>(null);

  useFrame(({ clock }) => {
    if (reducedMotion || !active) return;
    const t = clock.getElapsedTime();

    if (server.current) {
      // Six-second cycle, approximately ±4 px at the current camera framing.
      server.current.position.y = Math.sin((t * Math.PI * 2) / 6) * 0.04;
    }
    if (status.current) {
      status.current.scale.setScalar(0.78 + (Math.sin(t * 2.1) * 0.5 + 0.5) * 0.22);
    }
  });

  return (
    <group ref={server}>
      {/* Main graphite shell. Never pure black: key/fill lights need a surface to reveal. */}
      <RoundedBox args={[W, H, D]} radius={0.16} smoothness={5} material={materials.body} />

      {/* Raised top cap with a darker recessed centre. */}
      <RoundedBox
        args={[W * 0.92, 0.16, D * 0.9]}
        radius={0.1}
        smoothness={4}
        position={[0, H / 2 + 0.015, 0]}
        material={materials.trim}
      />
      <RoundedBox
        args={[W * 0.7, 0.055, D * 0.64]}
        radius={0.06}
        smoothness={3}
        position={[0, H / 2 + 0.105, 0]}
        material={materials.bodyDark}
      />

      {/* Front face frame and inset service panel. */}
      <RoundedBox
        args={[W * 0.9, H * 0.82, 0.14]}
        radius={0.1}
        smoothness={4}
        position={[0, -0.03, FRONT + 0.025]}
        material={materials.trim}
      />
      <RoundedBox
        args={[W * 0.84, H * 0.74, 0.12]}
        radius={0.08}
        smoothness={4}
        position={[0, -0.03, FRONT + 0.104]}
        material={materials.panel}
      />

      {/* A second shallow recess behind the logo gives the front real depth. */}
      <RoundedBox
        args={[1.02, 0.94, 0.04]}
        radius={0.08}
        smoothness={3}
        position={[0, 0.01, FRONT + 0.173]}
        material={materials.bodyDark}
      />

      {/* Visible right-side service panel. */}
      {!simplified && (
        <>
          <RoundedBox
            args={[0.12, H * 0.78, D * 0.86]}
            radius={0.08}
            smoothness={4}
            position={[RIGHT + 0.025, -0.04, 0]}
            material={materials.trim}
          />
          <RoundedBox
            args={[0.1, H * 0.7, D * 0.79]}
            radius={0.06}
            smoothness={3}
            position={[RIGHT + 0.098, -0.04, 0]}
            material={materials.panel}
          />
          <SideVents materials={materials} />
        </>
      )}

      <LightSeams material={materials.edge} />
      <Chevron material={materials.chevron} />
      <FrontVents material={materials.bodyDark} />

      {/* Face fasteners and a small lower control strip. */}
      {!simplified && (
        <>
          <Fastener x={-1.05} y={0.48} materials={materials} />
          <Fastener x={1.05} y={0.48} materials={materials} />
          <Fastener x={-1.05} y={-0.58} materials={materials} />
          <Fastener x={1.05} y={-0.58} materials={materials} />
          <mesh position={[0.58, -0.57, FRONT + 0.172]} material={materials.bodyDark}>
            <boxGeometry args={[0.34, 0.06, 0.03]} />
          </mesh>
          {[-0.1, 0, 0.1].map((x) => (
            <mesh
              key={x}
              position={[0.58 + x, -0.57, FRONT + 0.194]}
              material={x === 0.1 ? materials.status : materials.trim}
            >
              <sphereGeometry args={[0.018, 10, 10]} />
            </mesh>
          ))}
        </>
      )}

      {/* Primary live status at the requested bottom-right of the front panel. */}
      <mesh
        ref={status}
        position={[0.99, -0.58, FRONT + 0.192]}
        material={materials.status}
      >
        <sphereGeometry args={[0.055, 18, 18]} />
      </mesh>
    </group>
  );
}
