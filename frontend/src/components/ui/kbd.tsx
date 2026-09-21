import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

/**
 * Keyboard shortcut indicator.
 *
 * Renders one or more keys in the system's monospace font with a subtle
 * raised appearance that matches the design system's surface/border tokens.
 * Use a `+` separator between keys for combos: `<Kbd>⌘+K</Kbd>`.
 *
 * The component splits on `+` automatically so each key gets its own pill,
 * or pass children as-is for a single key.
 */
export function Kbd({
  children,
  className,
  ...props
}: HTMLAttributes<HTMLElement>) {
  const raw = typeof children === 'string' ? children : null;
  const keys = raw ? raw.split('+').map((k) => k.trim()) : null;

  if (keys && keys.length > 1) {
    return (
      <span className={cn('inline-flex items-center gap-0.5', className)} {...props}>
        {keys.map((key, i) => (
          <kbd
            key={i}
            className="inline-flex h-5 min-w-5 items-center justify-center rounded-[5px] border border-border-strong bg-surface px-1.5 font-mono text-[11px] font-medium leading-none text-content-secondary shadow-[0_1px_0_var(--dl-border-strong)]"
          >
            {key}
          </kbd>
        ))}
      </span>
    );
  }

  return (
    <kbd
      className={cn(
        'inline-flex h-5 min-w-5 items-center justify-center rounded-[5px] border border-border-strong bg-surface px-1.5 font-mono text-[11px] font-medium leading-none text-content-secondary shadow-[0_1px_0_var(--dl-border-strong)]',
        className,
      )}
      {...props}
    >
      {children}
    </kbd>
  );
}
