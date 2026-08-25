import { ArrowRight, Menu, Star, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { GithubIcon } from '@/components/icons/GithubIcon';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

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
      <div className="landing-container flex h-16 items-center gap-8">
        <Link
          to="/"
          className="flex shrink-0 items-center rounded-md focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent"
        >
          <img src="/brand/deploylane-logo.png" alt="DeployLane" className="h-6 w-auto" />
        </Link>

        <nav aria-label="Main" className="hidden lg:flex lg:items-center lg:gap-1">
          {LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="rounded-full px-3 py-2 text-[13.5px] font-medium text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              {link.label}
            </a>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-2.5">
          {/* Star count is the developer-tool equivalent of a trust badge, so it gets the
              same visual weight as a nav item rather than being buried in the footer. */}
          <a
            href="https://github.com/Jatinnn-ui/DeployLane"
            target="_blank"
            rel="noreferrer noopener"
            className="hidden items-center gap-2 rounded-full border border-border-subtle bg-surface py-1.5 pl-3 pr-1.5 text-[12.5px] font-medium text-content-secondary transition-colors hover:border-border-strong hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent md:flex"
          >
            <GithubIcon className="h-3.5 w-3.5" />
            Star on GitHub
            <span className="flex items-center gap-1 rounded-full bg-canvas-secondary px-2 py-1 font-mono text-[11px] text-content-primary">
              <Star className="h-2.5 w-2.5 text-accent" aria-hidden="true" />
              8.4k
            </span>
          </a>

          <Link to="/login" className="hidden sm:block">
            <Button variant="ghost" size="sm">
              Sign in
            </Button>
          </Link>

          <Link to="/login">
            <Button variant="primary" size="sm" className="group">
              Start Deploying
              <ArrowRight
                className="h-3.5 w-3.5 transition-transform duration-200 group-hover:translate-x-0.5"
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
