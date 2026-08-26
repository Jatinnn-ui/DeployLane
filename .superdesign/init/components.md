# Shared UI primitives

React 19 + TypeScript. Custom Tailwind/CVA primitives.

## `frontend/src/components/ui/button.tsx`

```tsx
import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

/**
 * Pill silhouette in every variant, per the design system.
 *
 * `primary` is the only lime fill on a screen: lime directs attention rather than
 * decorating, so the secondary action is a thin outline instead of a second solid fill.
 * Text on lime is always `on-accent` (dark ink) because lime stays bright in both themes.
 */
const buttonVariants = cva(
  'inline-flex select-none items-center justify-center gap-2 whitespace-nowrap rounded-full text-[14px] font-semibold leading-none transition-[background-color,border-color,color,box-shadow,opacity,transform] duration-200 disabled:pointer-events-none disabled:opacity-45 active:translate-y-px',
  {
    variants: {
      variant: {
        primary: 'bg-accent text-on-accent hover:bg-accent-hover',
        secondary:
          'border border-border-strong bg-transparent text-content-primary hover:bg-surface-hover',
        ghost:
          'border border-transparent text-content-secondary hover:bg-surface-hover hover:text-content-primary',
        danger:
          'border border-danger-border bg-danger-soft text-danger-foreground hover:bg-danger hover:text-content-inverse',
        outline:
          'border border-border-subtle bg-transparent text-content-primary hover:border-border-strong hover:bg-surface',
        link: 'h-auto rounded-none p-0 text-link underline-offset-4 hover:underline',
        lime: 'bg-accent text-on-accent hover:bg-accent-hover',
        /** Solid high-contrast fill: near-white on dark, near-black on light. */
        ink: 'bg-ink-deep text-content-inverse hover:opacity-90',
      },
      size: {
        sm: 'h-9 px-4 text-[13px]',
        md: 'h-11 px-5',
        lg: 'h-12 px-6 text-[15px]',
        icon: 'h-9 w-9 p-0',
      },
    },
    defaultVariants: { variant: 'secondary', size: 'md' },
  },
);

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof buttonVariants> & { loading?: boolean };

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, loading, disabled, children, ...props }, ref) => (
    <button
      ref={ref}
      className={cn(buttonVariants({ variant, size }), className)}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : null}
      {children}
    </button>
  ),
);
Button.displayName = 'Button';

export { buttonVariants };
```

