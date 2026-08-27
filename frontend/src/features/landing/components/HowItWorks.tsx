import { Globe, Rocket, Zap } from 'lucide-react';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { cn } from '@/lib/utils';
import type { IconComponent } from './icon-type';
import {
  BuildWatermark,
  DeployWatermark,
  LiveWatermark,
  RepositoryWatermark,
} from './workflow-watermarks';

/**
 * Card radius in px. The wire SVG draws its rounded rect with this same value, which is how the
 * travelling current follows the card's real corners instead of cutting across them.
 */
const CARD_RADIUS = 16;

/**
 * Phase of the 6s current loop each card starts at, as a percentage of the perimeter.
 *
 * These are complements (0 / 75 / 50 / 25) rather than 0 / 25 / 50 / 75 on purpose: a card's pulse
 * leaves its top edge at `(100 - phase)%` of the cycle, so this ordering makes the four top edges
 * fire left to right - card 1, then 2, then 3, then 4 - which is the pipeline direction. The plain
 * ascending order staggers them equally but runs the energy backwards.
 */
const CARD_PHASE = [0, 75, 50, 25];

const STEPS: Array<{
  n: number;
  title: string;
  body: string;
  icon: IconComponent;
  watermark: (props: { className?: string }) => React.JSX.Element;
  variant: string;
}> = [
  {
    n: 1,
    title: 'Connect Repository',
    body: 'Link your Git repository and choose a branch to deploy.',
    icon: GithubIcon,
    watermark: RepositoryWatermark,
    variant: 'workflow-card-repository',
  },
  {
    n: 2,
    title: 'Build & Test',
    body: 'We install dependencies, run tests, and build your application.',
    icon: Zap,
    watermark: BuildWatermark,
    variant: 'workflow-card-build',
  },
  {
    n: 3,
    title: 'Deploy',
    body: 'Your application is deployed to our global infrastructure automatically.',
    icon: Rocket,
    watermark: DeployWatermark,
    variant: 'workflow-card-deploy',
  },
  {
    n: 4,
    title: 'Go Live',
    body: 'Get a live URL instantly with global CDN and high availability.',
    icon: Globe,
    watermark: LiveWatermark,
    variant: 'workflow-card-live',
  },
];

/**
 * The card border as a wire carrying current.
 *
 * The rect fills the SVG viewport and is stroked across the card's edge, so its outer half is
 * clipped by the viewport and what remains is a hairline flush with the card - no `calc()` in
 * geometry attributes, and correct at every card width.
 *
 * `pathLength="100"` is what makes this responsive: dash lengths and the offset animation are then
 * expressed as percentages of the perimeter, so the pulse stays the same fraction of the border and
 * the loop closes seamlessly whatever the card measures. The trail and the brighter core share one
 * duration and delay and differ only in dash phase, which keeps the core pinned to the leading edge.
 */
function CardWire() {
  const rect = {
    x: 0,
    y: 0,
    width: '100%',
    height: '100%',
    rx: CARD_RADIUS,
    pathLength: 100,
    fill: 'none',
  } as const;

  return (
    <svg className="workflow-wire" aria-hidden="true" focusable="false">
      <rect {...rect} className="workflow-wire-track" />
      <rect {...rect} className="workflow-wire-trail" />
      <rect {...rect} className="workflow-wire-core" />
    </svg>
  );
}

export function HowItWorks() {
  return (
    <section id="product" className="pb-4" aria-labelledby="how-it-works-heading">
      <div className="landing-container">
        <div className="landing-panel px-5 py-7 sm:px-7 sm:py-8">
          <p className="text-[9.5px] font-semibold uppercase tracking-[0.14em] text-accent">
            How DeployLane works
          </p>
          <h2
            id="how-it-works-heading"
            className="mt-3 text-[25px] font-bold leading-[1.14] tracking-[-0.03em] text-content-primary sm:text-[31px]"
          >
            From code to production in <span className="text-accent">minutes</span>
          </h2>

          <ol className="mt-7 grid gap-4 sm:grid-cols-2 lg:grid-cols-4 lg:gap-5">
            {STEPS.map((step, index) => (
              <li key={step.n} className="relative">
                {/* Dashed rail toward the next step. Desktop only, and suppressed on the last
                    card, so it never points into empty space. */}
                {index < STEPS.length - 1 && (
                  <span
                    aria-hidden="true"
                    className="step-connector absolute -right-[18px] top-[26px] hidden h-px w-3.5 lg:block"
                    style={{ '--link-delay': `${1.4 + index * 1.5}s` } as React.CSSProperties}
                  />
                )}

                <div
                  className={cn(
                    'workflow-card group relative h-full overflow-hidden rounded-2xl border p-4 sm:p-5',
                    step.variant,
                    step.n === 1 ? 'bg-accent-soft' : 'bg-surface',
                  )}
                  style={{ '--wire-phase': CARD_PHASE[index] } as React.CSSProperties}
                >
                  {/* Layer 1: oversized watermark, cropped by the card and behind everything. */}
                  <span className="workflow-watermark" aria-hidden="true">
                    <step.watermark />
                  </span>

                  {/* Layer 2: the wire. */}
                  <CardWire />

                  {/* Layer 3: content, which never changes. */}
                  <div className="relative">
                    <div className="flex items-center gap-2.5">
                      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accent font-mono text-[11px] font-bold text-on-accent">
                        {step.n}
                      </span>
                      <span className="workflow-stage-icon flex h-7 w-7 items-center justify-center text-content-secondary">
                        <step.icon className="h-3.5 w-3.5" strokeWidth={2} aria-hidden="true" />
                      </span>
                    </div>

                    <h3 className="mt-4 text-[14.5px] font-semibold tracking-[-0.01em] text-content-primary">
                      {step.title}
                    </h3>
                    <p className="mt-2 text-[12.5px] leading-[1.6] text-content-secondary">
                      {step.body}
                    </p>
                  </div>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  );
}
