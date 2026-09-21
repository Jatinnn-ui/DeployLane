import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  GitBranch,
  Lock,
  Rocket,
  Sparkles,
  Terminal,
} from 'lucide-react';
import { useEffect } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { useAuthConfig } from '@/api/platform';
import { Button } from '@/components/ui/button';
import { ErrorState, Spinner } from '@/components/ui/feedback';
import { BrandLogo } from '@/components/BrandLogo';
import { ThemeToggle } from '@/components/ui/theme-toggle';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { useAuthStore } from '@/stores/auth-store';
import { apiClient } from '@/lib/api-client';

/* ─── Feature highlights ──────────────────────────────────────────────────── */
const FEATURES = [
  {
    icon: Terminal,
    label: 'Real deployments, not a mock',
    detail: 'Clone → build image → run container → health check → publish URL.',
  },
  {
    icon: Sparkles,
    label: 'AI failure analysis',
    detail: 'Root cause, evidence and a one-click fix for every failed build.',
  },
  {
    icon: Lock,
    label: 'Secrets encrypted at rest',
    detail: 'AES-256-GCM before storage, redacted out of every log line.',
  },
  {
    icon: Rocket,
    label: 'Zero-downtime deploys',
    detail: 'Old container serves until the new one passes its health check.',
  },
];

/* ─── Terminal log lines ──────────────────────────────────────────────────── */
const LOG_LINES = [
  { time: '12:43:21', tag: 'SYSTEM', cls: 'login-tag-system', msg: 'Preparing deployment...',         delay: '0s'    },
  { time: '12:43:26', tag: 'GIT',    cls: 'login-tag-git',    msg: 'Repository cloned successfully.', delay: '0.55s' },
  { time: '12:43:41', tag: 'BUILD',  cls: 'login-tag-build',  msg: 'Running npm run build...',        delay: '1.1s'  },
  { time: '12:44:14', tag: 'DOCKER', cls: 'login-tag-docker', msg: 'Container started.',              delay: '1.65s' },
  { time: '12:44:16', tag: 'HEALTH', cls: 'login-tag-health', msg: 'Health check passed.',            delay: '2.2s'  },
  { time: '12:44:16', tag: 'SYSTEM', cls: 'login-tag-ready',  msg: 'Deployment ready. ✓',             delay: '2.75s' },
] as const;

/* Pipeline steps shown in the form */
const PIPELINE = ['Clone', 'Build', 'Test', 'Deploy'];

export function LoginPage() {
  const status = useAuthStore((s) => s.status);
  const { data: config, isLoading, error } = useAuthConfig();
  const [searchParams, setSearchParams] = useSearchParams();
  const loginError = searchParams.get('error');
  const returnTo   = searchParams.get('returnTo') ?? '/dashboard';

  useEffect(() => {
    if (!loginError) return;
    const timer = window.setTimeout(() => {
      searchParams.delete('error');
      setSearchParams(searchParams, { replace: true });
    }, 12_000);
    return () => window.clearTimeout(timer);
  }, [loginError, searchParams, setSearchParams]);

  if (status === 'authenticated') return <Navigate to={returnTo} replace />;

  const startLogin = () => {
    window.location.assign(
      `${apiClient.baseUrl}/api/v1/auth/github/authorize?returnTo=${encodeURIComponent(returnTo)}`,
    );
  };

  return (
    /* The root element uses semantic tokens so both themes apply automatically */
    <div className="login-root relative grid min-h-screen bg-canvas lg:grid-cols-[1fr_1.05fr]">

      {/* ── Theme toggle ─────────────────────────────────────── */}
      <div className="absolute right-5 top-5 z-20">
        <ThemeToggle />
      </div>

      {/* ════════════════════════════════════════════════════════
          LEFT COLUMN — sign-in form
          ════════════════════════════════════════════════════════ */}
      <div className="flex flex-col justify-between px-7 py-10 sm:px-12 lg:px-16 lg:py-14">

        {/* Logo */}
        <BrandLogo className="h-8 max-w-[152px]" />

        {/* Form body */}
        <div className="mx-auto w-full max-w-[380px] space-y-8 py-12">

          {/* Eyebrow + headline */}
          <div className="space-y-3">
            <p className="text-[9.5px] font-semibold uppercase tracking-[0.16em] text-accent">
              Your deployment copilot
            </p>
            <h1 className="text-[32px] font-bold leading-[1.1] tracking-[-0.03em] text-content-primary sm:text-[36px]">
              Deploy.&nbsp;Monitor.
              <br />
              Debug.&nbsp;<span className="text-accent">Fix.</span>
            </h1>
            <p className="text-[13.5px] leading-[1.65] text-content-secondary">
              Connect a GitHub repository and DeployLane builds it, runs it in Docker, streams
              the logs and explains what broke when something does.
            </p>
          </div>

          {/* Pipeline breadcrumb */}
          <div className="flex items-center">
            {PIPELINE.map((step, i) => (
              <div key={step} className="flex items-center">
                <span
                  className={`rounded-full px-2.5 py-0.5 text-[10px] font-semibold transition-colors ${
                    i === PIPELINE.length - 1
                      ? 'bg-accent/15 text-accent'
                      : 'text-content-muted'
                  }`}
                >
                  {step}
                </span>
                {i < PIPELINE.length - 1 && (
                  <span className="mx-0.5 text-[10px] text-border-subtle">→</span>
                )}
              </div>
            ))}
            {/* trailing arrow + final step */}
            <span className="mx-0.5 text-[10px] text-border-subtle">→</span>
            <span className="rounded-full px-2.5 py-0.5 text-[10px] font-semibold text-accent bg-accent/15">
              Live
            </span>
          </div>

          {/* Error alert */}
          {loginError && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-xl border border-danger-border bg-danger-soft px-4 py-3 text-[12.5px] leading-relaxed text-danger-foreground"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{loginError}</span>
            </div>
          )}

          {/* Auth control */}
          {isLoading ? (
            <div className="flex items-center gap-2.5 text-[13px] text-content-secondary">
              <Spinner />
              Checking sign-in options…
            </div>
          ) : error ? (
            <ErrorState error={error} onRetry={() => window.location.reload()} className="text-[12px]" />
          ) : config?.githubOauthEnabled ? (
            <div className="space-y-4">
              <Button
                variant="primary"
                size="lg"
                className="group w-full rounded-xl text-[14px] font-semibold login-cta-shadow"
                onClick={startLogin}
              >
                <GithubIcon className="h-4 w-4 shrink-0" aria-hidden="true" />
                Continue with GitHub
                <ArrowRight
                  className="ml-auto h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </Button>

              {/* Scope note */}
              <div className="flex items-start gap-2 rounded-xl border border-border-subtle bg-surface px-3.5 py-3">
                <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-content-muted" aria-hidden="true" />
                <p className="text-[11.5px] leading-[1.55] text-content-muted">
                  Requests the{' '}
                  <code className="rounded bg-surface-raised px-1 font-mono text-content-secondary">
                    repo
                  </code>{' '}
                  scope to clone repositories and configure webhooks. Token is encrypted before
                  storage and never sent to your browser.
                </p>
              </div>
            </div>
          ) : (
            <div className="space-y-2 rounded-xl border border-warning-border bg-warning-soft px-4 py-3.5">
              <p className="text-[13px] font-semibold text-warning-foreground">
                GitHub sign-in is not configured
              </p>
              <p className="text-[12px] leading-relaxed text-content-secondary">
                Set{' '}
                <code className="font-mono">GITHUB_CLIENT_ID</code> and{' '}
                <code className="font-mono">GITHUB_CLIENT_SECRET</code> in the backend environment,
                then restart it. Callback:{' '}
                <code className="break-all font-mono">
                  {apiClient.baseUrl}/api/v1/auth/github/callback
                </code>
              </p>
            </div>
          )}
        </div>

        {/* Footer */}
        <p className="text-[11px] text-content-muted">
          © {new Date().getFullYear()} DeployLane · Deploy with confidence
        </p>
      </div>

      {/* ════════════════════════════════════════════════════════
          RIGHT COLUMN — visual / feature panel
          Uses surface tokens so it adapts to both themes.
          ════════════════════════════════════════════════════════ */}
      <div className="login-panel relative hidden flex-col justify-center gap-8 overflow-hidden border-l border-border-subtle px-10 py-14 lg:flex xl:px-14">

        {/* Subtle grid overlay */}
        <div
          aria-hidden="true"
          className="login-grid pointer-events-none absolute inset-0"
        />

        {/* Accent ambient glow */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -right-32 -top-32 h-[500px] w-[500px] rounded-full login-glow"
        />

        {/* ── Feature list ─────────────────────────────────── */}
        <ul className="relative z-10 space-y-5">
          {FEATURES.map((f) => (
            <li key={f.label} className="flex items-start gap-4">
              <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-accent-border bg-accent-soft">
                <f.icon className="h-4 w-4 text-accent" strokeWidth={1.8} aria-hidden="true" />
              </span>
              <div>
                <p className="text-[14px] font-semibold leading-snug text-content-primary">
                  {f.label}
                </p>
                <p className="mt-0.5 text-[12px] leading-[1.6] text-content-secondary">
                  {f.detail}
                </p>
              </div>
            </li>
          ))}
        </ul>

        {/* ── Terminal card ─────────────────────────────────── */}
        <div className="login-terminal relative z-10 overflow-hidden rounded-2xl border border-border-subtle shadow-lg">

          {/* Title bar */}
          <div className="flex items-center justify-between border-b border-border-subtle bg-surface-raised px-4 py-2.5">
            <div className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full bg-[#ff5f57]" />
              <span className="h-2.5 w-2.5 rounded-full bg-[#febc2e]" />
              <span className="h-2.5 w-2.5 rounded-full bg-[#28c840]" />
            </div>
            <div className="flex items-center gap-1.5">
              <GitBranch className="h-3 w-3 text-content-muted" aria-hidden="true" />
              <span className="font-mono text-[10px] text-content-muted">main · deploylane-app</span>
            </div>
            <span className="flex items-center gap-1.5 rounded-full border border-success-border bg-success-soft px-2 py-0.5">
              <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-success" />
              <span className="text-[9px] font-semibold text-success-foreground">LIVE</span>
            </span>
          </div>

          {/* Log lines */}
          <div className="space-y-0.5 bg-surface-alt px-4 py-4 font-mono text-[11.5px]">
            {LOG_LINES.map((line) => (
              <p
                key={line.time + line.tag}
                className="login-log-line flex items-baseline gap-3 opacity-0"
                style={{ animationDelay: line.delay }}
              >
                <span className="shrink-0 text-content-muted">{line.time}</span>
                <span className={`w-[46px] shrink-0 text-right text-[10px] font-bold tracking-wide ${line.cls}`}>
                  {line.tag}
                </span>
                <span className="text-content-secondary">{line.msg}</span>
              </p>
            ))}
          </div>

          {/* Status bar */}
          <div className="flex items-center justify-between border-t border-border-subtle bg-success-soft px-4 py-2">
            <div className="flex items-center gap-1.5">
              <CheckCircle2 className="h-3.5 w-3.5 text-success" aria-hidden="true" />
              <span className="text-[11px] font-medium text-success-foreground">
                Deployment successful
              </span>
            </div>
            <span className="font-mono text-[10px] text-content-muted">55s total</span>
          </div>
        </div>
      </div>
    </div>
  );
}
