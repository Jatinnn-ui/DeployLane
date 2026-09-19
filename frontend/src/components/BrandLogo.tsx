import { cn } from '@/lib/utils';

/** Shared, theme-aware wordmark for marketing and authenticated screens. */
export function LaneMark() {
  return (
    <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <path d="M5 7h9v6H5zM18 7h9v6h-9zM5 17h9v8H5z" fill="currentColor" />
      <path d="m18 17 9-4v8l-9 4z" fill="currentColor" />
    </svg>
  );
}

export function BrandLogo({ className }: { className?: string }) {
  return (
    <span className={cn('product-wordmark', className)}>
      <LaneMark />
      <span>DeployLane<span className="product-wordmark-dot">.</span></span>
    </span>
  );
}
