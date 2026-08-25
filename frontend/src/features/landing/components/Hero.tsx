import { ArrowRight, GitBranch, Globe, ShieldCheck, Sparkles, Terminal, Zap } from 'lucide-react';
import { Suspense, lazy, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { Button } from '@/components/ui/button';
import { useMediaQuery, usePrefersReducedMotion } from '@/lib/use-media-query';
import { WebglBoundary } from '../hero/WebglBoundary';
import { PipelineOverlay } from './PipelineOverlay';

/**
 * three.js, R3F and drei are the heaviest thing on this page by a wide margin. Splitting
 * them behind a dynamic import keeps them out of the entry chunk, so the headline and CTAs
 * paint without waiting on a renderer the visitor may never see.
 */
const HeroScene = lazy(() =>
  import('../hero/HeroScene').then((module) => ({ default: module.HeroScene })),
);

const CAPABILITIES = [
  { label: 'Git Integration', icon: GitBranch },
  { label: 'Automated Builds', icon: Terminal },
  { label: 'Instant Deploys', icon: Zap },
  { label: 'Global CDN', icon: Globe },
  { label: 'Secure by Default', icon: ShieldCheck },
];

/**
 * Static stand-in shown before the renderer is ready, and permanently if WebGL is
 * unavailable. Shares the cube-on-rings language of the real scene so the handover is quiet.
 */
export function HeroPoster() {
  return (
    <div className="absolute inset-0 flex items-center justify-center" aria-hidden="true">
      <svg viewBox="0 0 400 400" className="h-full max-h-[520px] w-full max-w-[520px]">
        {[188, 152, 118, 88].map((r, i) => (
          <ellipse
            key={r}
            cx="200"
            cy="262"
            rx={r}
            ry={r * 0.32}
            fill="none"
            stroke="#a8f000"
            strokeOpacity={0.07 + i * 0.07}
            strokeWidth="1"
          />
        ))}
        <ellipse cx="200" cy="262" rx="74" ry="24" fill="#0e1112" />
        <rect x="152" y="150" width="96" height="96" rx="14" fill="#14181a" />
        <rect
          x="152"
          y="150"
          width="96"
          height="96"
          rx="14"
          fill="none"
          stroke="#ffffff"
          strokeOpacity="0.1"
        />
        <path
          d="M186 176 L212 198 L186 220"
          fill="none"
          stroke="#b7ff19"
          strokeWidth="9"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <rect x="158" y="238" width="84" height="2" rx="1" fill="#a8f000" fillOpacity="0.8" />
      </svg>
    </div>
  );
}

export function Hero() {
  const reducedMotion = usePrefersReducedMotion();
  const isCompact = useMediaQuery('(max-width: 1023px)');
  const [renderScene, setRenderScene] = useState(false);

  useEffect(() => {
    // Hand the main thread back for first paint before creating a GL context.
    const frame = window.requestAnimationFrame(() => setRenderScene(true));
    return () => window.cancelAnimationFrame(frame);
  }, []);

  return (
    <section className="relative overflow-hidden" aria-labelledby="hero-heading">
      <div
        aria-hidden="true"
        className="hero-glow pointer-events-none absolute right-[-14%] top-[-20%] h-[900px] w-[900px] rounded-full lg:right-0"
      />

      <div className="landing-container relative grid items-center gap-10 pb-14 pt-12 lg:grid-cols-[minmax(0,0.82fr)_minmax(0,1.18fr)] lg:gap-4 lg:pb-20 lg:pt-16">
        <div className="max-w-[520px]">
          <p className="inline-flex items-center gap-2 rounded-full border border-border-subtle bg-surface px-3 py-1.5">
            <Sparkles className="h-3 w-3 text-accent" aria-hidden="true" />
            <span className="text-[12px] font-medium text-content-secondary">
              Deployments are now 2x faster
            </span>
          </p>

          <h1
            id="hero-heading"
            className="mt-7 text-[38px] font-bold leading-[1.06] tracking-[-0.035em] text-content-primary sm:text-[50px] lg:text-[56px] xl:text-[62px]"
          >
            Deploy your code.
            <br />
            Anywhere.
            <br />
            <span className="text-accent">In seconds.</span>
          </h1>

          <p className="mt-6 max-w-[430px] text-[15.5px] leading-[1.62] text-content-secondary">
            DeployLane helps developers build, deploy, and manage applications with zero friction.
            From git push to global scale.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link to="/login">
              <Button variant="primary" size="md" className="group">
                Start Deploying
                <ArrowRight
                  className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </Button>
            </Link>

            <Link to="/login">
              <Button variant="secondary" size="md">
                <GithubIcon className="h-4 w-4" />
                Connect GitHub
              </Button>
            </Link>
          </div>

          <ul className="mt-9 flex flex-wrap items-center gap-x-5 gap-y-3">
            {CAPABILITIES.map((capability) => (
              <li key={capability.label} className="flex items-center gap-1.5">
                <capability.icon className="h-3.5 w-3.5 text-accent" aria-hidden="true" />
                <span className="text-[11.5px] font-medium text-content-secondary">
                  {capability.label}
                </span>
              </li>
            ))}
          </ul>
        </div>

        {/* Visual cell. Bleeds past the content rail on desktop so the cube can sit large
            without squeezing the copy column. */}
        <div className="relative h-[420px] sm:h-[500px] lg:h-[560px] lg:-mr-[7%] xl:h-[600px]">
          {/* Below lg the stage cards sit in a row underneath, so the scene stops short of
              the bottom rather than rendering behind them. */}
          <div className="absolute inset-x-0 bottom-[84px] top-0 lg:inset-0">
            <WebglBoundary fallback={<HeroPoster />}>
              {renderScene ? (
                <Suspense fallback={<HeroPoster />}>
                  <HeroScene reducedMotion={reducedMotion} simplified={isCompact} />
                </Suspense>
              ) : (
                <HeroPoster />
              )}
            </WebglBoundary>
          </div>

          <PipelineOverlay reducedMotion={reducedMotion} />
        </div>
      </div>
    </section>
  );
}
