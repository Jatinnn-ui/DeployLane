import { GithubIcon } from '@/components/icons/GithubIcon';
import { AlertCircle, ArrowRight, Lock, Sparkles, Terminal } from 'lucide-react';
import { useEffect } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { useAuthConfig } from '@/api/platform';
import { Button } from '@/components/ui/button';
import { ErrorState, Spinner } from '@/components/ui/feedback';
import { BrandLogo } from '@/components/BrandLogo';
import { ThemeToggle } from '@/components/ui/theme-toggle';
import { useAuthStore } from '@/stores/auth-store';
import { apiClient } from '@/lib/api-client';

const HIGHLIGHTS = [
  {
    icon: Terminal,
    title: 'Real deployments, not a mock',
    description: 'Clone, build an image, run a container, health check, publish a URL.',
  },
  {
    icon: Sparkles,
    title: 'AI failure analysis',
    description: 'A failed build is explained with root cause, evidence and a fix.',
  },
  {
    icon: Lock,
    title: 'Secrets encrypted at rest',
    description: 'AES-256-GCM before storage, redacted out of every log line.',
  },
];

/**
 * Sign-in screen.
 *
 * The OAuth handshake is entirely server driven: this page just navigates to the backend's authorize
 * endpoint, which issues the state, sets the cookie and redirects to GitHub. No client secret, no token in
 * a URL fragment.
 */
export function LoginPage() {
  const status = useAuthStore((state) => state.status);
  const { data: config, isLoading, error } = useAuthConfig();
  const [searchParams, setSearchParams] = useSearchParams();
  const loginError = searchParams.get('error');
  const returnTo = searchParams.get('returnTo') ?? '/dashboard';

  useEffect(() => {
    if (!loginError) return;
    // Keep the message visible but drop it from the URL so a refresh does not re-show it.
    const timer = window.setTimeout(() => {
      searchParams.delete('error');
      setSearchParams(searchParams, { replace: true });
    }, 12_000);
    return () => window.clearTimeout(timer);
  }, [loginError, searchParams, setSearchParams]);

  if (status === 'authenticated') {
    return <Navigate to={returnTo} replace />;
  }

  const startLogin = () => {
    const target = `${apiClient.baseUrl}/api/v1/auth/github/authorize?returnTo=${encodeURIComponent(returnTo)}`;
    window.location.assign(target);
  };

  return (
    <div className="relative grid min-h-screen lg:grid-cols-2">
      {/* Reachable before sign-in, so the theme can be set without an account. */}
      <div className="absolute right-5 top-5 z-10">
        <ThemeToggle />
      </div>

      <div className="flex items-center justify-center px-6 py-16">
        <div className="w-full max-w-sm space-y-8">
          <div className="space-y-2">
            <div className="flex items-center">
              <BrandLogo className="h-[32px] max-w-[160px]" />
            </div>
            <h1 className="text-2xl font-semibold tracking-tight text-content-primary">
              Deploy. Monitor. Debug. Fix.
            </h1>
            <p className="text-[13px] leading-relaxed text-content-secondary">
              Connect a GitHub repository and DeployLane builds it, runs it in Docker, streams the logs
              and explains what broke when something does.
            </p>
          </div>

          {loginError ? (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-lg border border-danger-border bg-danger-soft px-3 py-2.5 text-[12px] leading-relaxed text-danger-foreground"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{loginError}</span>
            </div>
          ) : null}

          {isLoading ? (
            <div className="flex items-center gap-2 text-[13px] text-content-secondary">
              <Spinner />
              Checking sign-in options...
            </div>
          ) : error ? (
            <ErrorState
              error={error}
              onRetry={() => window.location.reload()}
              className="text-[12px]"
            />
          ) : config?.githubOauthEnabled ? (
            <div className="space-y-3">
              <Button variant="primary" size="lg" className="w-full" onClick={startLogin}>
                <GithubIcon className="h-4 w-4" aria-hidden="true" />
                Continue with GitHub
                <ArrowRight className="ml-auto h-4 w-4" aria-hidden="true" />
              </Button>
              <p className="text-[12px] leading-relaxed text-content-muted">
                DeployLane requests the <code className="font-mono">repo</code> scope so it can clone
                private repositories and configure webhooks. The token is encrypted before storage and
                never sent to your browser.
              </p>
            </div>
          ) : (
            <div className="space-y-3 rounded-lg border border-warning-border bg-warning-soft px-4 py-3">
              <p className="text-[13px] font-medium text-warning-foreground">GitHub sign-in is not configured</p>
              <p className="text-[12px] leading-relaxed text-content-secondary">
                Set <code className="font-mono">GITHUB_CLIENT_ID</code> and{' '}
                <code className="font-mono">GITHUB_CLIENT_SECRET</code> in the backend environment, then
                restart it. The OAuth callback URL must be{' '}
                <code className="font-mono break-all">
                  {apiClient.baseUrl}/api/v1/auth/github/callback
                </code>
                .
              </p>
            </div>
          )}
        </div>
      </div>

      <div className="relative hidden items-center justify-center overflow-hidden border-l border-border-subtle bg-surface lg:flex">
        <div
          className="absolute inset-0"
          style={{
            backgroundImage:
              'radial-gradient(circle at 30% 30%, var(--dl-accent-soft), transparent 55%), radial-gradient(circle at 75% 65%, var(--dl-ring), transparent 55%)',
          }}
          aria-hidden="true"
        />
        <div className="relative w-full max-w-md space-y-8 px-10">
          <ul className="space-y-5">
            {HIGHLIGHTS.map((highlight) => (
              <li key={highlight.title} className="flex gap-3">
                <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-border-subtle bg-surface-raised">
                  <highlight.icon className="h-4 w-4 text-accent" aria-hidden="true" />
                </div>
                <div>
                  <p className="text-[13px] font-medium text-content-primary">{highlight.title}</p>
                  <p className="mt-0.5 text-[12px] leading-relaxed text-content-secondary">
                    {highlight.description}
                  </p>
                </div>
              </li>
            ))}
          </ul>

          <div className="rounded-card border border-border-subtle bg-surface-alt p-4 font-mono text-[11px] leading-relaxed text-content-secondary">
            <p className="text-content-muted">12:43:21 SYSTEM Preparing deployment...</p>
            <p>12:43:26 GIT Repository cloned successfully.</p>
            <p>12:43:41 BUILD Running npm run build...</p>
            <p>12:44:14 DOCKER Container started.</p>
            <p className="text-success-foreground">12:44:16 HEALTH Health check passed.</p>
            <p className="text-success-foreground">12:44:16 SYSTEM Deployment ready.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
