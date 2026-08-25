import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';

export function FinalCta() {
  return (
    <section className="pb-24 lg:pb-32" aria-labelledby="final-cta-heading">
      <div className="landing-container">
        <div className="relative overflow-hidden rounded-3xl border border-border-subtle bg-surface px-6 py-16 text-center sm:px-12 lg:py-20">
          <div
            aria-hidden="true"
            className="hero-grid pointer-events-none absolute inset-0 opacity-50 [mask-image:radial-gradient(ellipse_60%_70%_at_50%_0%,#000_0%,transparent_100%)]"
          />
          <div
            aria-hidden="true"
            className="hero-glow pointer-events-none absolute left-1/2 top-[-40%] h-[560px] w-[560px] -translate-x-1/2 rounded-full"
          />

          <div className="relative">
            <h2
              id="final-cta-heading"
              className="mx-auto max-w-[620px] text-[30px] font-bold leading-[1.1] tracking-[-0.03em] text-content-primary sm:text-[40px] lg:text-[48px]"
            >
              Point it at a repo. Get a URL back.
            </h2>

            <p className="mx-auto mt-5 max-w-[520px] text-[16px] leading-[1.6] text-content-secondary sm:text-[17px]">
              Connect GitHub, import a project and watch the first build stream in. Nothing to
              install locally.
            </p>

            <div className="mt-9 flex flex-wrap items-center justify-center gap-3">
              <Link to="/login">
                <Button variant="primary" size="lg" className="group">
                  Start deploying free
                  <ArrowRight
                    className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5"
                    aria-hidden="true"
                  />
                </Button>
              </Link>

              <a href="#how-it-works">
                <Button variant="secondary" size="lg">
                  Read the workflow
                </Button>
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
