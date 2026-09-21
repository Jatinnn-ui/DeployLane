/**
 * Full-screen loading splash shown while the auth session is being restored.
 *
 * Renders before any router decision so the user never sees a bare white
 * background or a flash of the login page. Uses only CSS custom properties
 * from the design system so it inherits the correct theme instantly — the
 * bootstrap script in index.html sets `data-theme` before first paint.
 */
export function AppLoader() {
  return (
    <div
      className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-5 bg-canvas"
      aria-label="Loading application"
      role="status"
    >
      {/* Pulsing brand icon — keeps the branded impression while loading */}
      <div className="flex h-12 w-12 items-center justify-center rounded-xl border border-border-subtle bg-surface shadow-sm">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          className="h-6 w-6 animate-[pulse-ring_2s_cubic-bezier(0.4,0,0.6,1)_infinite] text-accent"
          aria-hidden="true"
        >
          <path
            d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>

      {/* Subtle horizontal shimmer bar */}
      <div className="h-1 w-32 overflow-hidden rounded-full bg-surface">
        <div className="h-full w-1/2 animate-shimmer rounded-full bg-accent-soft" />
      </div>

      <p className="text-[13px] text-content-muted">Loading DeployLane...</p>
    </div>
  );
}
