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

          <ol className="mt-7 grid gap-4 sm:grid-cols-2 lg:grid-cols-4 lg:gap-5">
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
                    'group h-full rounded-2xl border p-4 transition-[background-color,border-color,box-shadow,transform] duration-300 hover:-translate-y-1 hover:border-accent hover:bg-accent hover:shadow-[0_16px_35px_rgba(168,240,0,0.14)] sm:p-5',
                    step.n === 1 && 'workflow-step-card workflow-step-card-repository',
                    step.n === 2 && 'workflow-step-card workflow-build-card',
                    step.n === 3 && 'workflow-step-card workflow-step-card-deploy',
                    step.n === 4 && 'workflow-step-card workflow-step-card-live',
                    step.n === 1
                      ? 'border-accent-border bg-accent-soft'
                      : 'border-border-subtle bg-surface',
                  )}
                >
                  <div className="flex items-center gap-2.5">
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accent font-mono text-[11px] font-bold text-on-accent transition-colors duration-300 group-hover:bg-on-accent group-hover:text-accent">
                      {step.n}
                    </span>
                    <span className="workflow-stage-icon flex h-7 w-7 items-center justify-center text-content-secondary transition-colors duration-300 group-hover:text-on-accent">
                      <step.icon className="h-3.5 w-3.5" strokeWidth={2} aria-hidden="true" />
                    </span>
                  </div>

                  <h3 className="mt-4 text-[14.5px] font-semibold tracking-[-0.01em] text-content-primary transition-colors duration-300 group-hover:text-on-accent">
                    {step.title}
                  </h3>
                  <p className="mt-2 text-[12.5px] leading-[1.6] text-content-secondary transition-colors duration-300 group-hover:text-black/70">
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
