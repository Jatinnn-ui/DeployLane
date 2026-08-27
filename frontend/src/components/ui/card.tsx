import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'overflow-hidden rounded-card border border-border-subtle bg-surface-alt shadow-[var(--dl-shadow-inset-card)] transition-[border-color,background-color] duration-200',
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
          <h4 className="section-title truncate">
            {title}
          </h4>
          {description ? (
            <p className="body-small mt-1 text-content-secondary">{description}</p>
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
      <dt className="meta-label">{label}</dt>
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
