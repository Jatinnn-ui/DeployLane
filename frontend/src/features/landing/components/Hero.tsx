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
function HeroServerPoster() {
  return (
    <div className="absolute inset-0 flex items-center justify-center" aria-hidden="true">
      <svg viewBox="0 0 560 460" className="h-full w-full max-w-[620px]">
        <defs>
          <linearGradient id="server-top" x1="0" x2="1" y1="0" y2="1">
            <stop offset="0" stopColor="#333a3c" />
            <stop offset="1" stopColor="#181d1e" />
          </linearGradient>
          <linearGradient id="server-front" x1="0" x2="1" y1="0" y2="1">
            <stop offset="0" stopColor="#202627" />
            <stop offset="1" stopColor="#101415" />
          </linearGradient>
          <radialGradient id="server-glow">
            <stop offset="0" stopColor="#a8f000" stopOpacity="0.11" />
            <stop offset="0.42" stopColor="#a8f000" stopOpacity="0.04" />
            <stop offset="1" stopColor="#a8f000" stopOpacity="0" />
          </radialGradient>
          <filter id="lime-soft-glow" x="-100%" y="-100%" width="300%" height="300%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <circle cx="285" cy="250" r="210" fill="url(#server-glow)" />

        {[178, 145, 112].map((rx, i) => (
          <ellipse
            key={rx}
            cx="285"
            cy="352"
            rx={rx}
            ry={rx * 0.29}
            fill="none"
            stroke="#a8f000"
            strokeOpacity={0.12 + i * 0.1}
            strokeWidth="1"
          />
        ))}
        <ellipse cx="285" cy="346" rx="130" ry="40" fill="#0e1213" stroke="#a8f000" strokeOpacity="0.55" />

        {/* Three-quarter server silhouette: top, right side, then dominant front. */}
        <path d="M174 166 L229 130 L407 158 L353 194 Z" fill="url(#server-top)" stroke="#515b5e" strokeWidth="2" />
        <path d="M353 194 L407 158 L405 315 L353 347 Z" fill="#111617" stroke="#343c3e" strokeWidth="2" />
        <rect x="174" y="166" width="179" height="181" rx="18" fill="url(#server-front)" stroke="#4a5355" strokeWidth="2" />
        <rect x="187" y="181" width="153" height="148" rx="12" fill="#151a1b" stroke="#2d3537" />
        <rect x="211" y="207" width="106" height="90" rx="9" fill="#0f1314" stroke="#242b2d" />

        <path d="M180 205 L348 205 L400 174" fill="none" stroke="#a8f000" strokeWidth="3" filter="url(#lime-soft-glow)" />
        <path d="M180 321 L348 321 L399 291" fill="none" stroke="#a8f000" strokeOpacity="0.65" strokeWidth="2" />
        <path d="M251 225 L287 252 L251 279" fill="none" stroke="#b7ff19" strokeWidth="13" strokeLinecap="round" strokeLinejoin="round" filter="url(#lime-soft-glow)" />
        <circle cx="325" cy="308" r="5" fill="#43dd82" filter="url(#lime-soft-glow)" />

        {[0, 1, 2, 3, 4].map((slot) => (
          <rect key={slot} x="373" y={204 + slot * 16} width="22" height="3" rx="1.5" fill="#4a5355" />
        ))}
      </svg>
    </div>
  );
}

/** Existing cube poster retained for the separate lower-page scene. */
export function HeroPoster() {
  return (
    <div className="absolute inset-0 flex items-center justify-center" aria-hidden="true">
      <svg viewBox="0 0 400 400" className="h-full max-h-[520px] w-full max-w-[520px]">
        {[188, 152, 118, 88].map((radius, index) => (
          <ellipse
            key={radius}
            cx="200"
            cy="262"
            rx={radius}
            ry={radius * 0.32}
            fill="none"
            stroke="#a8f000"
            strokeOpacity={0.07 + index * 0.07}
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

        {/* Visual cell only: local floor/grid and glow sit behind the WebGL product render.
            The left hero content and parent layout remain unchanged. */}
        <div className="relative h-[420px] sm:h-[500px] lg:h-[560px] lg:-mr-[7%] xl:h-[600px]">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-[9%_3%_4%_3%] opacity-40 [mask-image:radial-gradient(ellipse_62%_54%_at_52%_54%,#000_0%,transparent_100%)]"
          >
            <div className="hero-grid absolute inset-0 origin-bottom [transform:perspective(520px)_rotateX(63deg)_scale(1.18)]" />
          </div>
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-[4%_2%_0_2%] rounded-full"
            style={{
              background:
                'radial-gradient(circle, rgba(168,240,0,0.09) 0%, rgba(168,240,0,0.035) 35%, transparent 68%)',
            }}
          />

          {/* Below lg the stage cards sit in a row underneath, so the server keeps the upper
              area and remains the dominant object rather than shrinking behind cards. */}
          <div className="absolute inset-x-0 bottom-[84px] top-0 z-[1] lg:inset-0">
            <WebglBoundary fallback={<HeroServerPoster />}>
              {renderScene ? (
                <Suspense fallback={<HeroServerPoster />}>
                  <HeroScene reducedMotion={reducedMotion} simplified={isCompact} />
                </Suspense>
              ) : (
                <HeroServerPoster />
              )}
            </WebglBoundary>
          </div>

          <div className="absolute inset-0 z-[2]">
            <PipelineOverlay reducedMotion={reducedMotion} />
          </div>
        </div>
      </div>
    </section>
  );
}
