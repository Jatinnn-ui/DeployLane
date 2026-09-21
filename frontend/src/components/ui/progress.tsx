import { forwardRef, type HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

export interface ProgressProps extends HTMLAttributes<HTMLDivElement> {
  /** 0–100 percentage. When `null` or `undefined`, renders an indeterminate shimmer. */
  value?: number | null;
  /** Visual tone controlling the fill colour. */
  tone?: 'accent' | 'success' | 'warning' | 'danger' | 'info';
  /** Height preset. */
  size?: 'sm' | 'md' | 'lg';
  /** Accessible label describing what is progressing. */
  label?: string;
}

const TONE_FILL: Record<NonNullable<ProgressProps['tone']>, string> = {
  accent: 'bg-accent',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  info: 'bg-info',
};

const SIZE_TRACK: Record<NonNullable<ProgressProps['size']>, string> = {
  sm: 'h-1',
  md: 'h-1.5',
  lg: 'h-2.5',
};

/**
 * Progress / ProgressBar.
 *
 * Determinate when `value` is a number (0–100); indeterminate (pulsing shimmer)
 * otherwise. Uses the design system's surface token for the track and accent or
 * status tones for the fill, with pill-shaped geometry matching --radius-pill.
 */
export const Progress = forwardRef<HTMLDivElement, ProgressProps>(
  ({ value, tone = 'accent', size = 'md', label, className, ...props }, ref) => {
    const determinate = typeof value === 'number';
    const clampedValue = determinate ? Math.max(0, Math.min(100, value)) : 0;

    return (
      <div
        ref={ref}
        role="progressbar"
        aria-valuenow={determinate ? clampedValue : undefined}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label}
        className={cn(
          'w-full overflow-hidden rounded-full bg-surface',
          SIZE_TRACK[size],
          className,
        )}
        {...props}
      >
        <div
          className={cn(
            'h-full rounded-full transition-[width] duration-300 ease-[var(--ease-default)]',
            TONE_FILL[tone],
            !determinate && 'w-full animate-shimmer',
          )}
          style={determinate ? { width: `${clampedValue}%` } : undefined}
        />
      </div>
    );
  },
);
Progress.displayName = 'Progress';
