import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

const buttonVariants = cva(
  'inline-flex select-none items-center justify-center gap-2 whitespace-nowrap rounded-[12px] text-[14px] font-medium leading-none transition-[background-color,border-color,color,box-shadow,opacity,transform] duration-300 disabled:pointer-events-none disabled:opacity-45 active:scale-[0.97]',
  {
    variants: {
      variant: {
        primary:
          'bg-accent-deep text-content-primary hover:bg-[#b5c30e]',
        secondary:
          'bg-ink-deep text-content-inverse border border-transparent hover:bg-[#282c35]',
        ghost:
          'text-content-secondary border border-transparent hover:bg-surface-hover hover:text-content-primary',
        danger:
          'bg-danger-soft text-danger-foreground border border-danger-border hover:bg-danger hover:text-content-inverse',
        outline:
          'border border-border-subtle bg-transparent text-content-primary hover:border-border-strong hover:bg-surface',
        link: 'h-auto p-0 text-content-primary underline-offset-4 hover:underline',
        lime: 'bg-accent text-ink-deep rounded-[12px] hover:bg-accent-hover',
      },
      size: {
        sm: 'h-[34px] px-3 text-[13px]',
        md: 'h-[40px] px-4',
        lg: 'h-[47px] px-[18px] text-[15px]',
        icon: 'h-[34px] w-[34px] p-0',
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
