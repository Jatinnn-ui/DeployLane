import { useEffect, useRef, useState } from 'react';
import {
  ArrowRight,
  Box,
  CheckCircle2,
  Globe,
  Loader2,
  Rocket,
  ShieldCheck,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { Button } from '@/components/ui/button';
import { usePrefersReducedMotion } from '@/lib/use-media-query';
import type { IconComponent } from './icon-type';
import { InfrastructureScene } from './InfrastructureScene';
import { Wordmark } from './Wordmark';

const PILLARS: Array<{
  title: string;
  body: string;
  icon: LucideIcon;
  imagePosition: 'left center' | 'center center' | 'right center';
}> = [
  {
    title: 'Global Infrastructure',
    body: 'Deploy to data centers around the world with 99.99% uptime.',
    icon: Globe,
    imagePosition: 'left center',
  },
  {
    title: 'Speed & Performance',
    body: 'Optimized builds and edge delivery for the fastest experience.',
    icon: Zap,
    imagePosition: 'center center',
  },
  {
    title: 'Security First',
    body: 'Automatic HTTPS, isolated environments, and secure by default.',
    icon: ShieldCheck,
    imagePosition: 'right center',
  },
];

/* ─────────────────────────────────────────────────────────────
   Live deployment simulator.

   The left panel runs a self-contained pipeline loop: each stage
   lights up in sequence, the elapsed timer ticks, and when the run
   completes the deploy count + last-deploy time update — so the
   section demonstrates the product instead of just decorating it.
   ───────────────────────────────────────────────────────────── */

interface Stage {
  id: string;
  label: string;
  icon: IconComponent | LucideIcon;
  /** How long this stage takes in the simulation (ms). */
  duration: number;
}

const STAGES: Stage[] = [
  { id: 'clone',  label: 'Cloning repository',  icon: GithubIcon, duration: 1400 },
  { id: 'build',  label: 'Building image',      icon: Box,        duration: 2600 },
  { id: 'deploy', label: 'Deploying container', icon: Rocket,     duration: 1800 },
  { id: 'live',   label: 'Live & healthy',      icon: Globe,      duration: 1600 },
];

const TOTAL_MS = STAGES.reduce((sum, s) => sum + s.duration, 0);

type StageState = 'pending' | 'active' | 'done';

function useDeploymentLoop(enabled: boolean) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [elapsed, setElapsed] = useState(0);
  const [deployCount, setDeployCount] = useState(1247);
  const [lastDeploy, setLastDeploy] = useState(0); // seconds since last completed run
  const timers = useRef<number[]>([]);

  useEffect(() => {
    if (!enabled) {
      // Reduced motion / disabled: show a completed state, no ticking.
      setActiveIndex(STAGES.length);
      return;
    }

    let cancelled = false;

    const clearAll = () => {
      timers.current.forEach((t) => window.clearTimeout(t));
      timers.current.forEach((t) => window.clearInterval(t));
      timers.current = [];
    };

    const runOnce = () => {
      if (cancelled) return;
      setActiveIndex(0);
      setElapsed(0);

      // Elapsed-time ticker for the active run. 250ms (4/sec) instead of 100ms (10/sec)
      // keeps the timer readable while cutting re-renders of this heavy scene subtree by 60%.
      const start = performance.now();
      const ticker = window.setInterval(() => {
        const ms = performance.now() - start;
        setElapsed(Math.min(ms, TOTAL_MS));
        if (ms >= TOTAL_MS) window.clearInterval(ticker);
      }, 250);
      timers.current.push(ticker);

      // Advance through stages.
      let acc = 0;
      STAGES.forEach((stage, i) => {
        acc += stage.duration;
        const t = window.setTimeout(() => {
          if (cancelled) return;
          if (i < STAGES.length - 1) {
            setActiveIndex(i + 1);
          } else {
            // Run complete.
            setActiveIndex(STAGES.length);
            setDeployCount((c) => c + 1);
            setLastDeploy(0);
          }
        }, acc);
        timers.current.push(t);
      });

      // Pause on the success state, then restart the loop.
      const restart = window.setTimeout(() => {
        if (!cancelled) runOnce();
      }, TOTAL_MS + 3200);
      timers.current.push(restart);
    };

    // "Seconds since last deploy" counter — advances while idle after a run.
    const idleTicker = window.setInterval(() => {
      setLastDeploy((s) => s + 1);
    }, 1000);
    timers.current.push(idleTicker);

    runOnce();

    return () => {
      cancelled = true;
      clearAll();
    };
  }, [enabled]);

  const stageStateFor = (i: number): StageState => {
    if (activeIndex >= STAGES.length) return 'done';
    if (i < activeIndex) return 'done';
    if (i === activeIndex) return 'active';
    return 'pending';
  };

  const complete = activeIndex >= STAGES.length;
  const progress = complete ? 1 : Math.min(elapsed / TOTAL_MS, 1);

  return { activeIndex, stageStateFor, complete, progress, elapsed, deployCount, lastDeploy };
}

/* ─── Small floating chip over the scene ─────────────────────── */
function SceneChip({
  value,
  label,
  className,
}: {
  value: string;
  label: string;
  className?: string;
}) {
  return (
    <div
      className={`bfd-chip pointer-events-none absolute z-20 flex flex-col gap-0.5 rounded-xl border border-[rgba(255,255,255,0.1)] bg-[rgba(10,13,12,0.9)] px-3 py-2 shadow-[0_8px_24px_rgba(0,0,0,0.5)] backdrop-blur-[6px] ${className ?? ''}`}
    >
      <span className="flex items-center gap-1.5">
        <span className="relative flex h-1.5 w-1.5 shrink-0">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent opacity-60" />
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-accent" />
        </span>
        <span className="text-[8.5px] font-medium uppercase tracking-[0.08em] text-accent/80">
          {label}
        </span>
      </span>
      <span className="text-[16px] font-bold leading-none tracking-tight text-white">{value}</span>
    </div>
  );
}

export function BuiltForDevelopers() {
  const reducedMotion = usePrefersReducedMotion();
  const { stageStateFor, complete, progress, elapsed, deployCount, lastDeploy } =
    useDeploymentLoop(!reducedMotion);

  const elapsedLabel = complete ? '3.4s' : `${(elapsed / 1000).toFixed(1)}s`;

  return (
    <section id="built-for-developers" className="pb-4" aria-labelledby="pillars-heading">
      <div className="landing-container">
        <div className="landing-panel grid gap-8 px-5 py-7 sm:px-7 sm:py-8 md:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] md:items-stretch md:gap-6">

          {/* ══════════════════════════════════════════════
              LEFT — live deployment simulator
              ══════════════════════════════════════════════ */}
          <div
            className="relative flex min-h-[340px] flex-col overflow-hidden rounded-2xl border border-[rgba(168,240,0,0.14)]"
            style={{
              background:
                'linear-gradient(145deg, rgba(14,18,15,0.98) 0%, rgba(8,11,9,0.99) 100%)',
            }}
          >
            {/* Ambient bloom */}
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-0"
              style={{
                background:
                  'radial-gradient(ellipse at 50% 42%, rgba(160,255,0,0.08) 0%, transparent 66%)',
              }}
            />

            {/* Terminal header */}
            <div className="relative z-10 flex items-center justify-between border-b border-[rgba(255,255,255,0.06)] px-4 py-2.5">
              <div className="flex items-center gap-2">
                <span className="h-2 w-2 rounded-full bg-[#ff5f57]" />
                <span className="h-2 w-2 rounded-full bg-[#febc2e]" />
                <span className="h-2 w-2 rounded-full bg-[#28c840]" />
              </div>
              <span className="font-mono text-[10px] tracking-wider text-white/30">
                deploylane · live pipeline
              </span>
              <span
                className={`flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[9px] font-semibold uppercase tracking-wider transition-colors ${
                  complete ? 'bg-[rgba(68,221,130,0.14)] text-[#7ceaa8]' : 'bg-accent/10 text-accent'
                }`}
              >
                <span
                  className={`h-1 w-1 rounded-full ${complete ? 'bg-[#43dd82]' : 'animate-pulse bg-accent'}`}
                />
                {complete ? 'Ready' : 'Deploying'}
              </span>
            </div>

            {/* Scene with floating chips */}
            <div className="relative min-h-[190px] flex-1">
              <InfrastructureScene className="h-full w-full" />

              <SceneChip value={String(deployCount.toLocaleString())} label="deploys today" className="left-3 top-3" />
              <SceneChip value="99.99%" label="uptime" className="right-3 top-3" />

              {/* Brand identity anchored to the floor of the scene — fills the empty lower area
                  with the product's own voice instead of dead space, without touching the server. */}
              <div className="pointer-events-none absolute inset-x-0 bottom-0 z-10 flex flex-col items-center gap-2 px-4 pb-5 text-center">
                <span className="flex items-center gap-2">
                  <Wordmark textClass="text-[15px]" />
                </span>
                <p className="text-[10.5px] font-medium leading-relaxed tracking-[0.02em] text-white/45">
                  git push → live URL. Every commit, deployed for you.
                </p>
                <div className="mt-0.5 flex items-center gap-3 text-[8.5px] font-semibold uppercase tracking-[0.12em] text-white/35">
                  <span className="flex items-center gap-1">
                    <span className="h-1 w-1 rounded-full bg-accent" /> Zero config
                  </span>
                  <span className="h-2.5 w-px bg-white/10" />
                  <span className="flex items-center gap-1">
                    <span className="h-1 w-1 rounded-full bg-accent" /> Global edge
                  </span>
                  <span className="h-2.5 w-px bg-white/10" />
                  <span className="flex items-center gap-1">
                    <span className="h-1 w-1 rounded-full bg-accent" /> AI-assisted
                  </span>
                </div>
              </div>
            </div>

            {/* Live pipeline stepper */}
            <div className="relative z-10 border-t border-[rgba(255,255,255,0.07)] bg-[rgba(6,9,7,0.6)] px-4 py-3.5 backdrop-blur-sm">
              {/* Progress bar */}
              <div className="mb-3 h-1 w-full overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-[#6c9700] to-accent transition-[width] duration-200 ease-linear"
                  style={{ width: `${progress * 100}%` }}
                />
              </div>

              {/* Stage row */}
              <div className="flex items-center justify-between gap-1">
                {STAGES.map((stage) => {
                  const state = stageStateFor(STAGES.indexOf(stage));
                  const Icon = stage.icon;
                  return (
                    <div key={stage.id} className="flex flex-1 flex-col items-center gap-1.5">
                      <span
                        className={`flex h-7 w-7 items-center justify-center rounded-lg border transition-all duration-300 ${
                          state === 'done'
                            ? 'border-accent/40 bg-accent/15 text-accent'
                            : state === 'active'
                              ? 'border-accent bg-accent/20 text-accent shadow-[0_0_16px_rgba(168,240,0,0.35)]'
                              : 'border-[rgba(255,255,255,0.08)] bg-[rgba(255,255,255,0.02)] text-white/25'
                        }`}
                      >
                        {state === 'done' ? (
                          <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                        ) : state === 'active' ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                        ) : (
                          <Icon className="h-3.5 w-3.5" strokeWidth={1.8} aria-hidden="true" />
                        )}
                      </span>
                      <span
                        className={`text-center text-[8.5px] font-medium leading-tight transition-colors duration-300 ${
                          state === 'pending' ? 'text-white/30' : 'text-white/70'
                        }`}
                      >
                        {stage.label}
                      </span>
                    </div>
                  );
                })}
              </div>

              {/* Status line */}
              <div className="mt-3 flex items-center justify-between border-t border-[rgba(255,255,255,0.05)] pt-2.5 font-mono text-[10px]">
                <span className={complete ? 'text-[#7ceaa8]' : 'text-white/50'}>
                  {complete
                    ? `✓ deployed · ${lastDeploy}s ago`
                    : `› building… ${elapsedLabel}`}
                </span>
                <span className="text-white/30">us-east-1</span>
              </div>
            </div>
          </div>

          {/* ══════════════════════════════════════════════
              RIGHT — feature cards + CTA (unchanged)
              ══════════════════════════════════════════════ */}
          <div className="flex flex-col justify-center">
            <p className="text-[9.5px] font-semibold uppercase tracking-[0.14em] text-accent">
              Built for developers
            </p>
            <h2
              id="pillars-heading"
              className="mt-2.5 text-[22px] font-bold leading-[1.16] tracking-[-0.03em] text-content-primary sm:text-[26px]"
            >
              Infrastructure that just works.
            </h2>
            <p className="mt-3 text-[13px] leading-[1.65] text-content-secondary">
              Enterprise-grade infrastructure delivered through a developer-first experience. No
              ops team required.
            </p>

            <ul className="mt-5 grid gap-3 sm:grid-cols-2 md:grid-cols-3">
              {PILLARS.map((pillar, index) => (
                <li
                  key={pillar.title}
                  className="group rounded-2xl border border-border-subtle bg-surface p-4 transition-[border-color,box-shadow,transform] duration-300 hover:-translate-y-1 hover:border-accent-border hover:shadow-[0_14px_32px_rgba(0,0,0,0.28)]"
                >
                  <div className="developer-pillar-visual mb-3.5 aspect-square w-full overflow-hidden rounded-xl border border-accent-border/55 bg-canvas-secondary">
                    <div
                      aria-hidden="true"
                      className="developer-pillar-art h-full w-full transition-transform duration-500 group-hover:scale-[1.04]"
                      style={{
                        backgroundImage: "url('/landing/developer-pillars-triptych.png')",
                        backgroundPosition: pillar.imagePosition,
                        backgroundRepeat: 'no-repeat',
                        backgroundSize: '300% auto',
                        '--pillar-delay': `${index * -1.4}s`,
                      } as React.CSSProperties}
                    />
                  </div>
                  <span className="flex h-7 w-7 items-center justify-center rounded-lg border border-accent-border bg-accent-soft text-accent">
                    <pillar.icon className="h-3.5 w-3.5" strokeWidth={2} aria-hidden="true" />
                  </span>
                  <h3 className="mt-3.5 text-[13px] font-semibold tracking-[-0.01em] text-content-primary">
                    {pillar.title}
                  </h3>
                  <p className="mt-1.5 text-[11.5px] leading-[1.55] text-content-secondary">
                    {pillar.body}
                  </p>
                </li>
              ))}
            </ul>

            <div className="mt-8">
              <p className="text-[9.5px] font-semibold uppercase tracking-[0.14em] text-accent">
                Ready to deploy?
              </p>
              <p className="ship-headline mt-3 text-[22px] font-bold leading-[1.16] tracking-[-0.03em] text-content-primary sm:text-[26px]">
                Ship faster. Scale further.
                <br />
                DeployLane has your back.
              </p>
              <p className="mt-3.5 max-w-[440px] text-[13px] leading-[1.6] text-content-secondary">
                Join thousands of developers building and deploying modern applications with
                confidence.
              </p>

              <div className="mt-6 flex flex-wrap items-center gap-3">
                <Link to="/login" className="w-full sm:w-auto">
                  <Button
                    variant="primary"
                    size="md"
                    className="group h-11 w-full justify-center gap-3 rounded-[7px] px-5 text-[14px] font-medium sm:w-[190px]"
                  >
                    Start Deploying Now
                    <ArrowRight
                      className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                      aria-hidden="true"
                    />
                  </Button>
                </Link>

                <a href="mailto:hello@deploylane.online" className="w-full sm:w-auto">
                  <Button
                    variant="secondary"
                    size="md"
                    className="h-11 w-full justify-center rounded-[7px] px-5 text-[14px] font-medium sm:w-[190px]"
                  >
                    Talk to an Expert
                  </Button>
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
