import { useEffect } from 'react';
import { DashboardShowcase } from './components/DashboardShowcase';
import { FeatureCards } from './components/FeatureCards';
import { FinalCta } from './components/FinalCta';
import { Hero } from './components/Hero';
import { HowItWorks } from './components/HowItWorks';
import { IntegrationStrip } from './components/IntegrationStrip';
import { LandingFooter } from './components/LandingFooter';
import { LandingNav } from './components/LandingNav';

/**
 * Public marketing page.
 *
 * `.landing-root` pins the dark palette: the dashboard is themeable, but this page is
 * designed dark-only, and inheriting a light theme here would break both the 3D scene's
 * lighting and the contrast of the lime accent.
 */
export function LandingPage() {
  useEffect(() => {
    const previous = document.title;
    document.title = 'DeployLane — ship every push to a live URL';
    return () => {
      document.title = previous;
    };
  }, []);

  return (
    <div className="landing-root min-h-screen">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[60] focus:rounded-full focus:bg-accent focus:px-4 focus:py-2 focus:text-[13px] focus:font-semibold focus:text-on-accent"
      >
        Skip to content
      </a>

      <LandingNav />

      <main id="main">
        <Hero />
        <IntegrationStrip />
        <HowItWorks />
        <DashboardShowcase />
        <FeatureCards />
        <FinalCta />
      </main>

      <LandingFooter />
    </div>
  );
}
