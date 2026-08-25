import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-[11px] font-medium leading-5',
  {
    variants: {
      tone: {
        neutral: 'border-border-subtle bg-surface text-content-secondary',
        accent: 'border-accent-border bg-accent-soft text-ink-deep',
        success: 'border-success-border bg-success-soft text-success-foreground',
        warning: 'border-warning-border bg-warning-soft text-warning-foreground',
        danger: 'border-danger-border bg-danger-soft text-danger-foreground',
        info: 'border-info-border bg-info-soft text-info-foreground',
      },
    },
    defaultVariants: { tone: 'neutral' },
  },
);

type BadgeProps = React.HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeVariants>;

export function Badge({ className, tone, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}

const DOT_TONE: Record<string, string> = {
  neutral: 'bg-content-muted',
  accent: 'bg-accent-deep',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  info: 'bg-info',
};

export function StatusDot({
  tone = 'neutral',
  pulse = false,
  className,
}: {
  tone?: keyof typeof DOT_TONE;
  pulse?: boolean;
  className?: string;
}) {
  return (
    <span
      className={cn(
        'inline-block h-2 w-2 rounded-full',
        DOT_TONE[tone] ?? DOT_TONE.neutral,
        pulse && 'animate-[pulse-ring_2s_cubic-bezier(0.4,0,0.6,1)_infinite]',
        className,
      )}
      aria-hidden="true"
    />
  );
}
