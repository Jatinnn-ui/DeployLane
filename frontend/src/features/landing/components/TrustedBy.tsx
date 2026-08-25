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
    <section className="py-10" aria-labelledby="trusted-by-heading">
      <div className="landing-container">
        <h2
          id="trusted-by-heading"
          className="text-center text-[10.5px] font-semibold uppercase tracking-[0.16em] text-content-muted"
        >
          Trusted by developers at
        </h2>

        <ul className="mt-7 flex flex-wrap items-center justify-center gap-x-9 gap-y-5 lg:gap-x-12">
          {PLATFORMS.map((platform) => (
            <li
              key={platform.name}
              className="flex items-center gap-2 text-content-secondary opacity-70 transition-opacity duration-200 hover:opacity-100"
            >
              <platform.icon className="h-4 w-4" strokeWidth={2} aria-hidden="true" />
              <span className="text-[15px] font-semibold tracking-[-0.01em]">{platform.name}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
