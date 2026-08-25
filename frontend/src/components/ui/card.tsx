import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'overflow-hidden rounded-[18px] border border-border-subtle bg-surface-alt shadow-[rgba(255,255,255,0.05)_0px_0px_0px_1px_inset] transition-[border-color,background-color] duration-300',
        className,
      )}
      {...props}
    />
  );
}

export function CardHeader({
  title,
  description,
  actions,
  icon,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  icon?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'flex items-start justify-between gap-4 border-b border-border-subtle px-5 py-4',
        className,
      )}
    >
      <div className="flex min-w-0 items-start gap-3">
        {icon ? <div className="mt-0.5 text-content-muted">{icon}</div> : null}
        <div className="min-w-0">
          <h4 className="truncate text-[15px] font-medium tracking-[-0.3px] text-content-primary">
            {title}
          </h4>
          {description ? (
            <p className="mt-1 text-[13px] leading-relaxed text-content-secondary">{description}</p>
          ) : null}
        </div>
      </div>
      {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export function CardBody({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-5 py-5', className)} {...props} />;
}

export function CardFooter({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'border-t border-border-subtle px-5 py-3.5 text-[13px] text-content-secondary',
        className,
      )}
      {...props}
    />
  );
}

export function MetaItem({
  label,
  value,
  mono = false,
  className,
}: {
  label: ReactNode;
  value: ReactNode;
  mono?: boolean;
  className?: string;
}) {
  return (
    <div className={cn('min-w-0', className)}>
      <dt className="text-[11px] font-medium uppercase tracking-wider text-content-muted">{label}</dt>
      <dd
        className={cn(
          'mt-1 truncate text-[13px] text-content-primary',
          mono && 'font-mono text-[12px]',
        )}
      >
        {value ?? '-'}
      </dd>
    </div>
  );
}
