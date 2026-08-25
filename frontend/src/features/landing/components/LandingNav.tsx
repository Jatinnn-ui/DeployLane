import { Menu, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

const LINKS = [
  { label: 'Platform', href: '#platform' },
  { label: 'How it works', href: '#how-it-works' },
  { label: 'Features', href: '#features' },
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
      if (window.innerWidth >= 768) setMenuOpen(false);
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
      <div className="landing-container flex h-16 items-center gap-6">
        <Link
          to="/"
          className="flex shrink-0 items-center rounded-md focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent"
        >
          <img src="/brand/deploylane-logo.png" alt="DeployLane" className="h-7 w-auto" />
        </Link>

        <nav aria-label="Main" className="hidden md:flex md:items-center md:gap-1">
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

        <div className="ml-auto flex items-center gap-2">
          <Link to="/login" className="hidden sm:block">
            <Button variant="ghost" size="sm">
              Sign in
            </Button>
          </Link>

          <Link to="/login">
            <Button variant="primary" size="sm">
              Start deploying
            </Button>
          </Link>

          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-controls="landing-mobile-nav"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            className="flex h-9 w-9 items-center justify-center rounded-full border border-border-subtle text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent md:hidden"
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
          className="landing-container border-t border-border-subtle pb-4 pt-2 md:hidden"
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
