import { ArrowRight, GitBranch, Globe, ShieldCheck, Terminal, Zap } from 'lucide-react';
import { Link } from 'react-router-dom';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { Button } from '@/components/ui/button';
import { usePrefersReducedMotion } from '@/lib/use-media-query';
import { HeroVisual } from './HeroVisual';

const CAPABILITIES = [
  { label: 'Git Integration', icon: GitBranch },
  { label: 'Automated Builds', icon: Terminal },
  { label: 'Instant Deployments', icon: Zap },
  { label: 'Global CDN', icon: Globe },
  { label: 'Secure by Default', icon: ShieldCheck },
];

export function Hero() {
  const reducedMotion = usePrefersReducedMotion();

  return (
    <section
      className="relative overflow-hidden"
      style={{
        background:
          'radial-gradient(ellipse at 68% 48%, rgba(120,190,0,0.055) 0%, rgba(0,0,0,0) 42%)',
      }}
      aria-labelledby="hero-heading"
    >
      {/*
        Layout strategy:
          < sm  (< 640px) : single column — copy on top, full server scene below
          sm–lg (640–1023): two columns [44% | 56%]
          lg+   (1024px+) : two columns [36% | 64%]
        The visual is the full art-directed scene at every size; it is a container-query
        composition, so it scales as one unit down to the narrowest phone.
      */}
      <div className="landing-container relative grid gap-6 pb-8 pt-7 sm:grid-cols-[44%_56%] sm:items-start sm:gap-0 sm:pb-4 sm:pt-6 lg:grid-cols-[36%_64%] lg:pb-0 lg:pt-0">

        {/* ── Copy column ── */}
        <div className="min-w-0 sm:pr-[2vw] sm:pt-[2.8vw] lg:pr-[2vw] lg:pt-[2.8vw]">
          <p className="inline-flex items-center gap-1.5 rounded-full border border-accent-border bg-surface px-[1.1em] py-[0.55em] text-[length:var(--dl-hero-pill)]">
            <Zap className="h-[1em] w-[1em] shrink-0 text-accent" aria-hidden="true" />
            <span className="whitespace-nowrap font-medium tracking-[0.01em] text-content-secondary">
              Deployments are now 2x faster
            </span>
          </p>

          <h1
            id="hero-heading"
            className="mt-[0.55em] max-w-[9em] text-[length:var(--dl-hero-h1)] font-bold leading-[1.04] tracking-[-0.038em] text-content-primary"
          >
            Deploy your code.
            <br />
            Anywhere.
            <br />
            <span className="text-accent">In seconds.</span>
          </h1>

          <p className="mt-[1.45em] max-w-[25em] text-[length:var(--dl-hero-lead)] leading-[1.6] text-content-secondary">
            DeployLane helps developers build, deploy, and manage applications with zero friction.
            From git push to global scale.
          </p>

          <div className="mt-[2.2em] flex flex-wrap items-center gap-[1.2em] text-[length:var(--dl-hero-cta)]">
            <Link to="/login">
              <Button
                variant="primary"
                size="md"
                className="group h-[3.6em] min-w-[13em] justify-center gap-[0.8em] rounded-[0.5em] px-[1.6em] text-[1em] font-medium"
              >
                Start Deploying
                <ArrowRight
                  className="h-[1.2em] w-[1.2em] transition-transform duration-200 group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </Button>
            </Link>

            <Link to="/login">
              <Button
                variant="secondary"
                size="md"
                className="h-[3.6em] justify-center gap-[0.8em] rounded-[0.5em] border-border-strong bg-surface px-[1.5em] text-[1em] font-medium"
              >
                <GithubIcon className="h-[1.2em] w-[1.2em]" />
                Connect GitHub
              </Button>
            </Link>
          </div>

          {/* Capabilities row — 2-col grid on mobile, flex row from sm up */}
          <ul className="hero-capabilities mt-[2.6em] grid w-full grid-cols-2 items-center gap-x-[0.9em] gap-y-[0.9em] text-[length:var(--dl-hero-feat)] sm:flex sm:flex-wrap sm:gap-x-[1em] sm:gap-y-[0.6em] lg:flex-nowrap lg:justify-between lg:gap-x-[1.2em]">
            {CAPABILITIES.map((capability) => (
              <li
                key={capability.label}
                className="hero-capability flex min-w-0 items-center gap-[0.5em]"
              >
                <span className="flex h-[2.6em] w-[2.6em] shrink-0 items-center justify-center rounded-full border border-accent-border">
                  <capability.icon
                    className="h-[1.15em] w-[1.15em] text-accent"
                    aria-hidden="true"
                  />
                </span>
                <span className="min-w-0 truncate font-medium text-content-secondary">
                  {capability.label}
                </span>
              </li>
            ))}
          </ul>
        </div>

        {/* ── Visual column — full server scene at every breakpoint ── */}
        <div className="min-w-0">
          <HeroVisual reducedMotion={reducedMotion} />
        </div>
      </div>
    </section>
  );
}
