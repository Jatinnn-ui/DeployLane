import { Box, Globe, Rocket } from 'lucide-react';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { cn } from '@/lib/utils';
import type { IconComponent } from './icon-type';

const STEPS: Array<{ n: number; title: string; body: string; icon: IconComponent }> = [
  {
    n: 1,
    title: 'Connect Repository',
    body: 'Link your Git repository and choose a branch to deploy.',
    icon: GithubIcon,
  },
  {
    n: 2,
    title: 'Build & Test',
    body: 'We install dependencies, run tests, and build your application.',
    icon: Box,
  },
  {
    n: 3,
    title: 'Deploy',
    body: 'Your application is deployed to our global infrastructure automatically.',
    icon: Rocket,
  },
  {
    n: 4,
    title: 'Go Live',
    body: 'Get a live URL instantly with global CDN and high availability.',
    icon: Globe,
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

          <ol className="mt-9 grid gap-4 sm:grid-cols-2 lg:grid-cols-4 lg:gap-6">
            {STEPS.map((step, index) => (
              <li key={step.n} className="relative">
                {/* Dashed rail toward the next step. Desktop only, and suppressed on the last
                    card, so it never points into empty space. */}
                {index < STEPS.length - 1 && (
                  <span
                    aria-hidden="true"
                    className="step-connector absolute -right-[18px] top-[26px] hidden h-px w-3.5 lg:block"
                  />
                )}

                <div
                  className={cn(
                    'h-full rounded-2xl border p-4 transition-colors duration-200 sm:p-5',
                    step.n === 1
                      ? 'border-accent-border bg-accent-soft'
                      : 'border-border-subtle bg-surface hover:border-border-strong',
                  )}
                >
                  <div className="flex items-center gap-2.5">
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accent font-mono text-[11px] font-bold text-on-accent">
                      {step.n}
                    </span>
                    <span className="flex h-7 w-7 items-center justify-center rounded-lg border border-border-subtle bg-canvas-secondary text-content-secondary">
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
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  );
}
