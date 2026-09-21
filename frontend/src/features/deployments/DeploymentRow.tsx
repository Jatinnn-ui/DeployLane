import { GitBranch, GitCommitHorizontal } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { DeploymentStatusIndicator } from '@/components/DeploymentStatusBadge';
import { formatDuration, humanizeEnum, relativeTime, truncate } from '@/lib/utils';
import type { DeploymentSummary } from '@/types/api';

/** One row of deployment history. Dense on purpose: this list is scanned, not read. */
export function DeploymentRow({ deployment }: { deployment: DeploymentSummary }) {
  return (
    <li>
      <Link
        to={`/deployments/${deployment.id}`}
        className="flex items-center gap-2.5 px-3 py-3 transition-colors hover:bg-surface-hover sm:gap-4 sm:px-4"
      >
        <div className="w-12 shrink-0">
          <span className="font-mono text-[13px] tabular-nums text-content-primary">
            #{deployment.deploymentNumber}
          </span>
        </div>

        <div className="w-auto shrink-0 sm:w-36">
          <DeploymentStatusIndicator status={deployment.status} />
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1 text-[12px] text-content-secondary">
              <GitBranch className="h-3 w-3 shrink-0" aria-hidden="true" />
              {deployment.branch}
            </span>
            {deployment.shortCommitSha ? (
              <span className="inline-flex items-center gap-1 font-mono text-[11px] text-content-muted">
                <GitCommitHorizontal className="h-3 w-3" aria-hidden="true" />
                {deployment.shortCommitSha}
              </span>
            ) : null}
            {deployment.promoted ? <Badge tone="success">live</Badge> : null}
          </div>
          <p className="mt-0.5 truncate text-[12px] text-content-muted">
            {deployment.status === 'FAILED' && deployment.failureMessage
              ? truncate(deployment.failureMessage, 90)
              : (truncate(deployment.commitMessage, 90) || humanizeEnum(deployment.triggerType))}
          </p>
        </div>

        <div className="hidden w-24 shrink-0 text-right sm:block">
          <p className="font-mono text-[11px] tabular-nums text-content-secondary">
            {formatDuration(deployment.durationMs)}
          </p>
          <p className="text-[11px] text-content-muted">{relativeTime(deployment.createdAt)}</p>
        </div>
      </Link>
    </li>
  );
}
