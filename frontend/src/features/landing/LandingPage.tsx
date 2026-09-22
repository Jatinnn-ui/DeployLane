import { useEffect } from 'react';
import { BuiltForDevelopers } from './components/BuiltForDevelopers';
import { DashboardShowcase } from './components/DashboardShowcase';
import { Hero } from './components/Hero';
import { HowItWorks } from './components/HowItWorks';
import { LandingFooter } from './components/LandingFooter';
import { LandingNav } from './components/LandingNav';
import { TrustedBy } from './components/TrustedBy';

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
    document.title = 'DeployLane — deploy your code anywhere, in seconds';
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
        <div className="landing-defer">
          <TrustedBy />
        </div>
        <div className="landing-defer">
          <HowItWorks />
        </div>
        <div className="landing-defer">
          <DashboardShowcase />
        </div>
        <div className="landing-defer">
          <BuiltForDevelopers />
        </div>
      </main>

      <LandingFooter />
    </div>
  );
}
