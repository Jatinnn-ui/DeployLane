import type { ApiErrorBody, SessionResponse } from '@/types/api';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';

/** Structured error carrying the backend's machine readable code and request id. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId?: string;
  readonly fieldErrors?: Record<string, string>;

  constructor(status: number, body?: Partial<ApiErrorBody>, fallbackMessage?: string) {
    super(body?.message ?? fallbackMessage ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code ?? 'UNKNOWN';
    this.requestId = body?.requestId;
    this.fieldErrors = body?.fieldErrors;
  }

  /** True when the session should be refreshed rather than the user logged out. */
  get isExpiredSession(): boolean {
    return this.status === 401 && this.code === 'SESSION_EXPIRED';
  }

  get isUnauthenticated(): boolean {
    return this.status === 401;
  }

  get isGithubDisconnected(): boolean {
    return this.code === 'GITHUB_NOT_CONNECTED';
  }
}

type TokenListener = (session: SessionResponse | null) => void;

/**
 * Single HTTP client for the whole app.
 *
 * Session design: the access token lives in memory only (never localStorage, so an XSS bug cannot steal a
 * durable credential), and the refresh token is an HttpOnly cookie the browser sends only to
 * `/api/v1/auth/*`. On a 401 with `SESSION_EXPIRED` the client silently refreshes once and replays the
 * request. Concurrent 401s share a single in-flight refresh instead of stampeding the endpoint.
 */
class ApiClient {
  private accessToken: string | null = null;
  private refreshPromise: Promise<SessionResponse | null> | null = null;
  private listeners = new Set<TokenListener>();

  get baseUrl(): string {
    return API_BASE_URL;
  }

  setAccessToken(token: string | null): void {
    this.accessToken = token;
  }

  hasAccessToken(): boolean {
    return this.accessToken !== null;
  }

  onSessionChange(listener: TokenListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit(session: SessionResponse | null): void {
    this.listeners.forEach((listener) => listener(session));
  }

  async request<T>(
    path: string,
    options: RequestInit & { skipAuthRetry?: boolean } = {},
  ): Promise<T> {
    const { skipAuthRetry, ...init } = options;
    const response = await this.send(path, init);

    if (response.status === 401 && !skipAuthRetry) {
      const body = await this.readErrorBody(response);
      const error = new ApiError(response.status, body);
      // Only an expired access token is worth a silent retry; anything else means "sign in".
      if (error.isExpiredSession || this.accessToken === null) {
        const session = await this.refreshSession();
        if (session) {
          const retried = await this.send(path, init);
          return this.unwrap<T>(retried);
        }
      }
      throw error;
    }

    return this.unwrap<T>(response);
  }

  get<T>(path: string): Promise<T> {
    return this.request<T>(path, { method: 'GET' });
  }

  post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  }

  patch<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, {
      method: 'PATCH',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  }

  delete<T>(path: string): Promise<T> {
    return this.request<T>(path, { method: 'DELETE' });
  }

  /** Plain text responses (log download). */
  async getText(path: string): Promise<string> {
    const response = await this.send(path, { method: 'GET' });
    if (!response.ok) {
      throw new ApiError(response.status, await this.readErrorBody(response));
    }
    return response.text();
  }

  /**
   * Exchanges the refresh cookie for a new access token.
   *
   * Deduplicated: several queries failing at once must not fire several refreshes, because each refresh
   * rotates the token and the losers would invalidate the winner.
   */
  async refreshSession(): Promise<SessionResponse | null> {
    if (this.refreshPromise) {
      return this.refreshPromise;
    }
    this.refreshPromise = (async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
          headers: { Accept: 'application/json' },
        });
        if (!response.ok) {
          this.setAccessToken(null);
          this.emit(null);
          return null;
        }
        const session = (await response.json()) as SessionResponse;
        this.setAccessToken(session.accessToken);
        this.emit(session);
        return session;
      } catch {
        this.setAccessToken(null);
        this.emit(null);
        return null;
      } finally {
        this.refreshPromise = null;
      }
    })();
    return this.refreshPromise;
  }

  async logout(): Promise<void> {
    try {
      await fetch(`${API_BASE_URL}/api/v1/auth/logout`, {
        method: 'POST',
        credentials: 'include',
      });
    } finally {
      this.setAccessToken(null);
      this.emit(null);
    }
  }

  /** Single use ticket for the WebSocket handshake, which cannot carry an Authorization header. */
  async websocketTicket(): Promise<string> {
    const response = await this.post<{ ticket: string; expiresInSeconds: number }>(
      '/api/v1/auth/ws-ticket',
    );
    return response.ticket;
  }

  websocketUrl(deploymentId: string, ticket: string): string {
    const base = API_BASE_URL.replace(/^http/, 'ws');
    return `${base}/ws/deployments/${deploymentId}?ticket=${encodeURIComponent(ticket)}`;
  }

  private async send(path: string, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body !== undefined && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }
    if (this.accessToken) {
      headers.set('Authorization', `Bearer ${this.accessToken}`);
    }
    return fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
      credentials: 'include',
    });
  }

  private async unwrap<T>(response: Response): Promise<T> {
    if (response.status === 204) {
      return undefined as T;
    }
    if (!response.ok) {
      throw new ApiError(response.status, await this.readErrorBody(response));
    }
    const text = await response.text();
    if (!text) {
      return undefined as T;
    }
    return JSON.parse(text) as T;
  }

  private async readErrorBody(response: Response): Promise<Partial<ApiErrorBody> | undefined> {
    try {
      return (await response.json()) as ApiErrorBody;
    } catch {
      return undefined;
    }
  }
}

export const apiClient = new ApiClient();
