import { useEffect, useRef, useState } from 'react';

/**
 * Reports whether an element is currently within (or near) the viewport, via
 * IntersectionObserver.
 *
 * Used to pause expensive continuously-animating sections (the 3D infrastructure
 * scene, the deployment ticker) while they are scrolled off screen, so they cost
 * nothing when the user isn't looking at them. A positive rootMargin keeps the
 * animation running slightly before it scrolls into view, avoiding a visible
 * "start" when it appears.
 */
export function useInView<T extends HTMLElement = HTMLDivElement>(
  rootMargin = '200px',
): { ref: React.RefObject<T | null>; inView: boolean } {
  const ref = useRef<T>(null);
  // Default to true so the content is never hidden if IntersectionObserver is unavailable.
  const [inView, setInView] = useState(true);

  useEffect(() => {
    const element = ref.current;
    if (!element || typeof IntersectionObserver === 'undefined') return;

    const observer = new IntersectionObserver(
      (entries) => {
        setInView(entries[0]?.isIntersecting ?? true);
      },
      { rootMargin },
    );

    observer.observe(element);
    return () => observer.disconnect();
  }, [rootMargin]);

  return { ref, inView };
}
