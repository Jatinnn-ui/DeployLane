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
      <div className="landing-container flex flex-wrap items-center justify-center gap-x-7 gap-y-3 lg:flex-nowrap lg:justify-between lg:gap-x-5 lg:px-[60px]">
        <h2
          id="trusted-by-heading"
          className="shrink-0 text-[7px] font-semibold uppercase tracking-[0.03em] text-white/45"
        >
          Trusted by developers at
        </h2>

        <ul className="flex flex-1 flex-wrap items-center justify-center gap-x-7 gap-y-3 opacity-45 grayscale lg:justify-between lg:gap-x-4">
          {PLATFORMS.map((platform) => (
            <li
              key={platform.name}
              className="flex items-center gap-1.5 text-content-primary transition-opacity duration-200"
            >
              <platform.icon className="h-[13px] w-[13px]" strokeWidth={2} aria-hidden="true" />
              <span className="text-[12px] font-semibold tracking-[-0.01em]">{platform.name}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
