import * as THREE from 'three';

export const LIME = '#a8f000';
export const LIME_BRIGHT = '#b7ff19';
export const SUCCESS = '#43dd82';

/**
 * One material set, created once and shared by every mesh in the server object.
 *
 * Creating materials inline per-mesh is the usual cause of a heavy R3F scene: each
 * unique material is a separate shader program and a separate draw call. Twenty-odd
 * meshes sharing six materials keeps the whole object cheap.
 *
 * `toneMapped: false` on the emissive materials is what makes the lime read as
 * genuinely luminous without a bloom pass — tone mapping would otherwise pull the
 * bright values back toward the dark end of the scene.
 */
export interface ServerMaterials {
  body: THREE.MeshStandardMaterial;
  panel: THREE.MeshStandardMaterial;
  trim: THREE.MeshStandardMaterial;
  glass: THREE.MeshStandardMaterial;
  seam: THREE.MeshStandardMaterial;
  status: THREE.MeshStandardMaterial;
  dispose: () => void;
}

export function createServerMaterials(): ServerMaterials {
  const body = new THREE.MeshStandardMaterial({
    color: '#15191a',
    metalness: 0.62,
    roughness: 0.38,
  });

  const panel = new THREE.MeshStandardMaterial({
    color: '#101314',
    metalness: 0.45,
    roughness: 0.6,
  });

  const trim = new THREE.MeshStandardMaterial({
    color: '#2b3133',
    metalness: 0.85,
    roughness: 0.28,
  });

  // Smoked black glass for the recessed front inset.
  const glass = new THREE.MeshStandardMaterial({
    color: '#0a0c0d',
    metalness: 0.5,
    roughness: 0.15,
  });

  const seam = new THREE.MeshStandardMaterial({
    color: LIME,
    emissive: LIME,
    emissiveIntensity: 2.4,
    toneMapped: false,
  });

  const status = new THREE.MeshStandardMaterial({
    color: SUCCESS,
    emissive: SUCCESS,
    emissiveIntensity: 2.8,
    toneMapped: false,
  });

  return {
    body,
    panel,
    trim,
    glass,
    seam,
    status,
    dispose: () => {
      [body, panel, trim, glass, seam, status].forEach((material) => material.dispose());
    },
  };
}
