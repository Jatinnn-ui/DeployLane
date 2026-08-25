import { Activity, Cpu, GitBranch, HardDrive, Rocket, Timer } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import { useDeployments } from '@/api/deployments';
import { useActivityFeed } from '@/api/platform';
import { useProjectHealth } from '@/api/projects';
import { Badge } from '@/components/ui/badge';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { EmptyState, Skeleton, SkeletonRows } from '@/components/ui/feedback';
import { HealthIndicator } from '@/components/DeploymentStatusBadge';
import { ActivityList } from '@/features/activity/ActivityList';
import { DeploymentRow } from '@/features/deployments/DeploymentRow';
import { formatBytes, formatPercent, formatUptime, relativeTime } from '@/lib/utils';
import type { Project } from '@/types/api';

/** Project landing page: current state, resource usage and the last few deployments. */
export function ProjectOverviewPage() {
  const { project } = useOutletContext<{ project: Project }>();
  const { data: health, isLoading: healthLoading } = useProjectHealth(project.id);
  const { data: deployments, isLoading: deploymentsLoading } = useDeployments(project.id);
  const { data: activity } = useActivityFeed({ kind: 'project', id: project.id }, 0);

  const memoryPercent =
    health?.memoryBytes && health?.memoryLimitBytes
      ? (health.memoryBytes / health.memoryLimitBytes) * 100
      : null;

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Card className="p-4">
          <p className="text-[11px] font-medium uppercase tracking-wider text-content-muted">Status</p>
          {healthLoading ? (
            <Skeleton className="mt-2 h-5 w-24" />
          ) : (
            <div className="mt-1.5 space-y-1">
              <HealthIndicator status={health?.status} />
              <p className="text-[11px] text-content-muted">{health?.detail}</p>
            </div>
          )}
        </Card>

        <Card className="p-4">
          <p className="text-[11px] font-medium uppercase tracking-wider text-content-muted">
            Current deployment
          </p>
          {healthLoading ? (
            <Skeleton className="mt-2 h-5 w-16" />
          ) : health?.deploymentId ? (
            <Link
              to={`/deployments/${health.deploymentId}`}
              className="mt-1.5 block text-lg font-semibold tabular-nums text-content-primary hover:text-accent"
            >
              #{health.deploymentNumber}
              <span className="ml-2 font-mono text-[11px] font-normal text-content-muted">
                {health.commitSha ?? ''}
              </span>
            </Link>
          ) : (
            <p className="mt-1.5 text-[13px] text-content-muted">Nothing live</p>
          )}
        </Card>

        <Card className="p-4">
          <p className="text-[11px] font-medium uppercase tracking-wider text-content-muted">
            <Cpu className="mr-1 inline h-3 w-3" aria-hidden="true" />
            CPU
          </p>
          {healthLoading ? (
            <Skeleton className="mt-2 h-5 w-16" />
          ) : (
            <p className="mt-1.5 text-lg font-semibold tabular-nums text-content-primary">
              {formatPercent(health?.cpuPercent, 1)}
            </p>
          )}
        </Card>

        <Card className="p-4">
          <p className="text-[11px] font-medium uppercase tracking-wider text-content-muted">
            <HardDrive className="mr-1 inline h-3 w-3" aria-hidden="true" />
            Memory
          </p>
          {healthLoading ? (
            <Skeleton className="mt-2 h-5 w-24" />
          ) : (
            <>
              <p className="mt-1.5 text-lg font-semibold tabular-nums text-content-primary">
                {formatBytes(health?.memoryBytes)}
                <span className="ml-1 text-[12px] font-normal text-content-muted">
                  / {formatBytes(health?.memoryLimitBytes)}
                </span>
              </p>
              {memoryPercent !== null ? (
                <div className="mt-2 h-1 overflow-hidden rounded-full bg-surface-raised">
                  <div
                    className={
                      memoryPercent > 85
                        ? 'h-full bg-danger'
                        : memoryPercent > 65
                          ? 'h-full bg-warning'
                          : 'h-full bg-accent'
                    }
                    style={{ width: `${Math.min(memoryPercent, 100)}%` }}
                  />
                </div>
              ) : null}
            </>
          )}
        </Card>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
        <Card>
          <CardHeader
            title="Recent deployments"
            icon={<Rocket className="h-4 w-4" />}
            actions={
              <Link
                to={`/projects/${project.id}/deployments`}
                className="text-[12px] text-content-secondary transition-colors hover:text-content-primary"
              >
                View all
              </Link>
            }
          />
          <CardBody className="p-0">
            {deploymentsLoading ? (
              <SkeletonRows rows={4} className="p-4" />
            ) : !deployments || deployments.items.length === 0 ? (
              <EmptyState
                icon={Rocket}
                title="No deployments yet"
                description={`Deploy the ${project.environments[0]?.branch ?? project.defaultBranch} branch to get started.`}
                className="py-10"
              />
            ) : (
              <ul className="divide-y divide-border-subtle">
                {deployments.items.slice(0, 6).map((deployment) => (
                  <DeploymentRow key={deployment.id} deployment={deployment} />
                ))}
              </ul>
            )}
          </CardBody>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader title="Environments" icon={<GitBranch className="h-4 w-4" />} />
            <CardBody className="space-y-3">
              {project.environments.map((environment) => (
                <div key={environment.id} className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 text-[13px] text-content-primary">
                      {environment.name}
                      {environment.type === 'PRODUCTION' ? <Badge tone="accent">production</Badge> : null}
                    </p>
                    <p className="mt-0.5 font-mono text-[11px] text-content-muted">
                      {environment.branch} · {environment.variableCount} variables
                    </p>
                  </div>
                  <Badge tone={environment.autoDeployEnabled ? 'success' : 'neutral'}>
                    {environment.autoDeployEnabled ? 'auto deploy' : 'manual'}
                  </Badge>
                </div>
              ))}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Runtime" icon={<Timer className="h-4 w-4" />} />
            <CardBody>
              <dl className="grid grid-cols-2 gap-4">
                <div>
                  <dt className="text-[11px] uppercase tracking-wider text-content-muted">Uptime</dt>
                  <dd className="mt-1 text-[13px] tabular-nums text-content-primary">
                    {formatUptime(health?.uptimeSeconds)}
                  </dd>
                </div>
                <div>
                  <dt className="text-[11px] uppercase tracking-wider text-content-muted">Restarts</dt>
                  <dd className="mt-1 text-[13px] tabular-nums text-content-primary">
                    {health?.restartCount ?? '-'}
                  </dd>
                </div>
                <div>
                  <dt className="text-[11px] uppercase tracking-wider text-content-muted">Container</dt>
                  <dd className="mt-1 text-[13px] text-content-primary">
                    {health?.containerStatus ?? '-'}
                  </dd>
                </div>
                <div>
                  <dt className="text-[11px] uppercase tracking-wider text-content-muted">Sampled</dt>
                  <dd className="mt-1 text-[13px] text-content-primary">
                    {relativeTime(health?.lastSampledAt)}
                  </dd>
                </div>
              </dl>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Activity" icon={<Activity className="h-4 w-4" />} />
            <CardBody className="p-0">
              {!activity ? (
                <SkeletonRows rows={3} className="p-4" />
              ) : activity.items.length === 0 ? (
                <EmptyState title="Nothing yet" className="py-8" />
              ) : (
                <ActivityList entries={activity.items.slice(0, 6)} compact />
              )}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
}
