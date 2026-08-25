import * as THREE from 'three';

export const LIME = '#a8f000';
export const LIME_BRIGHT = '#b7ff19';
export const SUCCESS = '#43dd82';

/**
 * One material set, created once and shared by every mesh in the hero.
 *
 * Creating materials inline per-mesh is the usual cause of a heavy R3F scene: each unique
 * material is a separate shader program and a separate draw call. Thirty-odd meshes sharing
 * seven materials keeps the whole composition cheap.
 *
 * `toneMapped: false` on the emissive materials is what makes the lime read as genuinely
 * luminous without a bloom pass — tone mapping would otherwise pull the bright values back
 * toward the dark end of the scene.
 */
export interface HeroMaterials {
  body: THREE.MeshStandardMaterial;
  bodyDark: THREE.MeshStandardMaterial;
  panel: THREE.MeshStandardMaterial;
  trim: THREE.MeshStandardMaterial;
  edge: THREE.MeshStandardMaterial;
  chevron: THREE.MeshStandardMaterial;
  status: THREE.MeshStandardMaterial;
  dispose: () => void;
}

export function createHeroMaterials(): HeroMaterials {
  const body = new THREE.MeshStandardMaterial({
    color: '#14181a',
    metalness: 0.68,
    roughness: 0.32,
  });

  const bodyDark = new THREE.MeshStandardMaterial({
    color: '#0c0f10',
    metalness: 0.6,
    roughness: 0.42,
  });

  const panel = new THREE.MeshStandardMaterial({
    color: '#101314',
    metalness: 0.45,
    roughness: 0.58,
  });

  const trim = new THREE.MeshStandardMaterial({
    color: '#2b3133',
    metalness: 0.85,
    roughness: 0.26,
  });

  // Edge lighting along the cube's seams: lime, bright, but below the chevron.
  const edge = new THREE.MeshStandardMaterial({
    color: LIME,
    emissive: LIME,
    emissiveIntensity: 2.2,
    toneMapped: false,
  });

  // The brand mark itself, the brightest thing in the scene.
  const chevron = new THREE.MeshStandardMaterial({
    color: LIME_BRIGHT,
    emissive: LIME_BRIGHT,
    emissiveIntensity: 3.4,
    toneMapped: false,
  });

  const status = new THREE.MeshStandardMaterial({
    color: SUCCESS,
    emissive: SUCCESS,
    emissiveIntensity: 2.8,
    toneMapped: false,
  });

  const all = [body, bodyDark, panel, trim, edge, chevron, status];

  return {
    body,
    bodyDark,
    panel,
    trim,
    edge,
    chevron,
    status,
    dispose: () => all.forEach((material) => material.dispose()),
  };
}
