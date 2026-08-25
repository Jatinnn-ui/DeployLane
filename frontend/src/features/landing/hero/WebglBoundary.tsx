import { Component, type ErrorInfo, type ReactNode } from 'react';

/**
 * Keeps a WebGL failure from taking down the page.
 *
 * Context creation genuinely does fail in the wild — blocklisted drivers, headless
 * browsers, hardware acceleration switched off, too many live contexts on the tab. The
 * hero is decorative reinforcement of the copy, so the correct response is to fall back to
 * the static poster and let the rest of the landing page carry on.
 */
export class WebglBoundary extends Component<
  { fallback: ReactNode; children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.warn('Hero visualisation disabled:', error, info.componentStack);
    }
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}
