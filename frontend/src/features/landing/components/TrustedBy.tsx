import type { CSSProperties } from 'react';
import { Cloud, Container, Cpu, Droplet, Flame, GitBranch, Globe, Layers, Shield, Terminal, Triangle, Zap } from 'lucide-react';
import { GithubIcon } from '@/components/icons/GithubIcon';
import type { IconComponent } from './icon-type';

const PLATFORMS: Array<{ name: string; icon: IconComponent }> = [
  { name: 'Vercel',        icon: Triangle   },
  { name: 'GitHub',        icon: GithubIcon },
  { name: 'Docker',        icon: Container  },
  { name: 'AWS',           icon: Layers     },
  { name: 'Cloudflare',    icon: Cloud      },
  { name: 'Sentry',        icon: Shield     },
  { name: 'DigitalOcean',  icon: Droplet    },
  { name: 'Netlify',       icon: Globe      },
  { name: 'Railway',       icon: Zap        },
  { name: 'Render',        icon: Cpu        },
  { name: 'GitLab',        icon: GitBranch  },
  { name: 'Fly.io',        icon: Flame      },
  { name: 'Heroku',        icon: Terminal   },
];

export function TrustedBy() {
  return (
    <section className="pb-9 pt-3 lg:pt-4" aria-labelledby="trusted-by-heading">
      <div className="landing-container flex flex-col items-center gap-y-3 lg:flex-row lg:flex-nowrap lg:gap-x-6">
        <h2
          id="trusted-by-heading"
          className="shrink-0 text-center text-[length:var(--dl-trust-label)] font-semibold uppercase tracking-[0.03em] text-content-muted lg:text-left"
        >
          Trusted by developers at
        </h2>

        {/*
          Three duplicate sets so the strip is always dense:
          the marquee translates by exactly -1/3 of total width to loop seamlessly.
          translateX(-33.333%) with 3 copies = same visual as -50% with 2, but
          much denser because we have 3× as many logos between seams.
        */}
        <div className="trusted-logo-viewport min-w-0 w-full flex-1 overflow-hidden">
          <ul className="trusted-logo-strip flex items-center gap-x-8">
            {[0, 1, 2].flatMap((copy) =>
              PLATFORMS.map((platform, index) => (
                <li
                  key={`${copy}-${platform.name}`}
                  className="trusted-logo-float flex shrink-0 items-center gap-[0.55em] text-[length:var(--dl-trust-logo)] text-content-secondary grayscale transition-[color,filter,opacity] duration-200 hover:text-content-primary hover:grayscale-0"
                  style={{ '--logo-delay': `${index * -0.42 - copy * 3.2}s` } as CSSProperties}
                >
                  <platform.icon
                    className="h-[1.1em] w-[1.1em] shrink-0"
                    strokeWidth={1.8}
                    aria-hidden="true"
                  />
                  <span className="whitespace-nowrap text-[length:var(--dl-trust-logo)] font-semibold tracking-[-0.01em]">
                    {platform.name}
                  </span>
                </li>
              )),
            )}
          </ul>
        </div>
      </div>
    </section>
  );
}
