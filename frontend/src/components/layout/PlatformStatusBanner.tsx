import { AlertTriangle, X } from 'lucide-react';
import { useState } from 'react';
import { usePlatformHealth } from '@/api/platform';

export function PlatformStatusBanner() {
  const { data: health } = usePlatformHealth();
  const [dismissed, setDismissed] = useState(false);

  if (!health || health.status === 'UP' || dismissed) {
    return null;
  }

  const failing = Object.entries(health.components).filter(([, component]) => !component.up);
  if (failing.length === 0) {
    return null;
  }

  return (
    <div
      role="alert"
      className="flex items-start gap-3 border-b border-warning-border bg-warning-soft px-6 py-2.5 md:px-12"
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
      <div className="min-w-0 flex-1 text-[13px] leading-relaxed">
        <p className="font-medium text-warning-foreground">
          Platform degraded: {failing.map(([name]) => name).join(', ')} unavailable
        </p>
        <ul className="mt-0.5 space-y-0.5 text-content-secondary">
          {failing.map(([name, component]) => (
            <li key={name}>
              <span className="font-mono text-[11px]">{name}</span> — {component.detail}
            </li>
          ))}
        </ul>
      </div>
      <button
        type="button"
        onClick={() => setDismissed(true)}
        className="rounded-sm p-0.5 text-content-muted transition-colors hover:text-content-primary"
        aria-label="Dismiss"
      >
        <X className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
