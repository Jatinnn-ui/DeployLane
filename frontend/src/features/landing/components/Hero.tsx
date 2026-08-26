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
      {/* Left column is ~39%, matching the reference. Explicit padding rather than a viewport
          min-height: the reference hero is ~640px tall, not a full screen. */}
      {/* 36/64 split and explicit vertical rhythm, both measured from the reference. The
          hero deliberately has no viewport-height rule, so the trusted row stays in view. */}
      {/* At lg the scene column starts flush with the navbar, so the 29px offset from the
          reference lives on the copy column. That keeps the trusted row at its measured y. */}
      <div className="landing-container relative grid gap-8 pb-6 pt-7 lg:grid-cols-[36%_64%] lg:items-start lg:gap-0 lg:pb-0 lg:pt-0">
        <div className="min-w-0 lg:pr-[2vw] lg:pt-[2.8vw]">
          <p className="inline-flex items-center gap-1.5 rounded-full border border-[rgba(160,255,0,0.22)] bg-[rgba(8,12,10,0.7)] px-[1.1em] py-[0.55em] text-[length:var(--dl-hero-pill)]">
            <Zap className="h-[1em] w-[1em] shrink-0 text-accent" aria-hidden="true" />
            <span className="whitespace-nowrap font-medium tracking-[0.01em] text-content-secondary">
              Deployments are now 2x faster
            </span>
          </p>

          <h1
            id="hero-heading"
            className="mt-[0.55em] max-w-[9em] text-[length:var(--dl-hero-h1)] font-bold leading-[1.04] tracking-[-0.038em] text-[#f4f4f4]"
          >
            Deploy your code.
            <br />
            Anywhere.
            <br />
            <span className="text-[#9cff00]">In seconds.</span>
          </h1>

          <p className="mt-[1.45em] max-w-[25em] text-[length:var(--dl-hero-lead)] leading-[1.6] text-white/65">
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
                <ArrowRight className="h-[1.2em] w-[1.2em] transition-transform duration-200 group-hover:translate-x-0.5" aria-hidden="true" />
              </Button>
            </Link>

            <Link to="/login">
              <Button
                variant="secondary"
                size="md"
                className="h-[3.6em] justify-center gap-[0.8em] rounded-[0.5em] border-[rgba(255,255,255,0.24)] bg-[rgba(10,13,12,0.65)] px-[1.5em] text-[1em] font-medium"
              >
                <GithubIcon className="h-[1.2em] w-[1.2em]" />
                Connect GitHub
              </Button>
            </Link>
          </div>

          <ul className="hero-capabilities mt-[2.6em] grid w-full max-w-full grid-cols-2 items-center gap-x-[0.9em] gap-y-[0.9em] text-[length:var(--dl-hero-feat)] sm:grid-cols-3 lg:flex lg:w-full lg:flex-nowrap lg:justify-between lg:gap-x-[1.2em]">
            {CAPABILITIES.map((capability) => (
              <li key={capability.label} className="hero-capability flex min-w-0 items-center gap-[0.5em]">
                <span className="flex h-[2.6em] w-[2.6em] shrink-0 items-center justify-center rounded-full border border-[rgba(160,255,0,0.45)]">
                  <capability.icon className="h-[1.15em] w-[1.15em] text-accent" aria-hidden="true" />
                </span>
                <span className="whitespace-nowrap font-medium text-white/68">
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
