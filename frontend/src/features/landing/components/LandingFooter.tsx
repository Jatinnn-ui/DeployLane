import { Link } from 'react-router-dom';

const COLUMNS: Array<{ heading: string; links: Array<{ label: string; href: string }> }> = [
  {
    heading: 'Product',
    links: [
      { label: 'How it works', href: '#how-it-works' },
      { label: 'Features', href: '#features' },
      { label: 'Runtimes', href: '#platform' },
    ],
  },
  {
    heading: 'Platform',
    links: [
      { label: 'Build logs', href: '#features' },
      { label: 'Monitoring', href: '#features' },
      { label: 'AI analysis', href: '#features' },
    ],
  },
  {
    heading: 'Account',
    links: [
      { label: 'Sign in', href: '/login' },
      { label: 'Dashboard', href: '/dashboard' },
    ],
  },
];

export function LandingFooter() {
  return (
    <footer className="border-t border-border-subtle bg-canvas-secondary py-14">
      <div className="landing-container">
        <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-[minmax(0,1.4fr)_repeat(3,minmax(0,1fr))]">
          <div className="max-w-[300px]">
            <img src="/brand/deploylane-logo.png" alt="DeployLane" className="h-7 w-auto" />
            <p className="mt-4 text-[14px] leading-[1.6] text-content-secondary">
              Git-driven deployments, containers and monitoring for developers who would rather ship
              than configure.
            </p>
          </div>

          {COLUMNS.map((column) => (
            <div key={column.heading}>
              <h2 className="text-[11.5px] font-semibold uppercase tracking-[0.09em] text-content-muted">
                {column.heading}
              </h2>
              <ul className="mt-4 space-y-2.5">
                {column.links.map((link) => (
                  <li key={`${column.heading}-${link.label}`}>
                    {link.href.startsWith('/') ? (
                      <Link
                        to={link.href}
                        className="text-[14px] text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                      >
                        {link.label}
                      </Link>
                    ) : (
                      <a
                        href={link.href}
                        className="text-[14px] text-content-secondary transition-colors hover:text-content-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                      >
                        {link.label}
                      </a>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-12 flex flex-col gap-3 border-t border-border-subtle pt-6 sm:flex-row sm:items-center">
          <p className="font-mono text-[11.5px] text-content-muted">
            © {new Date().getFullYear()} DeployLane
          </p>
          <p className="font-mono text-[11.5px] text-content-muted sm:ml-auto">
            Built on Docker and Traefik
          </p>
        </div>
      </div>
    </footer>
  );
}
