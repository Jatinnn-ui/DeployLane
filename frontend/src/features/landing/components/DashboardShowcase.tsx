import { Activity, Boxes, FileText, Gauge, LayoutDashboard, Settings, Sparkles } from 'lucide-react';

const NAV = [
  { label: 'Overview', icon: LayoutDashboard, active: true },
  { label: 'Deployments', icon: Boxes },
  { label: 'Logs', icon: FileText },
  { label: 'Monitoring', icon: Gauge },
  { label: 'AI analysis', icon: Sparkles },
  { label: 'Settings', icon: Settings },
];

const STATS = [
  { label: 'Uptime · 30d', value: '99.98%' },
  { label: 'p95 latency', value: '128 ms' },
  { label: 'Build time', value: '41 s' },
];

const DEPLOYMENTS = [
  { sha: '2f8c1ae', message: 'feat: cache warm on boot', state: 'Live', time: '2m ago' },
  { sha: '9b41d07', message: 'fix: retry webhook delivery', state: 'Succeeded', time: '3h ago' },
  { sha: 'c07e5f2', message: 'chore: bump dependencies', state: 'Succeeded', time: '1d ago' },
];

const LOG_LINES: Array<{ source: string; text: string; tone: string }> = [
  { source: 'git', text: 'Cloning main at 2f8c1ae', tone: 'text-[var(--dl-terminal-git)]' },
  { source: 'build', text: 'Detected Node.js 22 · npm ci', tone: 'text-[var(--dl-terminal-build)]' },
  {
    source: 'docker',
    text: 'Exported image sha256:4f1c… (84 MB)',
    tone: 'text-[var(--dl-terminal-docker)]',
  },
  {
    source: 'health',
    text: 'GET /healthz → 200 in 34ms',
    tone: 'text-[var(--dl-terminal-health)]',
  },
  { source: 'system', text: 'Routing 100% of traffic to new container', tone: 'text-success' },
];

/**
 * A composed mock of the real DeployLane dashboard.
 *
 * Built from the same design tokens as the product rather than as a screenshot: it stays
 * sharp at any DPR, follows the theme if the palette shifts, and — unlike an image — the
 * text inside it is real text for search engines and screen readers.
 */
export function DashboardShowcase() {
  return (
    <section
      className="border-y border-border-subtle bg-canvas-secondary py-20 lg:py-28"
      aria-labelledby="showcase-heading"
    >
      <div className="landing-container">
        <div className="max-w-[620px]">
          <p className="text-[12px] font-semibold uppercase tracking-[0.1em] text-accent">
            The console
          </p>
          <h2
            id="showcase-heading"
            className="mt-4 text-[28px] font-bold leading-[1.12] tracking-[-0.025em] text-content-primary sm:text-[36px] lg:text-[42px]"
          >
            Every deploy, log line and metric in one place.
          </h2>
          <p className="mt-4 text-[16px] leading-[1.6] text-content-secondary">
            Build output, container health and rollout state live on the same screen, so you are
            not stitching together three tools to answer one question.
          </p>
        </div>

        <div className="mt-14 overflow-hidden rounded-2xl border border-border-subtle bg-surface shadow-[0_28px_70px_-40px_rgba(0,0,0,0.9)]">
          {/* window chrome */}
          <div className="flex items-center gap-3 border-b border-border-subtle bg-surface-alt px-4 py-3">
            <div className="flex gap-1.5" aria-hidden="true">
              {[0, 1, 2].map((dot) => (
                <span key={dot} className="h-2.5 w-2.5 rounded-full bg-[#3a4042]" />
              ))}
            </div>
            <p className="mx-auto rounded-md border border-border-subtle bg-canvas px-3 py-1 font-mono text-[11px] text-content-muted">
              deploylane.online/projects/api-gateway
            </p>
          </div>

          <div className="flex">
            {/* nav rail */}
            <nav
              aria-hidden="true"
              className="hidden w-[190px] shrink-0 border-r border-border-subtle p-3 lg:block"
            >
              <p className="px-2.5 pb-2 text-[10.5px] font-semibold uppercase tracking-[0.09em] text-content-muted">
                api-gateway
              </p>
              <ul className="space-y-0.5">
                {NAV.map((item) => (
                  <li key={item.label}>
                    <span
                      className={
                        item.active
                          ? 'flex items-center gap-2.5 rounded-lg bg-accent-soft px-2.5 py-2 text-[13px] font-medium text-accent'
                          : 'flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] text-content-secondary'
                      }
                    >
                      <item.icon className="h-3.5 w-3.5" strokeWidth={2} />
                      {item.label}
                    </span>
                  </li>
                ))}
              </ul>
            </nav>

            <div className="min-w-0 flex-1 p-4 sm:p-5">
              {/* project header */}
              <div className="flex flex-wrap items-center gap-3">
                <h3 className="text-[16px] font-semibold tracking-[-0.01em] text-content-primary">
                  api-gateway
                </h3>
                <span className="inline-flex items-center gap-1.5 rounded-full border border-success-border bg-success-soft px-2.5 py-1 text-[11px] font-semibold text-success-foreground">
                  <span className="h-1.5 w-1.5 rounded-full bg-success" aria-hidden="true" />
                  Live
                </span>
                <span className="font-mono text-[11.5px] text-content-muted">
                  api-gateway.deploylane.online
                </span>
              </div>

              {/* stat tiles */}
              <dl className="mt-4 grid gap-3 sm:grid-cols-3">
                {STATS.map((stat) => (
                  <div
                    key={stat.label}
                    className="rounded-xl border border-border-subtle bg-canvas-secondary p-3"
                  >
                    <dt className="text-[11px] uppercase tracking-[0.07em] text-content-muted">
                      {stat.label}
                    </dt>
                    <dd className="mt-1.5 text-[20px] font-semibold tracking-[-0.02em] text-content-primary">
                      {stat.value}
                    </dd>
                  </div>
                ))}
              </dl>

              <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
                {/* deployment list */}
                <div className="rounded-xl border border-border-subtle bg-canvas-secondary">
                  <p className="border-b border-border-subtle px-3.5 py-2.5 text-[12px] font-semibold uppercase tracking-[0.07em] text-content-muted">
                    Deployments
                  </p>
                  <ul className="divide-y divide-border-subtle">
                    {DEPLOYMENTS.map((deployment) => (
                      <li key={deployment.sha} className="flex items-center gap-3 px-3.5 py-2.5">
                        <Activity
                          className={
                            deployment.state === 'Live'
                              ? 'h-3.5 w-3.5 shrink-0 text-success'
                              : 'h-3.5 w-3.5 shrink-0 text-content-muted'
                          }
                          aria-hidden="true"
                        />
                        <div className="min-w-0">
                          <p className="truncate text-[13px] text-content-primary">
                            {deployment.message}
                          </p>
                          <p className="mt-0.5 font-mono text-[10.5px] text-content-muted">
                            {deployment.sha} · {deployment.time}
                          </p>
                        </div>
                        <span className="ml-auto shrink-0 font-mono text-[10.5px] text-content-secondary">
                          {deployment.state}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>

                {/* log stream */}
                <div className="overflow-hidden rounded-xl border border-border-subtle bg-canvas">
                  <p className="flex items-center gap-2 border-b border-border-subtle px-3.5 py-2.5 text-[12px] font-semibold uppercase tracking-[0.07em] text-content-muted">
                    Build log
                    <span className="ml-auto flex items-center gap-1.5 text-[10.5px] font-medium normal-case tracking-normal text-accent">
                      <span className="h-1.5 w-1.5 rounded-full bg-accent" aria-hidden="true" />
                      streaming
                    </span>
                  </p>
                  <div className="space-y-1.5 p-3.5 font-mono text-[11px] leading-[1.5]">
                    {LOG_LINES.map((line) => (
                      <p key={line.text} className="flex gap-2.5">
                        <span className={`w-[52px] shrink-0 ${line.tone}`}>[{line.source}]</span>
                        <span className="min-w-0 text-content-secondary">{line.text}</span>
                      </p>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
