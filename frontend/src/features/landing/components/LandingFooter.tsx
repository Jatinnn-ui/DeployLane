import { ArrowRight, ArrowUpRight } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { LinkedinIcon } from '@/components/icons/LinkedinIcon';
import { DiscordIcon, XIcon } from '@/components/icons/SocialIcons';
import { Button } from '@/components/ui/button';
import type { IconComponent } from './icon-type';
import { Wordmark } from './Wordmark';

const COLUMNS: Array<{ heading: string; links: Array<{ label: string; href: string; to?: string }> }> = [
  {
    heading: 'Product',
    links: [
      { label: 'Features', href: '/#features' },
      { label: 'How it works', href: '/#product' },
      { label: 'Pricing', href: '/pricing', to: '/pricing' },
      { label: 'Changelog', href: '#changelog' },
    ],
  },
  {
    heading: 'Resources',
    links: [
      { label: 'Documentation', href: 'https://github.com/Jatinnn-ui/DeployLane#readme' },
      { label: 'GitHub', href: 'https://github.com/Jatinnn-ui/DeployLane' },
      { label: 'API Reference', href: '#api' },
      { label: 'Status', href: '#status' },
    ],
  },
  {
    heading: 'Company',
    links: [
      { label: 'About', href: '#about' },
      { label: 'Blog', href: '#blog' },
      { label: 'Privacy', href: '#privacy' },
      { label: 'Contact', href: 'mailto:hello@deploylane.online' },
    ],
  },
];

const SOCIALS: Array<{ label: string; href: string; icon: IconComponent }> = [
  { label: 'GitHub', href: 'https://github.com/Jatinnn-ui', icon: GithubIcon },
  { label: 'X', href: 'https://x.com', icon: XIcon },
  { label: 'Discord', href: 'https://discord.com', icon: DiscordIcon },
  { label: 'LinkedIn', href: 'https://www.linkedin.com', icon: LinkedinIcon },
];

const LEGAL = [
  { label: 'Terms', href: '#terms' },
  { label: 'Privacy', href: '#privacy' },
  { label: 'Security', href: '#security' },
];

/** Newsletter capture — composes a mail since there's no list endpoint yet. */
function NewsletterForm() {
  const [email, setEmail] = useState('');

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!email) return;
    const subject = encodeURIComponent('Subscribe to DeployLane updates');
    const body = encodeURIComponent(`Please add ${email} to the DeployLane updates list.`);
    window.location.href = `mailto:hello@deploylane.online?subject=${subject}&body=${body}`;
  }

  return (
    <form onSubmit={handleSubmit} className="w-full">
      <div className="flex items-center gap-2 rounded-full border border-border-subtle bg-surface p-1 pl-4 focus-within:border-accent-border">
        <input
          id="newsletter-email"
          type="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="you@example.com"
          className="min-w-0 flex-1 bg-transparent py-1.5 text-[12.5px] text-content-primary placeholder:text-content-muted focus:outline-none"
        />
        <button
          type="submit"
          aria-label="Subscribe"
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-accent text-on-accent transition-colors hover:bg-accent-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        >
          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      </div>
    </form>
  );
}

export function LandingFooter() {
  return (
    <footer className="pb-5">
      <div className="landing-container">
        <div className="landing-panel relative overflow-hidden px-6 pb-8 pt-10 sm:px-9 sm:pt-12">
          {/* Ambient glow anchored bottom-left, so the brand block feels lit. */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -bottom-24 -left-24 h-72 w-72 rounded-full"
            style={{ background: 'radial-gradient(circle, rgba(168,240,0,0.08), transparent 70%)' }}
          />

          {/* ══════════════ CTA BAND ══════════════ */}
          <div className="relative flex flex-col gap-5 border-b border-border-subtle pb-9 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="max-w-[14em] text-[24px] font-bold leading-[1.14] tracking-[-0.03em] text-content-primary sm:text-[30px]">
                Ready to ship? <span className="text-accent">Deploy in seconds.</span>
              </h2>
              <p className="mt-2.5 max-w-[30em] text-[13px] leading-[1.6] text-content-secondary">
                Connect a GitHub repo and watch your first deployment go live. Free while in beta.
              </p>
            </div>
            <div className="flex shrink-0 flex-wrap items-center gap-3">
              <Link to="/login">
                <Button
                  variant="primary"
                  size="md"
                  className="group h-11 gap-2.5 rounded-[9px] px-6 text-[14px] font-semibold"
                >
                  Start Deploying
                  <ArrowRight
                    className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                    aria-hidden="true"
                  />
                </Button>
              </Link>
              <a href="https://github.com/Jatinnn-ui/DeployLane" target="_blank" rel="noreferrer noopener">
                <Button variant="secondary" size="md" className="h-11 gap-2 rounded-[9px] px-5 text-[14px] font-medium">
                  <GithubIcon className="h-4 w-4" aria-hidden="true" />
                  Star on GitHub
                </Button>
              </a>
            </div>
          </div>

          {/* ══════════════ MAIN GRID ══════════════ */}
          <div className="relative mt-10 grid gap-9 sm:grid-cols-2 lg:grid-cols-[minmax(0,1.4fr)_repeat(3,minmax(0,0.7fr))]">
            {/* Brand block */}
            <div>
              <Wordmark />
              <p className="mt-4 max-w-[260px] text-[12.5px] leading-[1.65] text-content-secondary">
                The deployment platform that turns a git push into a live URL — built for developers
                who'd rather ship than configure.
              </p>

              {/* Newsletter */}
              <div className="mt-5 max-w-[300px]">
                <p className="mb-2 text-[10.5px] font-semibold uppercase tracking-[0.1em] text-content-muted">
                  Get product updates
                </p>
                <NewsletterForm />
              </div>

              {/* Socials */}
              <ul className="mt-5 flex items-center gap-2">
                {SOCIALS.map((social) => (
                  <li key={social.label}>
                    <a
                      href={social.href}
                      target={social.href.startsWith('http') ? '_blank' : undefined}
                      rel={social.href.startsWith('http') ? 'noreferrer noopener' : undefined}
                      aria-label={social.label}
                      className="flex h-8 w-8 items-center justify-center rounded-lg border border-border-subtle bg-surface text-content-secondary transition-colors hover:border-accent-border hover:text-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                    >
                      <social.icon className="h-3.5 w-3.5" aria-hidden="true" />
                    </a>
                  </li>
                ))}
              </ul>
            </div>

            {/* Link columns */}
            {COLUMNS.map((column) => (
              <div key={column.heading}>
                <h3 className="text-[11px] font-semibold uppercase tracking-[0.1em] text-content-primary">
                  {column.heading}
                </h3>
                <ul className="mt-4 space-y-2.5">
                  {column.links.map((link) => {
                    const external = link.href.startsWith('http') || link.href.startsWith('mailto:');
                    const cls =
                      'group inline-flex items-center gap-1 text-[12.5px] text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent';
                    return (
                      <li key={`${column.heading}-${link.label}`}>
                        {link.to ? (
                          <Link to={link.to} className={cls}>
                            {link.label}
                          </Link>
                        ) : (
                          <a
                            href={link.href}
                            className={cls}
                            {...(external ? { target: '_blank', rel: 'noreferrer noopener' } : {})}
                          >
                            {link.label}
                            {external && (
                              <ArrowUpRight
                                className="h-3 w-3 opacity-0 transition-opacity group-hover:opacity-60"
                                aria-hidden="true"
                              />
                            )}
                          </a>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))}
          </div>

          {/* ══════════════ BOTTOM BAR ══════════════ */}
          <div className="relative mt-10 flex flex-col gap-3 border-t border-border-subtle pt-5 sm:flex-row sm:items-center">
            <p className="text-[11.5px] text-content-muted">
              © {new Date().getFullYear()} DeployLane. Built for developers.
            </p>

            <ul className="flex flex-wrap items-center gap-x-5 gap-y-2 sm:ml-auto">
              {LEGAL.map((item) => (
                <li key={item.label}>
                  <a
                    href={item.href}
                    className="text-[11.5px] text-content-muted transition-colors hover:text-content-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                  >
                    {item.label}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </footer>
  );
}
