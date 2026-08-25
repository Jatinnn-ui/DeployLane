import { ArrowRight, Globe, ShieldCheck, Zap, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { ServerIllustration } from './ServerIllustration';

const PILLARS: Array<{ title: string; body: string; icon: LucideIcon }> = [
  {
    title: 'Global Infrastructure',
    body: 'Deploy to data centers around the world with 99.99% uptime.',
    icon: Globe,
  },
  {
    title: 'Speed & Performance',
    body: 'Optimized builds and edge delivery for the fastest experience.',
    icon: Zap,
  },
  {
    title: 'Security First',
    body: 'Automatic HTTPS, isolated environments, and secure by default.',
    icon: ShieldCheck,
  },
];

export function BuiltForDevelopers() {
  return (
    <section id="built-for-developers" className="pb-4" aria-labelledby="pillars-heading">
      <div className="landing-container">
        <div className="landing-panel grid gap-8 px-5 py-7 sm:px-7 sm:py-8 lg:grid-cols-[minmax(0,0.82fr)_minmax(0,1.18fr)] lg:gap-9">
          {/* Same illustration as the hero, framed a little wider for this panel. */}
          <div className="relative flex items-center justify-center">
            <div
              aria-hidden="true"
              className="pointer-events-none absolute left-1/2 top-1/2 h-[340px] w-[340px] -translate-x-1/2 -translate-y-1/2 rounded-full"
              style={{
                background: 'radial-gradient(circle, rgba(160,255,0,0.08), transparent 55%)',
              }}
            />
            <ServerIllustration className="relative w-full max-w-[430px]" />
          </div>

          <div>
            <p className="text-[9.5px] font-semibold uppercase tracking-[0.14em] text-accent">
              Built for developers
            </p>
            <h2 id="pillars-heading" className="sr-only">
              Built for developers
            </h2>

            <ul className="mt-5 grid gap-3 sm:grid-cols-3">
              {PILLARS.map((pillar) => (
                <li
                  key={pillar.title}
                  className="rounded-2xl border border-border-subtle bg-surface p-4 transition-colors duration-200 hover:border-border-strong"
                >
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

            <div className="mt-9">
              <p className="text-[9.5px] font-semibold uppercase tracking-[0.14em] text-accent">
                Ready to deploy?
              </p>
              <p className="mt-3 text-[22px] font-bold leading-[1.16] tracking-[-0.03em] text-content-primary sm:text-[26px]">
                Ship faster. Scale further.
                <br />
                DeployLane has your back.
              </p>
              <p className="mt-3.5 max-w-[440px] text-[13px] leading-[1.6] text-content-secondary">
                Join thousands of developers building and deploying modern applications with
                confidence.
              </p>

              <div className="mt-6 flex flex-wrap items-center gap-3">
                <Link to="/login">
                  <Button variant="primary" size="md" className="group">
                    Start Deploying Now
                    <ArrowRight
                      className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                      aria-hidden="true"
                    />
                  </Button>
                </Link>

                <a href="mailto:hello@deploylane.online">
                  <Button variant="secondary" size="md">
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
