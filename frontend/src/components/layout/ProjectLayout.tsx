import { GithubIcon } from '@/components/icons/GithubIcon';
import {
  Activity,
  ExternalLink,
  GitBranch,
  KeyRound,
  LayoutDashboard,
  Rocket,
  ScrollText,
  Settings,
  Sparkles,
} from 'lucide-react';
import { NavLink, Outlet, useParams } from 'react-router-dom';
import { useProject } from '@/api/projects';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ErrorState, Skeleton } from '@/components/ui/feedback';
import { DeploymentStatusIndicator } from '@/components/DeploymentStatusBadge';
import { DeployButton } from '@/features/deployments/DeployButton';
import { cn } from '@/lib/utils';

const PROJECT_NAV = [
  { segment: '', label: 'Overview', icon: LayoutDashboard },
  { segment: 'deployments', label: 'Deployments', icon: Rocket },
  { segment: 'logs', label: 'Logs', icon: ScrollText },
  { segment: 'monitoring', label: 'Monitoring', icon: Activity },
  { segment: 'environment', label: 'Environment', icon: KeyRound },
  { segment: 'ai', label: 'AI DevOps', icon: Sparkles },
  { segment: 'settings', label: 'Settings', icon: Settings },
];

export function ProjectLayout() {
  const { projectId } = useParams<{ projectId: string }>();
  const { data: project, isLoading, error, refetch } = useProject(projectId);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96" />
        <Skeleton className="h-10 w-full" />
      </div>
    );
  }

  if (error || !project) {
    return <ErrorState error={error} onRetry={() => void refetch()} />;
  }

  const latest = project.latestDeployment;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="truncate text-2xl text-content-primary">{project.name}</h1>
            {project.frameworkLabel ? <Badge>{project.frameworkLabel}</Badge> : null}
            {project.status !== 'ACTIVE' ? (
              <Badge tone={project.status === 'ARCHIVED' ? 'danger' : 'warning'}>
                {project.status}
              </Badge>
            ) : null}
            {latest ? <DeploymentStatusIndicator status={latest.status} /> : null}
          </div>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-content-secondary">
            <a
              href={project.repository.url}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 transition-colors hover:text-content-primary"
            >
              <GithubIcon className="h-3.5 w-3.5" aria-hidden="true" />
              {project.repository.fullName}
              {project.repository.isPrivate ? <Badge className="ml-1">private</Badge> : null}
            </a>
            <span className="inline-flex items-center gap-1.5">
              <GitBranch className="h-3.5 w-3.5" aria-hidden="true" />
              {project.environments[0]?.branch ?? project.defaultBranch}
            </span>
            {latest?.deploymentUrl ? (
              <a
                href={latest.deploymentUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1.5 font-mono text-[11px] text-accent-deep transition-colors hover:text-content-primary"
              >
                {latest.deploymentUrl.replace(/^https?:\/\//, '')}
                <ExternalLink className="h-3 w-3" aria-hidden="true" />
              </a>
            ) : null}
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          {latest?.deploymentUrl ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => window.open(latest.deploymentUrl ?? '', '_blank', 'noopener')}
            >
              <ExternalLink className="h-3.5 w-3.5" />
              Open app
            </Button>
          ) : null}
          <DeployButton project={project} />
        </div>
      </div>

      <nav className="scrollbar-hide -mx-1 flex items-center gap-0.5 overflow-x-auto border-b border-border-subtle pb-px">
        {PROJECT_NAV.map((item) => (
          <NavLink
            key={item.segment || 'overview'}
            to={item.segment ? `/projects/${project.id}/${item.segment}` : `/projects/${project.id}`}
            end={item.segment === ''}
            className={({ isActive }) =>
              cn(
                'flex shrink-0 items-center gap-2 border-b-2 px-3.5 py-2.5 text-[13px] font-medium transition-colors',
                isActive
                  ? 'border-accent-deep text-content-primary'
                  : 'border-transparent text-content-secondary hover:text-content-primary',
              )
            }
          >
            <item.icon className="h-3.5 w-3.5" aria-hidden="true" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <Outlet context={{ project }} />
    </div>
  );
}
