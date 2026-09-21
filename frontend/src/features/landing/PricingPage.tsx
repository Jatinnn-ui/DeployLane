import { useEffect } from 'react';
import {
  ArrowRight,
  Building2,
  Check,
  Rocket,
  Sparkles,
  type LucideIcon,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { LandingFooter } from './components/LandingFooter';
import { LandingNav } from './components/LandingNav';
import { Wordmark } from './components/Wordmark';

/* ─────────────────────────────────────────────────────────────
   Pricing page.

   DeployLane is free during the beta. Rather than invent dollar
   figures, every plan shows "Free" today, with the future paid
   intent noted as "then …" so the value ladder is still clear.
   ───────────────────────────────────────────────────────────── */

interface Plan {
  id: string;
  name: string;
  icon: LucideIcon;
  tagline: string;
  /** Shown large. During beta everything is Free. */
  price: string;
  /** Small note under the price, e.g. future intent. */
  note: string;
  cta: string;
  href: string;
  featured?: boolean;
  features: string[];
}

const PLANS: Plan[] = [
  {
    id: 'hobby',
    name: 'Hobby',
    icon: Sparkles,
    tagline: 'For side projects, prototypes and learning.',
    price: 'Free',
    note: 'Free forever',
    cta: 'Start building',
    href: '/login',
    features: [
      '3 projects',
      'Deploy from GitHub',
      'Automatic Docker builds',
      'Live build logs',
      'deploylane.app subdomain',
      'Community support',
    ],
  },
  {
    id: 'pro',
    name: 'Pro',
    icon: Rocket,
    tagline: 'For developers shipping real products.',
    price: 'Free',
    note: 'Free during beta',
    cta: 'Get early access',
    href: '/login',
    featured: true,
    features: [
      'Unlimited projects',
      'Auto-deploy on git push',
      'AI failure analysis',
      'Zero-downtime deploys',
      'Custom domains + HTTPS',
      'Priority build queue',
      'Email support',
    ],
  },
  {
    id: 'team',
    name: 'Team',
    icon: Building2,
    tagline: 'For teams that ship together.',
    price: 'Free',
    note: 'Free during beta',
    cta: 'Talk to us',
    href: 'mailto:hello@deploylane.online',
    features: [
      'Everything in Pro',
      'Team workspaces & roles',
      'Preview deployments',
      'Deployment approvals',
      'Audit log & activity feed',
      'Dedicated support',
    ],
  },
];

const COMPARISON: Array<{ feature: string; hobby: boolean | string; pro: boolean | string; team: boolean | string }> = [
  { feature: 'Projects', hobby: '3', pro: 'Unlimited', team: 'Unlimited' },
  { feature: 'Deploy from GitHub', hobby: true, pro: true, team: true },
  { feature: 'Docker builds & live logs', hobby: true, pro: true, team: true },
  { feature: 'Auto-deploy on push', hobby: false, pro: true, team: true },
  { feature: 'AI failure analysis', hobby: false, pro: true, team: true },
  { feature: 'Custom domains + HTTPS', hobby: false, pro: true, team: true },
  { feature: 'Zero-downtime deploys', hobby: false, pro: true, team: true },
  { feature: 'Team workspaces & roles', hobby: false, pro: false, team: true },
  { feature: 'Preview deployments', hobby: false, pro: false, team: true },
  { feature: 'Audit log', hobby: false, pro: false, team: true },
];

const FAQ: Array<{ q: string; a: string }> = [
  {
    q: 'Is DeployLane really free right now?',
    a: 'Yes. DeployLane is in beta and every plan is completely free while we build. No credit card, no trial timer — just connect a repo and deploy.',
  },
  {
    q: 'Will you charge later?',
    a: 'Eventually Pro and Team will move to paid plans, but the Hobby tier stays free forever, and beta users get grandfathered pricing when that happens.',
  },
  {
    q: 'What happens when a build fails?',
    a: 'DeployLane captures the logs, analyses the failure, and shows you the root cause with evidence and a suggested fix. On Pro and Team this uses AI analysis.',
  },
  {
    q: 'Can I bring my own domain?',
    a: 'Pro and Team include custom domains with automatic HTTPS. Hobby projects get a deploylane.app subdomain.',
  },
];

/** A single feature-comparison cell. */
function Cell({ value }: { value: boolean | string }) {
  if (value === true)
    return (
      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-accent/15 text-accent">
        <Check className="h-3 w-3" strokeWidth={3} aria-hidden="true" />
      </span>
    );
  if (value === false) return <span className="text-content-muted">—</span>;
  return <span className="text-[12px] font-medium text-content-primary">{value}</span>;
}

export function PricingPage() {
  useEffect(() => {
    const previous = document.title;
    document.title = 'Pricing — DeployLane';
    return () => {
      document.title = previous;
    };
  }, []);

  return (
    <div className="landing-root min-h-screen">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[60] focus:rounded-full focus:bg-accent focus:px-4 focus:py-2 focus:text-[13px] focus:font-semibold focus:text-on-accent"
      >
        Skip to content
      </a>

      <LandingNav />

      <main id="main" className="pb-4">
        {/* ══════════════ HEADER ══════════════ */}
        <section className="relative overflow-hidden">
          {/* Ambient glow */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 top-0 h-[420px]"
            style={{
              background:
                'radial-gradient(ellipse 60% 100% at 50% 0%, rgba(168,240,0,0.08) 0%, transparent 70%)',
            }}
          />
          <div className="landing-container relative pt-14 text-center sm:pt-20">
            <div className="mb-6 flex justify-center">
              <Wordmark textClass="text-[20px]" />
            </div>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-accent-border bg-accent-soft px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-accent">
              <Sparkles className="h-3 w-3" aria-hidden="true" />
              Free while in beta
            </span>

            <h1 className="mx-auto mt-6 max-w-[15em] text-[32px] font-bold leading-[1.08] tracking-[-0.03em] text-content-primary sm:text-[46px]">
              Deploy for free.
              <br />
              <span className="text-accent">Pay nothing today.</span>
            </h1>
            <p className="mx-auto mt-5 max-w-[34em] text-[14px] leading-[1.65] text-content-secondary sm:text-[15px]">
              Every DeployLane plan is free during the beta. Real deployments — clone, build,
              containerize, health-check and publish a live URL — at zero cost while we grow.
            </p>
          </div>
        </section>

        {/* ══════════════ PLAN CARDS ══════════════ */}
        <section className="landing-container relative mt-12">
          <div className="grid items-stretch gap-5 md:grid-cols-3">
            {PLANS.map((plan) => {
              const Icon = plan.icon;
              const isExternal = plan.href.startsWith('mailto:');
              const CtaInner = (
                <Button
                  variant={plan.featured ? 'primary' : 'secondary'}
                  size="md"
                  className="group h-11 w-full justify-center gap-2 rounded-[9px] text-[13.5px] font-semibold"
                >
                  {plan.cta}
                  <ArrowRight
                    className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                    aria-hidden="true"
                  />
                </Button>
              );

              return (
                <div
                  key={plan.id}
                  className={`relative flex flex-col overflow-hidden rounded-2xl border p-6 transition-[transform,box-shadow,border-color] duration-300 ${
                    plan.featured
                      ? 'border-accent bg-[linear-gradient(160deg,rgba(168,240,0,0.09),rgba(168,240,0,0.02))] shadow-[0_24px_60px_rgba(168,240,0,0.12)] md:-translate-y-3'
                      : 'border-border-subtle bg-surface hover:-translate-y-1 hover:border-accent-border'
                  }`}
                >
                  {plan.featured && (
                    <>
                      <span
                        aria-hidden="true"
                        className="pointer-events-none absolute -right-16 -top-16 h-40 w-40 rounded-full"
                        style={{ background: 'radial-gradient(circle, rgba(168,240,0,0.16), transparent 70%)' }}
                      />
                      <span className="absolute right-4 top-4 rounded-full bg-accent px-2.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-on-accent">
                        Most popular
                      </span>
                    </>
                  )}

                  <span
                    className={`flex h-10 w-10 items-center justify-center rounded-xl border ${
                      plan.featured
                        ? 'border-accent bg-accent/15 text-accent'
                        : 'border-accent-border bg-accent-soft text-accent'
                    }`}
                  >
                    <Icon className="h-4.5 w-4.5" strokeWidth={2} aria-hidden="true" />
                  </span>

                  <h2 className="mt-4 text-[17px] font-bold tracking-tight text-content-primary">
                    {plan.name}
                  </h2>
                  <p className="mt-1.5 min-h-[36px] text-[12.5px] leading-[1.5] text-content-secondary">
                    {plan.tagline}
                  </p>

                  <div className="mt-5 flex items-end gap-2">
                    <span className="text-[40px] font-bold leading-none tracking-tight text-content-primary">
                      {plan.price}
                    </span>
                  </div>
                  <p className="mt-1.5 text-[11px] font-medium uppercase tracking-wider text-accent/80">
                    {plan.note}
                  </p>

                  <div className="mt-6">
                    {isExternal ? (
                      <a href={plan.href}>{CtaInner}</a>
                    ) : (
                      <Link to={plan.href}>{CtaInner}</Link>
                    )}
                  </div>

                  <ul className="mt-6 space-y-3 border-t border-border-subtle pt-5">
                    {plan.features.map((feature) => (
                      <li key={feature} className="flex items-start gap-2.5">
                        <span className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-accent/15 text-accent">
                          <Check className="h-2.5 w-2.5" strokeWidth={3} aria-hidden="true" />
                        </span>
                        <span className="text-[12.5px] leading-[1.5] text-content-secondary">
                          {feature}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>
        </section>

        {/* ══════════════ COMPARISON TABLE ══════════════ */}
        <section className="landing-container mt-16">
          <h2 className="text-center text-[22px] font-bold tracking-[-0.02em] text-content-primary sm:text-[26px]">
            Compare every plan
          </h2>

          <div className="mt-8 overflow-hidden rounded-2xl border border-border-subtle bg-surface">
            {/* Header row */}
            <div className="grid grid-cols-[1.6fr_1fr_1fr_1fr] items-center border-b border-border-subtle bg-canvas-secondary px-4 py-3.5 sm:px-6">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-content-muted">
                Feature
              </span>
              {['Hobby', 'Pro', 'Team'].map((name) => (
                <span
                  key={name}
                  className={`text-center text-[12.5px] font-bold ${
                    name === 'Pro' ? 'text-accent' : 'text-content-primary'
                  }`}
                >
                  {name}
                </span>
              ))}
            </div>

            {/* Rows */}
            {COMPARISON.map((row, i) => (
              <div
                key={row.feature}
                className={`grid grid-cols-[1.6fr_1fr_1fr_1fr] items-center px-4 py-3 sm:px-6 ${
                  i % 2 === 1 ? 'bg-canvas-secondary/40' : ''
                }`}
              >
                <span className="text-[12.5px] text-content-secondary">{row.feature}</span>
                <span className="flex justify-center"><Cell value={row.hobby} /></span>
                <span className="flex justify-center"><Cell value={row.pro} /></span>
                <span className="flex justify-center"><Cell value={row.team} /></span>
              </div>
            ))}
          </div>
        </section>

        {/* ══════════════ FAQ ══════════════ */}
        <section className="landing-container mt-16">
          <h2 className="text-center text-[22px] font-bold tracking-[-0.02em] text-content-primary sm:text-[26px]">
            Questions, answered.
          </h2>
          <div className="mx-auto mt-8 grid max-w-[860px] gap-4 sm:grid-cols-2">
            {FAQ.map((item) => (
              <div key={item.q} className="rounded-2xl border border-border-subtle bg-surface p-5">
                <h3 className="text-[13.5px] font-semibold text-content-primary">{item.q}</h3>
                <p className="mt-2 text-[12.5px] leading-[1.6] text-content-secondary">{item.a}</p>
              </div>
            ))}
          </div>
        </section>

        {/* ══════════════ CTA ══════════════ */}
        <section className="landing-container mt-16">
          <div className="landing-panel relative flex flex-col items-center gap-5 overflow-hidden px-6 py-14 text-center">
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-0"
              style={{
                background:
                  'radial-gradient(ellipse 55% 120% at 50% 100%, rgba(168,240,0,0.1) 0%, transparent 70%)',
              }}
            />

            {/* Real DeployLane logo lockup, crowning the final call to action. */}
            <div className="relative">
              <Wordmark textClass="text-[22px]" />
            </div>

            <h2 className="relative max-w-[18em] text-[26px] font-bold leading-[1.14] tracking-[-0.02em] text-content-primary sm:text-[32px]">
              Ship your first deploy in the next 60 seconds.
            </h2>
            <p className="relative max-w-[32em] text-[13.5px] leading-[1.6] text-content-secondary">
              Free while in beta. No credit card. Connect a repository and watch it go live.
            </p>
            <Link to="/login" className="relative">
              <Button
                variant="primary"
                size="lg"
                className="group h-12 gap-2.5 rounded-[9px] px-7 text-[15px] font-semibold"
              >
                Start Deploying — free
                <ArrowRight
                  className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </Button>
            </Link>
          </div>
        </section>
      </main>

      <LandingFooter />
    </div>
  );
}
