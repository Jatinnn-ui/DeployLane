const STEPS = [
  {
    n: '01',
    title: 'Connect GitHub',
    body: 'Sign in with GitHub OAuth. DeployLane reads only the repositories you grant it.',
  },
  {
    n: '02',
    title: 'Import a repository',
    body: 'Pick a repo and branch. The buildpack, install command and start command are detected for you.',
  },
  {
    n: '03',
    title: 'Build and containerise',
    body: 'Every push builds into an immutable image, with the full build log streaming as it runs.',
  },
  {
    n: '04',
    title: 'Live on your URL',
    body: 'Traefik routes traffic to the new container, health-checks it, then retires the old one.',
  },
];

export function HowItWorks() {
  return (
    <section id="how-it-works" className="py-20 lg:py-28" aria-labelledby="how-it-works-heading">
      <div className="landing-container">
        <div className="max-w-[620px]">
          <p className="text-[12px] font-semibold uppercase tracking-[0.1em] text-accent">
            How it works
          </p>
          <h2
            id="how-it-works-heading"
            className="mt-4 text-[28px] font-bold leading-[1.12] tracking-[-0.025em] text-content-primary sm:text-[36px] lg:text-[42px]"
          >
            Four steps from repository to production.
          </h2>
          <p className="mt-4 text-[16px] leading-[1.6] text-content-secondary">
            No YAML to write, no runners to configure. The pipeline is the product.
          </p>
        </div>

        <ol className="mt-14 grid gap-x-6 gap-y-10 sm:grid-cols-2 lg:grid-cols-4">
          {STEPS.map((step, index) => (
            <li key={step.n} className="relative">
              {/* Dotted rail toward the next step. Suppressed on the last card and wherever
                  the grid wraps, so it never points into empty space. */}
              {index < STEPS.length - 1 && (
                <span
                  aria-hidden="true"
                  className="step-connector absolute left-[52px] top-[17px] hidden h-px lg:block"
                  style={{ right: '-24px' }}
                />
              )}

              <div className="flex items-center gap-3">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-accent-border bg-accent-soft font-mono text-[12.5px] font-semibold text-accent">
                  {step.n}
                </span>
              </div>

              <h3 className="mt-5 text-[17px] font-semibold tracking-[-0.01em] text-content-primary">
                {step.title}
              </h3>
              <p className="mt-2.5 text-[14.5px] leading-[1.62] text-content-secondary">
                {step.body}
              </p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
