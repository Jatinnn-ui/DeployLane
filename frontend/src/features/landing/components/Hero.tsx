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
    <section className="relative overflow-hidden" aria-labelledby="hero-heading">
      {/* Left column is ~39%, matching the reference. Explicit padding rather than a viewport
          min-height: the reference hero is ~640px tall, not a full screen. */}
      <div className="landing-container relative grid items-center gap-9 pb-8 pt-8 lg:grid-cols-[minmax(0,0.78fr)_minmax(0,1.22fr)] lg:gap-5 lg:pb-9 lg:pt-9">
        <div>
          <p className="inline-flex items-center gap-1.5 rounded-full border border-border-subtle bg-surface/70 px-2.5 py-1">
            <Zap className="h-2.5 w-2.5 text-accent" aria-hidden="true" />
            <span className="text-[11px] font-medium text-content-secondary">
              Deployments are now 2x faster
            </span>
          </p>

          <h1
            id="hero-heading"
            className="mt-9 max-w-[470px] text-[36px] font-bold leading-[1.02] tracking-[-0.04em] text-content-primary sm:text-[46px] lg:text-[55px]"
          >
            Deploy your code.
            <br />
            Anywhere.
            <br />
            <span className="text-accent">In seconds.</span>
          </h1>

          <p className="mt-5 max-w-[400px] text-[15px] leading-[1.6] text-content-secondary">
            DeployLane helps developers build, deploy, and manage applications with zero friction.
            From git push to global scale.
          </p>

          <div className="mt-7 flex flex-wrap items-center gap-3">
            <Link to="/login">
              <Button
                variant="primary"
                size="md"
                className="group h-12 w-[182px] justify-center rounded-[9px] text-[13.5px] shadow-[0_0_20px_-8px_rgba(168,240,0,0.45)]"
              >
                Start Deploying
                <ArrowRight
                  className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </Button>
            </Link>

            <Link to="/login">
              <Button
                variant="secondary"
                size="md"
                className="h-12 w-[170px] justify-center rounded-[9px] text-[13.5px]"
              >
                <GithubIcon className="h-4 w-4" />
                Connect GitHub
              </Button>
            </Link>
          </div>

          {/* Row runs wider than the copy column in the reference, so it is allowed to
              overflow the grid cell rather than wrapping. */}
          <ul className="mt-8 flex w-max max-w-full flex-wrap items-center gap-x-4 gap-y-2.5 lg:flex-nowrap">
            {CAPABILITIES.map((capability) => (
              <li key={capability.label} className="flex shrink-0 items-center gap-1.5">
                <span className="flex h-[15px] w-[15px] shrink-0 items-center justify-center rounded-full border border-accent-border">
                  <capability.icon className="h-2 w-2 text-accent" aria-hidden="true" />
                </span>
                <span className="whitespace-nowrap text-[11px] font-medium text-content-secondary">
                  {capability.label}
                </span>
              </li>
            ))}
          </ul>
        </div>

        <HeroVisual reducedMotion={reducedMotion} />
      </div>
    </section>
  );
}
