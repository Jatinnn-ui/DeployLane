import { ArrowLeft } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { BrandLogo } from '@/components/BrandLogo';
import { useAuthStore } from '@/stores/auth-store';

/**
 * 404 catch-all page.
 *
 * Shown for unknown routes when the user is authenticated (unauthed users still land on the login page
 * via RequireAuth). The copy stays understated and the single action takes them back to a known surface.
 */
export function NotFoundPage() {
  const location = useLocation();
  const status = useAuthStore((state) => state.status);
  const homeLink = status === 'authenticated' ? '/dashboard' : '/';

  return (
    <div className="flex min-h-[70vh] flex-col items-center justify-center px-6 text-center">
      <BrandLogo className="h-9 max-w-[172px]" />

      <p className="mt-8 font-mono text-[52px] font-bold leading-none tracking-tight text-accent">
        404
      </p>
      <h1 className="mt-3 text-2xl font-semibold tracking-tight text-content-primary">
        Page not found
      </h1>
      <p className="mt-2 max-w-md text-[14px] leading-relaxed text-content-secondary">
        There is nothing at{' '}
        <code className="rounded-xs border border-border-subtle bg-surface px-1.5 py-0.5 font-mono text-[12px] text-content-primary">
          {location.pathname}
        </code>
        . It may have been moved or deleted.
      </p>

      <Link to={homeLink} className="mt-8">
        <Button variant="primary" size="md">
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          Back to {status === 'authenticated' ? 'dashboard' : 'home'}
        </Button>
      </Link>
    </div>
  );
}
