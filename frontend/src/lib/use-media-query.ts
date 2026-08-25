import { useEffect, useState } from 'react';

/**
 * Subscribes to a CSS media query from JS.
 *
 * Used where a breakpoint has to change *behaviour* rather than styling — the hero drops
 * geometry and particles on small screens, which is not something a CSS class can express.
 * Anything purely visual should stay in Tailwind's responsive variants.
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia(query).matches;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;

    const list = window.matchMedia(query);
    setMatches(list.matches);

    function handleChange(event: MediaQueryListEvent) {
      setMatches(event.matches);
    }

    list.addEventListener('change', handleChange);
    return () => list.removeEventListener('change', handleChange);
  }, [query]);

  return matches;
}

/** True when the visitor has asked the OS to minimise animation. */
export function usePrefersReducedMotion(): boolean {
  return useMediaQuery('(prefers-reduced-motion: reduce)');
}
