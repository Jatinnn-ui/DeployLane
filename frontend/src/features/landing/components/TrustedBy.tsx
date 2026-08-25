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
    <section className="pb-7 pt-4" aria-labelledby="trusted-by-heading">
      {/* Reference keeps the label inline at the left of the same row as the logos, which is
          what makes this read as one quiet strip rather than a titled section. */}
      <div className="landing-container flex flex-wrap items-center justify-center gap-x-8 gap-y-4 lg:flex-nowrap lg:justify-between lg:gap-x-6">
        <h2
          id="trusted-by-heading"
          className="shrink-0 text-[9.5px] font-semibold uppercase tracking-[0.14em] text-content-muted"
        >
          Trusted by developers at
        </h2>

        <ul className="flex flex-1 flex-wrap items-center justify-center gap-x-8 gap-y-4 lg:justify-between lg:gap-x-4">
          {PLATFORMS.map((platform) => (
            <li
              key={platform.name}
              className="flex items-center gap-1.5 text-content-secondary opacity-65 transition-opacity duration-200 hover:opacity-100"
            >
              <platform.icon className="h-[15px] w-[15px]" strokeWidth={2} aria-hidden="true" />
              <span className="text-[14px] font-semibold tracking-[-0.01em]">{platform.name}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
