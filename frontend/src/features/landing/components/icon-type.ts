import type { ComponentType } from 'react';

/**
 * Icons on the landing page come from two places: lucide-react for generic glyphs, and local
 * inline components for brand marks, since lucide removed every brand icon in its 1.x line.
 *
 * This is the narrow prop surface both satisfy, so the two kinds can sit in the same array.
 */
export type IconComponent = ComponentType<{
  className?: string;
  strokeWidth?: number;
  'aria-hidden'?: boolean | 'true' | 'false';
}>;
