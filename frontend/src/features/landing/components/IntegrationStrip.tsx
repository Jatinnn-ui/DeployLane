const RUNTIMES = [
  'Node.js',
  'Next.js',
  'Vite',
  'Python',
  'Django',
  'FastAPI',
  'Go',
  'Java',
  'Spring Boot',
  'Rust',
  'Dockerfile',
  'Static',
];

/**
 * Detected-stack strip.
 *
 * Set as type rather than logos on purpose: third-party marks would drag a dozen colours
 * into a palette built on one accent, and this list is about build detection coverage — a
 * monospace chip communicates "runtime we detect" more honestly than a brand logo.
 */
export function IntegrationStrip() {
  return (
    <section id="platform" className="border-y border-border-subtle bg-canvas-secondary py-12">
      <div className="landing-container">
        <p className="text-center text-[12px] font-semibold uppercase tracking-[0.1em] text-content-muted">
          Buildpack detection for the stack you already ship
        </p>

        <ul className="mt-7 flex flex-wrap items-center justify-center gap-x-2.5 gap-y-3">
          {RUNTIMES.map((runtime) => (
            <li
              key={runtime}
              className="rounded-full border border-border-subtle bg-surface px-3.5 py-1.5 font-mono text-[12.5px] text-content-secondary transition-colors duration-200 hover:border-accent-border hover:text-content-primary"
            >
              {runtime}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
