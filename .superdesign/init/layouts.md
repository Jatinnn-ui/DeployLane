# Shared layouts

## `frontend/src/features/landing/components/LandingNav.tsx`

```tsx
import { ArrowRight, Menu, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { Wordmark } from './Wordmark';

const LINKS = [
  { label: 'Product', href: '#product' },
  { label: 'Features', href: '#features' },
  { label: 'Pricing', href: '#pricing' },
  { label: 'Docs', href: '#docs' },
  { label: 'Changelog', href: '#changelog' },
  { label: 'Enterprise', href: '#enterprise' },
];

/**
 * Marketing header.
 *
 * Transparent over the hero and solid once scrolled, so the 3D scene is never cropped by a
 * bar at the top of the fold but the nav stays readable over content further down.
 */
export function LandingNav() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    function handleScroll() {
      setScrolled(window.scrollY > 12);
    }

    handleScroll();
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Close the mobile sheet once the viewport is wide enough to show the inline nav.
  useEffect(() => {
    if (!menuOpen) return;

    function handleResize() {
      if (window.innerWidth >= 1024) setMenuOpen(false);
    }

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [menuOpen]);

  return (
    <header
      className={cn(
        'sticky top-0 z-50 transition-colors duration-300',
        scrolled || menuOpen
          ? 'border-b border-border-subtle bg-canvas/85 backdrop-blur-xl'
          : 'border-b border-transparent',
      )}
    >
      {/* Reference navbar is 58px at 1024px wide (5.66vw), and stays secondary to the hero. */}
      <div className="landing-container flex h-[clamp(56px,5.66vw,90px)] items-center gap-6 text-[length:var(--dl-nav-link)]">
        <Link
          to="/"
          className="flex shrink-0 items-center rounded-md focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent"
        >
          <Wordmark textClass="text-[length:var(--dl-nav-logo)]" />
        </Link>

        <nav
          aria-label="Main"
          className="hidden lg:ml-[4.4vw] lg:flex lg:items-center lg:gap-[2.6em]"
        >
          {LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="font-normal text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              {link.label}
            </a>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-[1.4em]">
          {/* Star count is the developer-tool equivalent of a trust badge, so it sits inline
              with the nav rather than being buried in the footer. */}
          <a
            href="https://github.com/Jatinnn-ui/DeployLane"
            target="_blank"
            rel="noreferrer noopener"
            className="hidden items-center gap-[0.6em] font-normal text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent md:flex"
          >
            <GithubIcon className="h-[1.4em] w-[1.4em]" />
            Star on GitHub
            <span className="rounded-[0.3em] border border-border-subtle bg-surface px-[0.6em] py-[0.15em] font-mono text-[0.9em] text-content-primary">
              8.4k
            </span>
          </a>

          {/* Vertical rule separating the repository link from account actions. */}
          <span className="hidden h-[1.4em] w-px bg-border-subtle md:block" aria-hidden="true" />

          <Link to="/login" className="hidden sm:block">
            <span className="font-normal text-content-secondary transition-colors hover:text-content-primary">
              Sign in
            </span>
          </Link>

          <Link to="/login">
            <Button
              variant="primary"
              size="sm"
              className="group h-[3.2em] gap-[0.8em] rounded-[0.4em] px-[1.6em] text-[1em] font-medium"
            >
              Start Deploying
              <ArrowRight
                className="h-[1.2em] w-[1.2em] transition-transform duration-200 group-hover:translate-x-0.5"
                aria-hidden="true"
              />
            </Button>
          </Link>

          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-controls="landing-mobile-nav"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            className="flex h-9 w-9 items-center justify-center rounded-full border border-border-subtle text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent lg:hidden"
          >
            {menuOpen ? (
              <X className="h-4 w-4" aria-hidden="true" />
            ) : (
              <Menu className="h-4 w-4" aria-hidden="true" />
            )}
          </button>
        </div>
      </div>

      {menuOpen && (
        <nav
          id="landing-mobile-nav"
          aria-label="Mobile"
          className="landing-container border-t border-border-subtle pb-4 pt-2 lg:hidden"
        >
          <ul className="flex flex-col gap-1">
            {LINKS.map((link) => (
              <li key={link.href}>
                <a
                  href={link.href}
                  onClick={() => setMenuOpen(false)}
                  className="block rounded-lg px-2 py-2.5 text-[14px] font-medium text-content-secondary transition-colors hover:bg-surface-hover hover:text-content-primary"
                >
                  {link.label}
                </a>
              </li>
            ))}
            <li className="sm:hidden">
              <Link
                to="/login"
                className="block rounded-lg px-2 py-2.5 text-[14px] font-medium text-content-secondary transition-colors hover:bg-surface-hover hover:text-content-primary"
              >
                Sign in
              </Link>
            </li>
          </ul>
        </nav>
      )}
    </header>
  );
}
```

## `frontend/src/features/landing/components/LandingFooter.tsx`

```tsx
import { ArrowRight } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { LinkedinIcon } from '@/components/icons/LinkedinIcon';
import { DiscordIcon, XIcon } from '@/components/icons/SocialIcons';
import type { IconComponent } from './icon-type';
import { Wordmark } from './Wordmark';

const COLUMNS: Array<{ heading: string; links: Array<{ label: string; href: string }> }> = [
  {
    heading: 'Product',
    links: [
      { label: 'Features', href: '#features' },
      { label: 'Pricing', href: '#pricing' },
      { label: 'Changelog', href: '#changelog' },
      { label: 'Roadmap', href: '#roadmap' },
    ],
  },
  {
    heading: 'Resources',
    links: [
      { label: 'Documentation', href: '#docs' },
      { label: 'Guides', href: '#guides' },
      { label: 'API Reference', href: '#api' },
      { label: 'Blog', href: '#blog' },
    ],
  },
  {
    heading: 'Company',
    links: [
      { label: 'About', href: '#about' },
      { label: 'Careers', href: '#careers' },
      { label: 'Privacy', href: '#privacy' },
      { label: 'Contact', href: 'mailto:hello@deploylane.online' },
    ],
  },
];

const SOCIALS: Array<{ label: string; href: string; icon: IconComponent }> = [
  { label: 'GitHub', href: 'https://github.com/Jatinnn-ui/DeployLane', icon: GithubIcon },
  { label: 'X', href: 'https://x.com', icon: XIcon },
  { label: 'Discord', href: 'https://discord.com', icon: DiscordIcon },
  { label: 'LinkedIn', href: 'https://www.linkedin.com', icon: LinkedinIcon },
];

const LEGAL = [
  { label: 'Terms', href: '#terms' },
  { label: 'Privacy', href: '#privacy' },
  { label: 'Security', href: '#security' },
  { label: 'Status', href: '#status' },
];

/**
 * Newsletter capture.
 *
 * There is no subscription endpoint on the backend yet, so rather than accept an address and
 * silently drop it, submitting composes a mail to the project inbox. Swap the handler for a
 * POST once a real list exists.
 */
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
    <form onSubmit={handleSubmit}>
      <h2 className="text-[11.5px] font-semibold uppercase tracking-[0.09em] text-content-primary">
        Get updates
      </h2>
      <label htmlFor="newsletter-email" className="mt-2 block text-[12.5px] text-content-secondary">
        Subscribe to our newsletter
      </label>

      <div className="mt-3 flex items-center gap-2 rounded-full border border-border-subtle bg-surface p-1 pl-3.5 focus-within:border-border-strong">
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
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent text-on-accent transition-colors hover:bg-accent-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
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
        <div className="landing-panel px-5 py-8 sm:px-7">
          <div className="grid gap-9 sm:grid-cols-2 lg:grid-cols-[minmax(0,1.25fr)_repeat(3,minmax(0,0.75fr))_minmax(0,1.2fr)]">
            <div>
              <Wordmark />
              <p className="mt-3.5 max-w-[230px] text-[12.5px] leading-[1.6] text-content-secondary">
                The modern deployment platform for developers and teams.
              </p>

              <ul className="mt-5 flex items-center gap-2">
                {SOCIALS.map((social) => (
                  <li key={social.label}>
                    <a
                      href={social.href}
                      target={social.href.startsWith('http') ? '_blank' : undefined}
                      rel={social.href.startsWith('http') ? 'noreferrer noopener' : undefined}
                      aria-label={social.label}
                      className="flex h-8 w-8 items-center justify-center rounded-lg border border-border-subtle bg-surface text-content-secondary transition-colors hover:border-border-strong hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                    >
                      <social.icon className="h-3.5 w-3.5" aria-hidden="true" />
                    </a>
                  </li>
                ))}
              </ul>
            </div>

            {COLUMNS.map((column) => (
              <div key={column.heading}>
                <h2 className="text-[11.5px] font-semibold uppercase tracking-[0.09em] text-content-primary">
                  {column.heading}
                </h2>
                <ul className="mt-4 space-y-2.5">
                  {column.links.map((link) => (
                    <li key={`${column.heading}-${link.label}`}>
                      <a
                        href={link.href}
                        className="text-[12.5px] text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                      >
                        {link.label}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}

            <NewsletterForm />
          </div>

          <div className="mt-10 flex flex-col gap-3 border-t border-border-subtle pt-5 sm:flex-row sm:items-center">
            <p className="text-[11.5px] text-content-muted">
              © 2024 DeployLane. All rights reserved.
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

            <Link
              to="/login"
              className="text-[11.5px] text-content-muted transition-colors hover:text-content-secondary sm:hidden"
            >
              Sign in
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
```

