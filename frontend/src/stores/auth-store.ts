import { create } from 'zustand';
import { apiClient } from '@/lib/api-client';
import type { GitHubConnectionStatus, User } from '@/types/api';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthState {
  status: AuthStatus;
  user: User | null;
  github: GitHubConnectionStatus | null;
  /** Restores the session from the refresh cookie. Called once on app boot. */
  bootstrap: () => Promise<void>;
  logout: () => Promise<void>;
}

/**
 * Client-only auth state.
 *
 * Zustand holds exactly what is not server data: whether we have a session and who it belongs to. Server
 * data (projects, deployments, logs) lives in TanStack Query and is deliberately not duplicated here.
 */
export const useAuthStore = create<AuthState>((set) => {
  apiClient.onSessionChange((session) => {
    if (session) {
      set({ status: 'authenticated', user: session.user, github: session.github });
    } else {
      set({ status: 'anonymous', user: null, github: null });
    }
  });

  return {
    status: 'loading',
    user: null,
    github: null,

    bootstrap: async () => {
      const session = await apiClient.refreshSession();
      if (session) {
        set({ status: 'authenticated', user: session.user, github: session.github });
      } else {
        set({ status: 'anonymous', user: null, github: null });
      }
    },

    logout: async () => {
      await apiClient.logout();
      set({ status: 'anonymous', user: null, github: null });
    },
  };
});
