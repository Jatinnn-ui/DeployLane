import { ArrowRight } from 'lucide-react';
import { Suspense, lazy, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
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

/**
 * Static stand-in shown before the renderer is ready, and permanently if WebGL is
 * unavailable. Shares the ring language of the real scene so the handover is quiet.
 */
function HeroPoster() {
  return (
    <div className="absolute inset-0 flex items-center justify-center" aria-hidden="true">
      <svg viewBox="0 0 400 400" className="h-full max-h-[520px] w-full max-w-[520px]">
        {[196, 158, 120].map((r, i) => (
          <circle
            key={r}
            cx="200"
            cy="248"
            r={r}
            fill="none"
            stroke="#a8f000"
            strokeOpacity={0.06 + i * 0.06}
            strokeWidth="1"
          />
        ))}
        <ellipse cx="200" cy="248" rx="96" ry="30" fill="#101314" />
        <rect x="146" y="150" width="108" height="82" rx="12" fill="#15191a" />
        <rect x="146" y="150" width="108" height="82" rx="12" fill="none" stroke="#ffffff" strokeOpacity="0.1" />
        <rect x="166" y="206" width="68" height="2" rx="1" fill="#a8f000" fillOpacity="0.7" />
        <circle cx="238" cy="220" r="3" fill="#43dd82" />
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
      {/* technical grid, faded out toward the edges so it never looks like a table */}
      <div
        aria-hidden="true"
        className="hero-grid pointer-events-none absolute inset-0 opacity-60 [mask-image:radial-gradient(ellipse_70%_60%_at_60%_35%,#000_0%,transparent_100%)]"
      />
      {/* single warm lime light source behind the server */}
      <div
        aria-hidden="true"
        className="hero-glow pointer-events-none absolute right-[-10%] top-[-14%] h-[820px] w-[820px] rounded-full lg:right-[2%]"
      />

      <div className="landing-container relative grid items-center gap-10 pb-16 pt-14 lg:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)] lg:gap-6 lg:pb-28 lg:pt-20">
        <div className="max-w-[560px]">
          <p className="inline-flex items-center gap-2 rounded-full border border-border-subtle bg-surface px-3 py-1.5">
            <span
              className="h-1.5 w-1.5 rounded-full bg-accent"
              style={reducedMotion ? undefined : { animation: 'status-pulse 2.4s ease-in-out infinite' }}
              aria-hidden="true"
            />
            <span className="text-[11.5px] font-semibold uppercase tracking-[0.08em] text-content-secondary">
              Push to deploy · Zero config
            </span>
          </p>

          <h1
            id="hero-heading"
            className="mt-6 text-[34px] font-bold leading-[1.05] tracking-[-0.03em] text-content-primary sm:text-[44px] lg:text-[56px] xl:text-[64px]"
          >
            Ship every push to a <span className="text-accent">live URL</span>.
          </h1>

          <p className="mt-5 text-[16px] leading-[1.6] text-content-secondary sm:text-[17.5px]">
            DeployLane builds your GitHub repository into a container, rolls it out with zero
            downtime, and streams logs, metrics and AI failure analysis while it happens.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link to="/login">
              <Button variant="primary" size="lg" className="group">
                Deploy your first project
                <ArrowRight
                  className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </Button>
            </Link>

            <a href="#how-it-works">
              <Button variant="secondary" size="lg">
                See how it works
              </Button>
            </a>
          </div>

          <p className="mt-6 font-mono text-[11.5px] text-content-muted">
            GitHub OAuth · No credit card · Free tier included
          </p>
        </div>

        {/* Visual cell. Bleeds slightly past the content rail on desktop so the server has
            room to sit large without pushing the copy narrower. */}
        <div className="relative h-[430px] sm:h-[500px] lg:h-[600px] lg:-mr-[5%]">
          {/* Below lg the stage cards sit in a row underneath, so the scene stops short of
              the bottom rather than rendering behind them. */}
          <div className="absolute inset-x-0 top-0 bottom-[92px] lg:inset-0">
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
