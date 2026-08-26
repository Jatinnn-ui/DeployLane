import type { CSSProperties } from 'react';
import { Cloud, Container, Droplet, Layers, Triangle } from 'lucide-react';
import { GithubIcon } from '@/components/icons/GithubIcon';
import type { IconComponent } from './icon-type';

/**
 * Platform strip.
 *
 * Rendered as icon-plus-wordmark in a single muted grey rather than as official brand SVGs:
 * it keeps the row monochrome (seven brand palettes would wreck a design built on one
 * accent) and avoids shipping third-party trademarks in the bundle.
 */
const PLATFORMS: Array<{ name: string; icon: IconComponent }> = [
  { name: 'Vercel', icon: Triangle },
  { name: 'GitHub', icon: GithubIcon },
  { name: 'docker', icon: Container },
  { name: 'aws', icon: Layers },
  { name: 'Cloudflare', icon: Cloud },
  { name: 'Sentry', icon: Layers },
  { name: 'DigitalOcean', icon: Droplet },
];

export function TrustedBy() {
  return (
    <section className="pb-9 pt-3 lg:pt-4" aria-labelledby="trusted-by-heading">
      {/* Label sits inline at the left of the logo row, and the whole strip is inset from the
          rail so it lands at the reference's y position inside the first viewport. */}
      <div className="landing-container flex flex-col items-center justify-center gap-y-3 lg:flex-row lg:flex-nowrap lg:justify-between lg:gap-x-5 lg:px-[5.8vw]">
        <h2
          id="trusted-by-heading"
          className="shrink-0 text-center text-[length:var(--dl-trust-label)] font-semibold uppercase tracking-[0.03em] text-white/45 lg:text-left"
        >
          Trusted by developers at
        </h2>

        <div className="trusted-logo-viewport min-w-0 w-full overflow-hidden lg:flex-1">
          <ul className="trusted-logo-strip flex w-max items-center gap-x-8 text-[length:var(--dl-trust-logo)] grayscale">
            {[0, 1].flatMap((copy) =>
              PLATFORMS.map((platform, index) => (
                <li
                  key={`${copy}-${platform.name}`}
                  className="trusted-logo-float flex shrink-0 items-center gap-[0.5em] text-content-primary"
                  style={{ '--logo-delay': `${index * -0.55 - copy * 3.8}s` } as CSSProperties}
                >
                  <platform.icon className="h-[1.1em] w-[1.1em]" strokeWidth={2} aria-hidden="true" />
                  <span className="font-semibold tracking-[-0.01em]">{platform.name}</span>
                </li>
              )),
            )}
          </ul>
        </div>
      </div>
    </section>
  );
}
