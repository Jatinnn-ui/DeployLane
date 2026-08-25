import { GithubIcon } from '@/components/icons/GithubIcon';
import { Activity, Boxes, Plus, Rocket } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useActivityFeed, useWorkspaces } from '@/api/platform';
import { useProjects } from '@/api/projects';
import { Badge } from '@/components/ui/badge';
import { buttonVariants } from '@/components/ui/button';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton, SkeletonRows } from '@/components/ui/feedback';
import { ProjectCard } from '@/features/dashboard/ProjectCard';
import { ActivityList } from '@/features/activity/ActivityList';
import { humanizeEnum } from '@/lib/utils';

/**
 * Landing screen: everything deployed, plus what has happened recently.
 *
 * The counters are derived from the project list already in cache rather than a second endpoint, which
 * keeps the dashboard to two requests.
 */
export function DashboardPage() {
  const { data: workspaces } = useWorkspaces();
  const { data: projects, isLoading, error, refetch } = useProjects();
  const { data: activity } = useActivityFeed({ kind: 'global' }, 0);

  const items = projects?.items ?? [];
  const live = items.filter((project) => project.latestDeployment?.status === 'READY').length;
  const failing = items.filter((project) => project.latestDeployment?.status === 'FAILED').length;
  const inFlight = items.filter((project) =>
    ['QUEUED', 'CLONING', 'DETECTING', 'BUILDING', 'IMAGE_BUILDING', 'STARTING', 'HEALTH_CHECKING'].includes(
      project.latestDeployment?.status ?? '',
    ),
  ).length;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-content-primary">Overview</h1>
          <p className="mt-1 text-[13px] text-content-secondary">
            {workspaces?.[0]
              ? `${workspaces[0].name} · ${humanizeEnum(workspaces[0].role)}`
              : 'Your deployments at a glance'}
          </p>
        </div>
        <Link to="/projects/import" className={buttonVariants({ variant: 'primary', size: 'sm' })}>
          <Plus className="h-3.5 w-3.5" aria-hidden="true" />
          Import repository
        </Link>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Projects" value={items.length} icon={Boxes} loading={isLoading} />
        <StatCard label="Live" value={live} icon={Rocket} tone="success" loading={isLoading} />
        <StatCard label="In progress" value={inFlight} icon={Activity} tone="accent" loading={isLoading} />
        <StatCard label="Failing" value={failing} icon={Activity} tone="danger" loading={isLoading} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-content-primary">Projects</h2>
            {items.length > 0 ? (
              <Link
                to="/projects"
                className="text-[12px] text-content-secondary transition-colors hover:text-content-primary"
              >
                View all
              </Link>
            ) : null}
          </div>

          {isLoading ? (
            <div className="grid gap-3 md:grid-cols-2">
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="h-40 w-full rounded-xl" />
              ))}
            </div>
          ) : error ? (
            <ErrorState error={error} onRetry={() => void refetch()} />
          ) : items.length === 0 ? (
            <Card>
              <EmptyState
                icon={GithubIcon}
                title="Deploy your first application"
                description="Connect a GitHub repository and DeployLane will detect the framework, build it, run it in Docker and monitor it."
                action={
                  <Link to="/projects/import" className={buttonVariants({ variant: 'primary' })}>
                    <Plus className="h-4 w-4" aria-hidden="true" />
                    Import repository
                  </Link>
                }
              />
            </Card>
          ) : (
            <div className="grid gap-3 md:grid-cols-2">
              {items.map((project) => (
                <ProjectCard key={project.id} project={project} />
              ))}
            </div>
          )}
        </section>

        <aside className="space-y-3">
          <Card>
            <CardHeader title="Recent activity" icon={<Activity className="h-4 w-4" />} />
            <CardBody className="p-0">
              {!activity ? (
                <SkeletonRows rows={4} className="p-4" />
              ) : activity.items.length === 0 ? (
                <EmptyState title="No activity yet" className="py-10" />
              ) : (
                <ActivityList entries={activity.items.slice(0, 8)} compact />
              )}
            </CardBody>
          </Card>
        </aside>
      </div>
    </div>
  );
}

function StatCard({
  label,
  value,
  icon: Icon,
  tone = 'neutral',
  loading,
}: {
  label: string;
  value: number;
  icon: typeof Activity;
  tone?: 'neutral' | 'success' | 'accent' | 'danger';
  loading?: boolean;
}) {
  return (
    <Card className="p-4">
      <div className="flex items-center justify-between">
        <p className="text-[12px] font-medium uppercase tracking-wider text-content-muted">{label}</p>
        <Icon className="h-3.5 w-3.5 text-content-muted" aria-hidden="true" />
      </div>
      {loading ? (
        <Skeleton className="mt-2 h-7 w-12" />
      ) : (
        <div className="mt-1.5 flex items-baseline gap-2">
          <span className="text-2xl font-semibold tabular-nums text-content-primary">{value}</span>
          {value > 0 && tone !== 'neutral' ? (
            <Badge tone={tone === 'accent' ? 'accent' : tone}>
              {tone === 'success' ? 'serving' : tone === 'danger' ? 'needs attention' : 'active'}
            </Badge>
          ) : null}
        </div>
      )}
    </Card>
  );
}
