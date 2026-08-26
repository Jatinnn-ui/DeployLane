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
        <div className="lg:pr-6 lg:pt-[29px]">
          <p className="inline-flex h-[22px] items-center gap-1.5 rounded-full border border-[rgba(160,255,0,0.22)] bg-[rgba(8,12,10,0.7)] px-[11px]">
            <Zap className="h-2 w-2 shrink-0 text-accent" aria-hidden="true" />
            <span className="whitespace-nowrap text-[8px] font-medium tracking-[0.01em] text-content-secondary">
              Deployments are now 2x faster
            </span>
          </p>

          <h1
            id="hero-heading"
            className="mt-[21px] max-w-[350px] text-[30px] font-bold leading-[1.04] tracking-[-0.038em] text-[#f4f4f4] sm:text-[35px] lg:text-[39px]"
          >
            Deploy your code.
            <br />
            Anywhere.
            <br />
            <span className="text-[#9cff00]">In seconds.</span>
          </h1>

          <p className="mt-[17px] max-w-[300px] text-[12px] leading-[1.6] text-white/65">
            DeployLane helps developers build, deploy, and manage applications with zero friction.
            From git push to global scale.
          </p>

          <div className="mt-[27px] flex flex-wrap items-center gap-3">
            <Link to="/login">
              <Button
                variant="primary"
                size="md"
                className="group h-9 min-w-[130px] justify-center gap-2 rounded-[5px] px-4 text-[10px] font-medium"
              >
                Start Deploying
                <ArrowRight
                  className="h-3 w-3 transition-transform duration-200 group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </Button>
            </Link>

            <Link to="/login">
              <Button
                variant="secondary"
                size="md"
                className="h-9 justify-center gap-2 rounded-[5px] border-[rgba(255,255,255,0.24)] bg-[rgba(10,13,12,0.65)] px-[15px] text-[10px] font-medium"
              >
                <GithubIcon className="h-3 w-3" />
                Connect GitHub
              </Button>
            </Link>
          </div>

          {/* Runs wider than the copy column in the reference, so it overflows the grid cell
              rather than wrapping onto a second line. */}
          <ul className="mt-[31px] flex w-max max-w-full flex-wrap items-center gap-x-[13px] gap-y-2 lg:flex-nowrap">
            {CAPABILITIES.map((capability) => (
              <li key={capability.label} className="flex shrink-0 items-center gap-1">
                <span className="flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-full border border-[rgba(160,255,0,0.45)]">
                  <capability.icon className="h-2 w-2 text-accent" aria-hidden="true" />
                </span>
                <span className="whitespace-nowrap text-[7px] font-medium text-white/68">
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
