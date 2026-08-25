import { Check, CircleDashed, Loader2, MinusCircle, X } from 'lucide-react';
import { cn, formatDuration } from '@/lib/utils';
import type { DeploymentStep } from '@/types/api';

/**
 * Vertical pipeline timeline.
 *
 * Each row is a real persisted step with its own duration, so "Application built - 31s" is measured rather
 * than inferred from log timestamps. A failed step keeps its message inline, which is where the eye goes
 * first when something breaks.
 */
export function DeploymentTimeline({ steps }: { steps: DeploymentStep[] }) {
  if (steps.length === 0) {
    return (
      <p className="px-1 py-6 text-center text-[13px] text-content-muted">
        The pipeline has not started yet.
      </p>
    );
  }

  return (
    <ol className="space-y-0">
      {steps.map((step, index) => {
        const isLast = index === steps.length - 1;
        return (
          <li key={step.step} className="relative flex gap-3 pb-4 last:pb-0">
            {!isLast ? (
              <span
                className={cn(
                  'absolute left-[11px] top-6 h-[calc(100%-1rem)] w-px',
                  step.status === 'SUCCEEDED' ? 'bg-success/40' : 'bg-border-subtle',
                )}
                aria-hidden="true"
              />
            ) : null}

            <StepIcon status={step.status} />

            <div className="min-w-0 flex-1 pt-0.5">
              <div className="flex items-baseline justify-between gap-3">
                <p
                  className={cn(
                    'text-[13px]',
                    step.status === 'PENDING' ? 'text-content-muted' : 'text-content-primary',
                    step.status === 'FAILED' && 'font-medium text-[#f9a8a8]',
                  )}
                >
                  {step.label}
                </p>
                {step.durationMs !== null && step.durationMs !== undefined && step.status !== 'PENDING' ? (
                  <span className="shrink-0 font-mono text-[11px] tabular-nums text-content-muted">
                    {formatDuration(step.durationMs)}
                  </span>
                ) : null}
              </div>

              {step.detail ? (
                <p
                  className={cn(
                    'mt-0.5 break-words text-[12px]',
                    step.status === 'FAILED'
                      ? 'font-mono text-[#f77272]'
                      : step.status === 'SKIPPED'
                        ? 'text-content-muted'
                        : 'text-content-secondary',
                  )}
                >
                  {step.detail}
                </p>
              ) : null}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

function StepIcon({ status }: { status: DeploymentStep['status'] }) {
  const base = 'relative z-10 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border';
  switch (status) {
    case 'SUCCEEDED':
      return (
        <span className={cn(base, 'border-success/40 bg-success-soft text-success')}>
          <Check className="h-3 w-3" aria-label="succeeded" />
        </span>
      );
    case 'RUNNING':
      return (
        <span className={cn(base, 'border-accent-border bg-accent-soft text-accent')}>
          <Loader2 className="h-3 w-3 animate-spin" aria-label="running" />
        </span>
      );
    case 'FAILED':
      return (
        <span className={cn(base, 'border-danger/40 bg-danger-soft text-danger')}>
          <X className="h-3 w-3" aria-label="failed" />
        </span>
      );
    case 'SKIPPED':
      return (
        <span className={cn(base, 'border-border-subtle bg-surface text-content-muted')}>
          <MinusCircle className="h-3 w-3" aria-label="skipped" />
        </span>
      );
    case 'CANCELLED':
      return (
        <span className={cn(base, 'border-warning/40 bg-warning-soft text-warning')}>
          <X className="h-3 w-3" aria-label="cancelled" />
        </span>
      );
    default:
      return (
        <span className={cn(base, 'border-border-subtle bg-surface text-content-muted')}>
          <CircleDashed className="h-3 w-3" aria-label="pending" />
        </span>
      );
  }
}
