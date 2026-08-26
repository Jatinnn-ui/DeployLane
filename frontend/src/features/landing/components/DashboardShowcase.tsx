import {
  Activity,
  ArrowRight,
  Check,
  ChevronRight,
  FolderGit2,
  GitBranch,
  GitCommit,
  LayoutDashboard,
  Plus,
  Rocket,
  Settings,
  Users,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { Wordmark } from './Wordmark';

const BENEFITS = [
  'Real-time deployment status',
  'Instant logs & metrics',
  'One-click rollbacks',
  'Team collaboration',
];

const NAV = [
  { label: 'Overview', icon: LayoutDashboard, active: true },
  { label: 'Projects', icon: FolderGit2 },
  { label: 'Deployments', icon: Rocket },
  { label: 'Activity', icon: Activity },
];

const WORKSPACE_NAV = [
  { label: 'Members', icon: Users },
  { label: 'Settings', icon: Settings },
];

const STATS = [
  { label: 'Projects', value: '3', tag: null, tone: 'default' as const },
  { label: 'Live', value: '2', tag: 'serving', tone: 'success' as const },
  { label: 'In Progress', value: '0', tag: null, tone: 'default' as const },
  { label: 'Failing', value: '1', tag: 'needs attention', tone: 'danger' as const },
];

const PROJECTS = [
  {
    name: 'cheslearn',
    repo: 'Jatinnn/cheslearn',
    branch: 'main',
    sha: '1a97fa9',
    domain: 'cheslearn.deploylane.app',
    state: 'Live' as const,
  },
  {
    name: 'Erpli_chess_code_new',
    repo: 'Jatinnn/Erpli_chess_code_new',
    branch: 'main',
    sha: '1e1e566',
    domain: 'erpli-chess-code-new-2.deploylane.app',
    state: 'Live' as const,
  },
  {
    name: 'web-store',
    repo: 'Jatinnn/web-store',
    branch: 'main',
    sha: '778acc1',
    domain: 'web-store.deploylane.app',
    state: 'Building' as const,
  },
];

const ACTIVITY = [
  { text: 'Deployment #129 went live', time: '3 hours ago', tone: 'success' as const },
  { text: 'Jatinnn started deployment #129', time: '3 hours ago', tone: 'default' as const },
  { text: 'Imported Jatinnn/Erpli_chess_code_new', time: '3 hours ago', tone: 'default' as const },
  { text: 'Deployment #128 went live', time: '5 hours ago', tone: 'success' as const },
  { text: 'Failure analysis completed', time: '6 hours ago', tone: 'danger' as const },
];

/**
 * A composed mock of the real DeployLane console.
 *
 * Built from the same design tokens as the product rather than as a screenshot: it stays
 * sharp at any DPR, follows the palette if it shifts, and — unlike an image — the text
 * inside it is real text for search engines and screen readers.
 *
 * The whole mock is `aria-hidden` all the same. Read linearly it is a wall of
 * context-free fragments, and the surrounding copy already states what it shows.
 */
function ConsoleMock() {
  return (
    <div
      aria-hidden="true"
      className="overflow-hidden rounded-xl border border-border-subtle bg-canvas shadow-[0_28px_70px_-40px_rgba(0,0,0,0.9)]"
    >
      <div className="flex">
        {/* nav rail */}
        <div className="hidden w-[132px] shrink-0 flex-col border-r border-border-subtle p-2.5 sm:flex">
          <Wordmark className="mb-4" textClass="text-[11px]" />

          <ul className="space-y-0.5">
            {NAV.map((item) => (
              <li key={item.label}>
                <span
                  className={cn(
                    'flex items-center gap-2 rounded-md px-2 py-1.5 text-[10.5px]',
                    item.active
                      ? 'bg-accent-soft font-medium text-accent'
                      : 'text-content-secondary',
                  )}
                >
                  <item.icon className="h-3 w-3 shrink-0" strokeWidth={2} />
                  {item.label}
                </span>
              </li>
            ))}
          </ul>

          <p className="mt-4 px-2 text-[8.5px] font-semibold uppercase tracking-[0.12em] text-content-muted">
            Workspace
          </p>
          <ul className="mt-1.5 space-y-0.5">
            {WORKSPACE_NAV.map((item) => (
              <li key={item.label}>
                <span className="flex items-center gap-2 rounded-md px-2 py-1.5 text-[10.5px] text-content-secondary">
                  <item.icon className="h-3 w-3 shrink-0" strokeWidth={2} />
                  {item.label}
                </span>
              </li>
            ))}
          </ul>

          <div className="mt-auto flex items-center gap-2 border-t border-border-subtle pt-2.5">
            <span className="flex h-5 w-5 items-center justify-center rounded-full bg-accent text-[9px] font-bold text-on-accent">
              J
            </span>
            <div className="min-w-0">
              <p className="truncate text-[10px] font-medium text-content-primary">Jatinnn</p>
              <p className="truncate text-[8.5px] text-content-muted">Owner</p>
            </div>
          </div>
        </div>

        <div className="min-w-0 flex-1 p-3">
          {/* header */}
          <div className="flex flex-wrap items-center gap-2">
            <div className="min-w-0">
              <p className="text-[13px] font-semibold tracking-[-0.01em] text-content-primary">
                Overview
              </p>
              <p className="text-[9.5px] text-content-muted">Jatinnn&apos;s workspace</p>
            </div>

            <span className="ml-auto inline-flex items-center gap-1 rounded-full border border-success-border bg-success-soft px-2 py-0.5 text-[9px] font-semibold text-success-foreground">
              <span className="h-1 w-1 rounded-full bg-success" />
              Healthy
            </span>

            <span className="inline-flex items-center gap-1 rounded-full bg-accent px-2 py-1 text-[9px] font-semibold text-on-accent">
              <Plus className="h-2.5 w-2.5" strokeWidth={3} />
              Import repository
            </span>
          </div>

          {/* stat tiles */}
          <div className="mt-3 grid grid-cols-2 gap-2 lg:grid-cols-4">
            {STATS.map((stat) => (
              <div
                key={stat.label}
                className="rounded-lg border border-border-subtle bg-canvas-secondary p-2"
              >
                <div className="flex items-center gap-1">
                  <p className="text-[9px] uppercase tracking-[0.08em] text-content-muted">
                    {stat.label}
                  </p>
                  <ChevronRight className="ml-auto h-2.5 w-2.5 text-content-muted" />
                </div>
                <div className="mt-1 flex items-baseline gap-1.5">
                  <p className="text-[16px] font-semibold leading-none tracking-[-0.02em] text-content-primary">
                    {stat.value}
                  </p>
                  {stat.tag && (
                    <span
                      className={cn(
                        'rounded px-1 py-0.5 text-[7.5px] font-semibold',
                        stat.tone === 'success'
                          ? 'bg-success-soft text-success-foreground'
                          : 'bg-danger-soft text-danger-foreground',
                      )}
                    >
                      {stat.tag}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>

          <div className="mt-3 grid gap-2.5 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)]">
            {/* project list */}
            <div className="rounded-lg border border-border-subtle bg-canvas-secondary">
              <p className="border-b border-border-subtle px-2.5 py-1.5 text-[9.5px] font-semibold uppercase tracking-[0.1em] text-content-muted">
                Projects
              </p>
              <ul className="divide-y divide-border-subtle">
                {PROJECTS.map((project) => (
                  <li key={project.name} className="flex items-center gap-2 px-2.5 py-2">
                    <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-border-subtle bg-surface">
                      <FolderGit2 className="h-2.5 w-2.5 text-content-secondary" />
                    </span>

                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[10.5px] font-medium leading-tight text-content-primary">
                        {project.name}
                      </p>
                      <p className="truncate font-mono text-[8px] leading-tight text-content-muted">
                        {project.repo}
                      </p>
                      <p className="truncate font-mono text-[8px] leading-tight text-accent">
                        {project.domain}
                      </p>
                    </div>

                    <div className="hidden shrink-0 sm:block">
                      <p className="flex items-center gap-1 font-mono text-[8px] text-content-secondary">
                        <GitBranch className="h-2 w-2" />
                        {project.branch}
                      </p>
                      <p className="flex items-center gap-1 font-mono text-[8px] text-content-muted">
                        <GitCommit className="h-2 w-2" />
                        {project.sha}
                      </p>
                    </div>

                    <span
                      className={cn(
                        'flex shrink-0 items-center gap-1 rounded-full px-1.5 py-0.5 text-[8px] font-semibold',
                        project.state === 'Live'
                          ? 'bg-success-soft text-success-foreground'
                          : 'bg-accent-soft text-accent',
                      )}
                    >
                      <span
                        className={cn(
                          'h-1 w-1 rounded-full',
                          project.state === 'Live' ? 'bg-success' : 'bg-accent',
                        )}
                      />
                      {project.state}
                    </span>
                  </li>
                ))}
              </ul>
            </div>

            {/* activity feed */}
            <div className="rounded-lg border border-border-subtle bg-canvas-secondary">
              <p className="border-b border-border-subtle px-2.5 py-1.5 text-[9.5px] font-semibold uppercase tracking-[0.1em] text-content-muted">
                Recent Activity
              </p>
              <ul className="p-2.5">
                {ACTIVITY.map((entry) => (
                  <li key={entry.text} className="flex gap-2 py-1">
                    <span
                      className={cn(
                        'mt-1 h-1.5 w-1.5 shrink-0 rounded-full',
                        entry.tone === 'success'
                          ? 'bg-success'
                          : entry.tone === 'danger'
                            ? 'bg-danger'
                            : 'bg-content-muted',
                      )}
                    />
                    <div className="min-w-0">
                      <p className="truncate text-[9.5px] leading-tight text-content-primary">
                        {entry.text}
                      </p>
                      <p className="text-[8px] text-content-muted">{entry.time}</p>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export function DashboardShowcase() {
  return (
    <section id="features" className="pb-4" aria-labelledby="showcase-heading">
      <div className="landing-container">
        <div className="landing-panel grid gap-8 px-5 py-7 sm:px-7 sm:py-8 lg:grid-cols-[minmax(0,0.54fr)_minmax(0,1.46fr)] lg:items-center lg:gap-8">
          <div>
            <p className="text-[9.5px] font-semibold uppercase tracking-[0.14em] text-accent">
              Full control
            </p>
            <h2
              id="showcase-heading"
              className="mt-3 text-[25px] font-bold leading-[1.14] tracking-[-0.03em] text-content-primary sm:text-[30px]"
            >
              Everything you need,
              <br />
              <span className="text-accent">all in one place</span>
            </h2>

            <p className="mt-4 max-w-[340px] text-[13.5px] leading-[1.62] text-content-secondary">
              Monitor deployments, view logs, manage environments, and roll back with a single
              click.
            </p>

            <ul className="mt-6 space-y-2.5">
              {BENEFITS.map((benefit) => (
                <li key={benefit} className="flex items-center gap-2.5">
                  <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-accent-soft">
                    <Check className="h-2.5 w-2.5 text-accent" strokeWidth={3} aria-hidden="true" />
                  </span>
                  <span className="text-[13px] text-content-secondary">{benefit}</span>
                </li>
              ))}
            </ul>

            <Link to="/login" className="mt-7 inline-block">
              <Button variant="secondary" size="sm" className="group">
                Explore Dashboard
                <ArrowRight
                  className="h-3.5 w-3.5 transition-transform duration-200 group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </Button>
            </Link>
          </div>

          <ConsoleMock />
        </div>
      </div>
    </section>
  );
}
