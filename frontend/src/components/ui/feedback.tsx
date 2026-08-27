import { AlertTriangle, Loader2 } from 'lucide-react';
import type { ComponentType, ReactNode } from 'react';
import { ApiError } from '@/lib/api-client';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';

export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn('animate-shimmer rounded-md bg-surface', className)}
      aria-hidden="true"
    />
  );
}

export function SkeletonRows({ rows = 3, className }: { rows?: number; className?: string }) {
  return (
    <div className={cn('space-y-3', className)} role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="flex items-center gap-3">
          <Skeleton className="h-9 w-9 rounded-md" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-3 w-1/3" />
            <Skeleton className="h-3 w-1/2" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <Loader2
      className={cn('h-4 w-4 animate-spin text-content-muted', className)}
      role="status"
      aria-label="Loading"
    />
  );
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
}: {
  icon?: ComponentType<{ className?: string }>;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-3 px-6 py-14 text-center',
        className,
      )}
    >
      {Icon ? (
        <div className="flex h-11 w-11 items-center justify-center rounded-lg border border-border-subtle bg-surface">
          <Icon className="h-5 w-5 text-content-muted" aria-hidden="true" />
        </div>
      ) : null}
      <div className="space-y-1">
        <h3 className="text-[15px] font-medium tracking-[-0.3px] text-content-primary">{title}</h3>
        {description ? (
          <p className="max-w-sm text-[13px] leading-relaxed text-content-secondary">{description}</p>
        ) : null}
      </div>
      {action ? <div className="mt-1">{action}</div> : null}
    </div>
  );
}

export function ErrorState({
  error,
  onRetry,
  className,
  compact = false,
}: {
  error: unknown;
  onRetry?: () => void;
  className?: string;
  compact?: boolean;
}) {
  const apiError = error instanceof ApiError ? error : null;
  const message =
    apiError?.message ?? (error instanceof Error ? error.message : 'An unexpected error occurred');

  return (
    <div
      role="alert"
      className={cn(
        'flex items-start gap-3 rounded-lg border border-danger-border bg-danger-soft px-4 py-3.5',
        compact ? 'text-[12px]' : 'text-[13px]',
        className,
      )}
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-danger" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="font-medium text-danger-foreground">{message}</p>
        {apiError ? (
          <p className="mt-1 font-mono text-[11px] text-content-muted">
            {apiError.code}
            {apiError.requestId ? ` · request ${apiError.requestId}` : ''}
          </p>
        ) : null}
      </div>
      {onRetry ? (
        <Button variant="ghost" size="sm" onClick={onRetry}>
          Retry
        </Button>
      ) : null}
    </div>
  );
}

export function InlineNotice({
  tone = 'info',
  children,
  className,
}: {
  tone?: 'info' | 'warning';
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'rounded-md border px-4 py-3 text-[13px] leading-relaxed',
        tone === 'info'
          ? 'border-info-border bg-info-soft text-info-foreground'
          : 'border-warning-border bg-warning-soft text-warning-foreground',
        className,
      )}
    >
      {children}
    </div>
  );
}
