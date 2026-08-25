import type { SVGProps } from 'react';

/**
 * LinkedIn mark.
 *
 * Inlined for the same reason as the GitHub mark: lucide dropped every brand glyph in its 1.x
 * line, so any brand icon taken from an icon set is one dependency bump away from vanishing.
 */
export function LinkedinIcon({ className, ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
      {...props}
    >
      <path d="M3.11 1.4a1.71 1.71 0 1 0 0 3.42 1.71 1.71 0 0 0 0-3.42ZM1.4 6.11h3.42V14.6H1.4V6.11Zm5.13 0h3.28v1.16h.05c.46-.83 1.57-1.37 2.7-1.37 2.29 0 2.84 1.4 2.84 3.66V14.6h-3.42v-4.4c0-.98-.36-1.65-1.24-1.65-.86 0-1.37.58-1.59 1.13-.08.2-.1.47-.1.75v4.17H6.53V6.11Z" />
    </svg>
  );
}
