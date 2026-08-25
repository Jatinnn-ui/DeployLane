import { GithubIcon } from '@/components/icons/GithubIcon';
import { ExternalLink, GitBranch, GitCommitHorizontal } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { DeploymentStatusIndicator } from '@/components/DeploymentStatusBadge';
import { formatDuration, relativeTime, truncate } from '@/lib/utils';
import type { ProjectSummary } from '@/types/api';

/**
 * Project card for the dashboard grid.
 *
 * Shows exactly what someone scanning a list of services needs: is it healthy, what is deployed, from
 * which branch and commit, and how long ago. Everything else belongs on the project page.
 */
export function ProjectCard({ project }: { project: ProjectSummary }) {
  const latest = project.latestDeployment;

  return (
    <Link
      to={`/projects/${project.id}`}
      className="group flex flex-col rounded-xl border border-border-subtle bg-surface p-4 transition-all hover:border-border-strong hover:bg-surface-raised"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-[14px] font-semibold text-content-primary">{project.name}</h3>
          <p className="mt-0.5 flex items-center gap-1.5 truncate text-[12px] text-content-muted">
            <GithubIcon className="h-3 w-3 shrink-0" aria-hidden="true" />
            {project.repository.fullName}
          </p>
        </div>
        {latest ? (
          <DeploymentStatusIndicator status={latest.status} className="shrink-0" />
        ) : (
          <Badge>Never deployed</Badge>
        )}
      </div>

      <div className="mt-4 space-y-2 text-[12px]">
        {latest?.deploymentUrl ? (
          <span className="flex items-center gap-1.5 truncate font-mono text-[11px] text-accent">
            {latest.deploymentUrl.replace(/^https?:\/\//, '')}
            <ExternalLink className="h-3 w-3 shrink-0 opacity-0 transition-opacity group-hover:opacity-100" />
          </span>
        ) : (
          <span className="font-mono text-[11px] text-content-muted">no live URL</span>
        )}

        <div className="flex items-center gap-3 text-content-secondary">
          <span className="inline-flex min-w-0 items-center gap-1.5">
            <GitBranch className="h-3 w-3 shrink-0" aria-hidden="true" />
            <span className="truncate">{project.productionBranch ?? '-'}</span>
          </span>
          {latest?.commitSha ? (
            <span className="inline-flex items-center gap-1.5 font-mono text-[11px]">
              <GitCommitHorizontal className="h-3 w-3" aria-hidden="true" />
              {latest.commitSha}
            </span>
          ) : null}
        </div>

        {latest?.commitMessage ? (
          <p className="truncate text-content-muted">{truncate(latest.commitMessage, 60)}</p>
        ) : null}
      </div>

      <div className="mt-4 flex items-center justify-between border-t border-border-subtle pt-3 text-[11px] text-content-muted">
        <span>
          {latest ? (
            <>
              #{latest.deploymentNumber} · {relativeTime(latest.createdAt)}
            </>
          ) : (
            'Deploy to get a URL'
          )}
        </span>
        {latest?.durationMs ? <span>{formatDuration(latest.durationMs)}</span> : null}
      </div>
    </Link>
  );
}
