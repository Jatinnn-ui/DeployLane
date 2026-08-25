import { Moon, Sun } from 'lucide-react';
import { useThemeStore } from '@/stores/theme-store';
import { cn } from '@/lib/utils';

/**
 * Switches between the light and dark variants of the design system.
 *
 * Both icons are rendered and cross-faded rather than swapped conditionally, so the
 * control never changes size mid-transition and the surrounding toolbar does not shift.
 */
export function ThemeToggle({ className }: { className?: string }) {
  const theme = useThemeStore((state) => state.theme);
  const toggleTheme = useThemeStore((state) => state.toggleTheme);
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      title={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      className={cn(
        'relative inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full',
        'border border-border-subtle bg-surface text-content-secondary',
        'transition-colors duration-200',
        'hover:border-accent-border hover:bg-surface-hover hover:text-content-primary',
        className,
      )}
    >
      <Sun
        className={cn(
          'absolute h-4 w-4 transition-all duration-200',
          isDark ? 'scale-75 opacity-0' : 'scale-100 opacity-100',
        )}
        aria-hidden="true"
      />
      <Moon
        className={cn(
          'absolute h-4 w-4 transition-all duration-200',
          isDark ? 'scale-100 opacity-100' : 'scale-75 opacity-0',
        )}
        aria-hidden="true"
      />
    </button>
  );
}
