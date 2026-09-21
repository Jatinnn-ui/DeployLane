import { ArrowRight, Menu, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { Wordmark } from './Wordmark';

/**
 * Nav links. `kind` decides how each is rendered:
 *   'anchor' — same-page scroll to a section that actually exists on the landing page
 *   'route'  — client-side navigation to a real page
 *   'external' — opens in a new tab
 * Only links that lead somewhere real are listed, so nothing in the header is a dead end.
 */
const LINKS: Array<{ label: string; href: string; kind: 'anchor' | 'route' | 'external' }> = [
  { label: 'How it Works', href: '/#product', kind: 'anchor' },
  { label: 'Features', href: '/#features', kind: 'anchor' },
  { label: 'Pricing', href: '/pricing', kind: 'route' },
  { label: 'Docs', href: 'https://github.com/Jatinnn-ui/DeployLane#readme', kind: 'external' },
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
          {LINKS.map((link) => {
            const cls =
              'font-normal text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent';
            if (link.kind === 'route') {
              return (
                <Link key={link.href} to={link.href} className={cls}>
                  {link.label}
                </Link>
              );
            }
            return (
              <a
                key={link.href}
                href={link.href}
                className={cls}
                {...(link.kind === 'external'
                  ? { target: '_blank', rel: 'noreferrer noopener' }
                  : {})}
              >
                {link.label}
              </a>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-[1.4em]">
          {/* Repository action stays visible without presenting an unverified star count. */}
          <a
            href="https://github.com/Jatinnn-ui"
            target="_blank"
            rel="noreferrer noopener"
            className="hidden items-center gap-[0.6em] font-normal text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent md:flex"
          >
            <GithubIcon className="h-[1.4em] w-[1.4em]" />
            Star on GitHub
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
            {LINKS.map((link) => {
              const cls =
                'block rounded-lg px-2 py-2.5 text-[14px] font-medium text-content-secondary transition-colors hover:bg-surface-hover hover:text-content-primary';
              return (
                <li key={link.href}>
                  {link.kind === 'route' ? (
                    <Link to={link.href} onClick={() => setMenuOpen(false)} className={cls}>
                      {link.label}
                    </Link>
                  ) : (
                    <a
                      href={link.href}
                      onClick={() => setMenuOpen(false)}
                      className={cls}
                      {...(link.kind === 'external'
                        ? { target: '_blank', rel: 'noreferrer noopener' }
                        : {})}
                    >
                      {link.label}
                    </a>
                  )}
                </li>
              );
            })}
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
