import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { queryKeys } from '@/api/keys';
import type {
  ActivityEntry,
  AiStatus,
  AuthConfig,
  GitHubConnectionStatus,
  Notification,
  PageResponse,
  PlatformHealth,
  User,
  Workspace,
  WorkspaceMember,
} from '@/types/api';

/** Platform connectivity. Polled slowly: it is a banner, not a metric. */
export function usePlatformHealth() {
  return useQuery({
    queryKey: queryKeys.platformHealth,
    queryFn: () => apiClient.get<PlatformHealth>('/api/v1/health'),
    refetchInterval: 30_000,
    retry: 1,
    // A degraded platform answers 503 with a body; that is data, not an error to hide.
    throwOnError: false,
  });
}

export function useAuthConfig() {
  return useQuery({
    queryKey: queryKeys.authConfig,
    queryFn: () => apiClient.get<AuthConfig>('/api/v1/auth/config'),
    staleTime: 5 * 60_000,
  });
}

export function useAccount() {
  return useQuery({
    queryKey: queryKeys.account,
    queryFn: () => apiClient.get<User>('/api/v1/account'),
  });
}

export function useGithubConnection() {
  return useQuery({
    queryKey: queryKeys.githubConnection,
    queryFn: () => apiClient.get<GitHubConnectionStatus>('/api/v1/github/connection'),
  });
}

export function useWorkspaces() {
  return useQuery({
    queryKey: queryKeys.workspaces,
    queryFn: () => apiClient.get<Workspace[]>('/api/v1/workspaces'),
    staleTime: 60_000,
  });
}

export function useWorkspaceMembers(workspaceId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.workspaceMembers(workspaceId ?? 'none'),
    queryFn: () => apiClient.get<WorkspaceMember[]>(`/api/v1/workspaces/${workspaceId}/members`),
    enabled: Boolean(workspaceId),
  });
}

export function useAddWorkspaceMember(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { githubUsername: string; role: string }) =>
      apiClient.post<WorkspaceMember>(`/api/v1/workspaces/${workspaceId}/members`, input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.workspaceMembers(workspaceId) }),
  });
}

export function useUpdateMemberRole(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { memberId: string; role: string }) =>
      apiClient.patch<WorkspaceMember>(
        `/api/v1/workspaces/${workspaceId}/members/${input.memberId}`,
        { role: input.role },
      ),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.workspaceMembers(workspaceId) }),
  });
}

export function useRemoveMember(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) =>
      apiClient.delete<void>(`/api/v1/workspaces/${workspaceId}/members/${memberId}`),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.workspaceMembers(workspaceId) }),
  });
}

export function useNotifications(page = 0) {
  return useQuery({
    queryKey: queryKeys.notifications(page),
    queryFn: () =>
      apiClient.get<PageResponse<Notification>>(`/api/v1/notifications?page=${page}&size=20`),
  });
}

export function useUnreadNotificationCount() {
  return useQuery({
    queryKey: queryKeys.unreadCount,
    queryFn: () => apiClient.get<{ count: number }>('/api/v1/notifications/unread-count'),
    refetchInterval: 60_000,
  });
}

export function useMarkNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient.post<{ updated: number }>('/api/v1/notifications/read-all'),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}

export function useActivityFeed(
  scope: { kind: 'global' } | { kind: 'workspace'; id: string } | { kind: 'project'; id: string },
  page = 0,
) {
  const path =
    scope.kind === 'global'
      ? `/api/v1/activity?page=${page}&size=30`
      : scope.kind === 'workspace'
        ? `/api/v1/workspaces/${scope.id}/activity?page=${page}&size=30`
        : `/api/v1/projects/${scope.id}/activity?page=${page}&size=30`;

  return useQuery({
    queryKey: queryKeys.activity(scope.kind, scope.kind === 'global' ? null : scope.id, page),
    queryFn: () => apiClient.get<PageResponse<ActivityEntry>>(path),
  });
}

export function useAiStatus() {
  return useQuery({
    queryKey: queryKeys.aiStatus,
    queryFn: () => apiClient.get<AiStatus>('/api/v1/ai/status'),
    staleTime: 5 * 60_000,
  });
}

export function useAiTools() {
  return useQuery({
    queryKey: queryKeys.aiTools,
    queryFn: () => apiClient.get<string[]>('/api/v1/ai/tools'),
    staleTime: 10 * 60_000,
  });
}

export function useDisconnectGithub() {
  return useMutation({
    mutationFn: () => apiClient.delete<void>('/api/v1/account/github'),
  });
}
