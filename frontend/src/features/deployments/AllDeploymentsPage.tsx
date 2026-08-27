import { Rocket } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useProjects } from '@/api/projects';
import { Badge } from '@/components/ui/badge';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/ui/feedback';
import { PageHeader } from '@/components/ui/page';
import { DeploymentStatusIndicator } from '@/components/DeploymentStatusBadge';
import { formatDuration, relativeTime, truncate } from '@/lib/utils';

/**
 * Cross-project deployment overview.
 *
 * Built from the latest-deployment snapshot the projects endpoint already returns, so this page costs one
 * request rather than one per project.
 */
export function AllDeploymentsPage() {
  const { data, isLoading, error, refetch } = useProjects();

  const rows = (data?.items ?? [])
    .filter((project) => project.latestDeployment)
    .sort((a, b) =>
      (b.latestDeployment?.createdAt ?? '').localeCompare(a.latestDeployment?.createdAt ?? ''),
    );

  return (
    <div className="space-y-6">
      <PageHeader
        title="Deployments"
        description="The most recent deployment of every project."
      />

      <Card>
        <CardHeader title="Latest per project" icon={<Rocket className="h-4 w-4" />} />
        <CardBody className="p-0">
          {isLoading ? (
            <SkeletonRows rows={5} className="p-4" />
          ) : error ? (
            <div className="p-4">
              <ErrorState error={error} onRetry={() => void refetch()} />
            </div>
          ) : rows.length === 0 ? (
            <EmptyState
              icon={Rocket}
              title="Nothing deployed yet"
              description="Import a repository and deploy it to see it here."
            />
          ) : (
            <ul className="divide-y divide-border-subtle">
              {rows.map((project) => {
                const deployment = project.latestDeployment!;
                return (
                  <li key={project.id}>
                    <Link
                      to={`/deployments/${deployment.deploymentId}`}
                      className="flex items-center gap-4 px-4 py-3 transition-colors hover:bg-surface-hover"
                    >
                      <div className="min-w-0 flex-1">
                        <p className="flex items-center gap-2 truncate text-[13px] font-medium text-content-primary">
                          {project.name}
                          <span className="font-mono text-[11px] font-normal text-content-muted">
                            #{deployment.deploymentNumber}
                          </span>
                          {project.frameworkLabel ? <Badge>{project.frameworkLabel}</Badge> : null}
                        </p>
                        <p className="mt-0.5 truncate text-[12px] text-content-muted">
                          {deployment.branch} · {truncate(deployment.commitMessage, 70) || 'no commit message'}
                        </p>
                      </div>
                      <div className="w-36 shrink-0">
                        <DeploymentStatusIndicator status={deployment.status} />
                      </div>
                      <div className="hidden w-24 shrink-0 text-right sm:block">
                        <p className="font-mono text-[11px] tabular-nums text-content-secondary">
                          {formatDuration(deployment.durationMs)}
                        </p>
                        <p className="text-[11px] text-content-muted">
                          {relativeTime(deployment.createdAt)}
                        </p>
                      </div>
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
