import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/feedback';
import { cn } from '@/lib/utils';

export function PageHeader({
  title,
  description,
  actions,
  eyebrow,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  eyebrow?: ReactNode;
  className?: string;
}) {
  return (
    <header
      className={cn(
        'flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between',
        className,
      )}
    >
      <div className="min-w-0">
        {eyebrow ? <div className="mb-2">{eyebrow}</div> : null}
        <h1 className="page-title">{title}</h1>
        {description ? <p className="page-description mt-1">{description}</p> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}

export function MetricCard({
  label,
  value,
  icon: Icon,
  detail,
  hint,
  loading = false,
  className,
  valueClassName,
}: {
  label: ReactNode;
  value: ReactNode;
  icon: LucideIcon;
  detail?: ReactNode;
  hint?: ReactNode;
  loading?: boolean;
  className?: string;
  valueClassName?: string;
}) {
  return (
    <Card className={cn('p-4', className)}>
      <div className="flex items-center justify-between gap-3">
        <p className="meta-label truncate">{label}</p>
        <Icon className="h-3.5 w-3.5 shrink-0 text-content-muted" aria-hidden="true" />
      </div>
      {loading ? (
        <Skeleton className="mt-2 h-7 w-12" />
      ) : (
        <div className="mt-1.5 flex min-w-0 items-baseline gap-2">
          <span className={cn('text-2xl font-semibold tabular-nums text-content-primary', valueClassName)}>
            {value}
          </span>
          {detail ? <div className="min-w-0">{detail}</div> : null}
        </div>
      )}
      {!loading && hint ? <p className="caption mt-0.5 text-content-muted">{hint}</p> : null}
    </Card>
  );
}

export function Pagination({
  page,
  totalPages,
  hasPrevious,
  hasNext,
  onPrevious,
  onNext,
}: {
  page: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
  onPrevious: () => void;
  onNext: () => void;
}) {
  return (
    <div className="flex w-full flex-wrap items-center justify-between gap-3">
      <span className="caption text-content-secondary">
        Page {page + 1} of {totalPages}
      </span>
      <div className="flex gap-2">
        <Button variant="secondary" size="sm" disabled={!hasPrevious} onClick={onPrevious}>
          Previous
        </Button>
        <Button variant="secondary" size="sm" disabled={!hasNext} onClick={onNext}>
          Next
        </Button>
      </div>
    </div>
  );
}
