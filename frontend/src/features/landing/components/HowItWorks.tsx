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

const STEPS: Array<{
  n: number;
  title: string;
  body: string;
  icon: IconComponent;
  watermark: (props: { className?: string }) => React.JSX.Element;
}> = [
  {
    n: 1,
    title: 'Connect Repository',
    body: 'Link your Git repository and choose a branch to deploy.',
    icon: GithubIcon,
    watermark: RepositoryWatermark,
  },
  {
    n: 2,
    title: 'Build & Test',
    body: 'We install dependencies, run tests, and build your application.',
    icon: Zap,
    watermark: BuildWatermark,
  },
  {
    n: 3,
    title: 'Deploy',
    body: 'Your application is deployed to our global infrastructure automatically.',
    icon: Rocket,
    watermark: DeployWatermark,
  },
  {
    n: 4,
    title: 'Go Live',
    body: 'Get a live URL instantly with global CDN and high availability.',
    icon: Globe,
    watermark: LiveWatermark,
  },
];

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

          <ol className="mt-7 grid gap-4 sm:grid-cols-2 md:grid-cols-4 md:gap-5 lg:gap-5">
            {STEPS.map((step, index) => (
              <li key={step.n} className="relative">
                {/* Live wiring bridging one card to the next. Desktop only, suppressed on the last
                    card so it never points into empty space. */}
                {index < STEPS.length - 1 && (
                  <span
                    aria-hidden="true"
                    className="step-connector absolute -right-[22px] top-1/2 z-20 hidden h-0.5 w-5 -translate-y-1/2 md:block"
                  />
                )}

                <div
                  className={cn(
                    'workflow-card group relative h-full min-h-[160px] overflow-hidden rounded-2xl p-4 sm:p-5',
                  )}
                >
                  {/* Watermark — absolutely positioned behind content, clipped by overflow:hidden */}
                  <span className="workflow-watermark" aria-hidden="true">
                    <step.watermark />
                  </span>

                  {/* Content */}
                  <div className="relative z-10">
                    <div className="flex items-center gap-2.5">
                      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accent font-mono text-[11px] font-bold text-on-accent">
                        {step.n}
                      </span>
                      <span className="flex h-7 w-7 items-center justify-center text-accent/70">
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
