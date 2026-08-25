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
