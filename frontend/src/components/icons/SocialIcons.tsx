import type { SVGProps } from 'react';

/**
 * X and Discord marks.
 *
 * Inlined for the same reason as the GitHub and LinkedIn marks: lucide removed every brand
 * glyph in its 1.x line, so a brand icon pulled from an icon set is one dependency bump away
 * from disappearing. These two appear only in the marketing footer.
 */
export function XIcon({ className, ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
      {...props}
    >
      <path d="M9.29 7.04 14.4 1.2h-1.21L8.75 6.26 5.3 1.2H1.13l5.36 7.85L1.13 14.8h1.21l4.7-5.38 3.66 5.38h4.17L9.29 7.04Zm-.62.71-.72-1.04L2.79 2.1h1.86l3.4 4.98.72 1.04 4.36 6.4h-1.86L8.67 7.75Z" />
    </svg>
  );
}

export function DiscordIcon({ className, ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
      {...props}
    >
      <path d="M13.05 3.34A12.3 12.3 0 0 0 9.98 2.4l-.2.44a9.1 9.1 0 0 1 2.7 1.06 9.35 9.35 0 0 0-8.97 0A9.1 9.1 0 0 1 6.22 2.8L6.02 2.4a12.3 12.3 0 0 0-3.07.94C.98 6.3.44 9.2.71 12.06a12.4 12.4 0 0 0 3.76 1.9l.48-.75a8 8 0 0 1-1.3-.63l.32-.25a8.83 8.83 0 0 0 7.56 0l.32.25a8 8 0 0 1-1.3.63l.47.75a12.4 12.4 0 0 0 3.77-1.9c.32-3.31-.55-6.18-1.84-8.72ZM5.62 10.3c-.73 0-1.33-.67-1.33-1.5 0-.82.59-1.5 1.33-1.5s1.34.68 1.33 1.5c0 .83-.6 1.5-1.33 1.5Zm4.9 0c-.74 0-1.34-.67-1.34-1.5 0-.82.59-1.5 1.34-1.5.74 0 1.34.68 1.33 1.5 0 .83-.6 1.5-1.33 1.5Z" />
    </svg>
  );
}
