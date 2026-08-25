import { useThemeStore } from '@/stores/theme-store';
import { cn } from '@/lib/utils';

/**
 * The DeployLane lockup, resolved for the active theme.
 *
 * The supplied logo has a near-white "Deploy" wordmark, which disappears against the
 * light canvas — so the light theme uses an ink-on-transparent variant of the same
 * lockup. The icon tile stays dark in both, because it is the brand mark rather than a
 * themed surface.
 */
export function BrandLogo({ className }: { className?: string }) {
  const theme = useThemeStore((state) => state.theme);

  return (
    <img
      src={theme === 'light' ? '/brand/deploylane-logo-light.svg' : '/brand/deploylane-logo.png'}
      alt="DeployLane"
      className={cn('w-auto object-contain', className)}
    />
  );
}
