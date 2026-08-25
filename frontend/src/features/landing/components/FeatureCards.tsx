import {
  KeyRound,
  RotateCcw,
  ScrollText,
  ShieldCheck,
  Sparkles,
  Waves,
  type LucideIcon,
} from 'lucide-react';

interface Feature {
  icon: LucideIcon;
  title: string;
  body: string;
}

const FEATURES: Feature[] = [
  {
    icon: ScrollText,
    title: 'Streaming build logs',
    body: 'Build output arrives over a WebSocket as it happens, colour-coded by source, with the full history kept per deployment.',
  },
  {
    icon: Waves,
    title: 'Zero-downtime rollouts',
    body: 'The new container has to pass its health check before Traefik shifts traffic. The old one is only retired afterwards.',
  },
  {
    icon: KeyRound,
    title: 'Encrypted environment',
    body: 'Variables and secrets are encrypted at rest and injected at runtime, scoped per project and per environment.',
  },
  {
    icon: ShieldCheck,
    title: 'Health monitoring',
    body: 'Uptime, p95 latency, restart counts and container resource use, tracked per deployment rather than per project.',
  },
  {
    icon: Sparkles,
    title: 'AI failure analysis',
    body: 'When a build breaks, the failing log is analysed and you get the probable cause and a concrete fix, not a stack trace.',
  },
  {
    icon: RotateCcw,
    title: 'Instant rollback',
    body: 'Previous images stay on the host, so reverting to the last good deploy is a promotion, not a rebuild.',
  },
];

export function FeatureCards() {
  return (
    <section id="features" className="py-20 lg:py-28" aria-labelledby="features-heading">
      <div className="landing-container">
        <div className="max-w-[620px]">
          <p className="text-[12px] font-semibold uppercase tracking-[0.1em] text-accent">
            Platform
          </p>
          <h2
            id="features-heading"
            className="mt-4 text-[28px] font-bold leading-[1.12] tracking-[-0.025em] text-content-primary sm:text-[36px] lg:text-[42px]"
          >
            The parts you would otherwise wire up yourself.
          </h2>
        </div>

        <ul className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((feature) => (
            <li
              key={feature.title}
              className="group rounded-2xl border border-border-subtle bg-surface p-5 transition-colors duration-200 hover:border-border-strong hover:bg-surface-alt"
            >
              <span className="flex h-9 w-9 items-center justify-center rounded-lg border border-border-subtle bg-canvas-secondary text-content-secondary transition-colors duration-200 group-hover:border-accent-border group-hover:text-accent">
                <feature.icon className="h-4 w-4" strokeWidth={2} aria-hidden="true" />
              </span>

              <h3 className="mt-4 text-[16px] font-semibold tracking-[-0.01em] text-content-primary">
                {feature.title}
              </h3>
              <p className="mt-2 text-[14.5px] leading-[1.62] text-content-secondary">
                {feature.body}
              </p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
