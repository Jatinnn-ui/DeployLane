import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { queryKeys } from '@/api/keys';
import type {
  BulkVariablesResponse,
  EnvironmentResponse,
  EnvironmentVariable,
  ImportProjectRequest,
  PageResponse,
  Project,
  ProjectHealth,
  ProjectSummary,
} from '@/types/api';

export function useProjects(workspaceId?: string | null) {
  return useQuery({
    queryKey: queryKeys.projects(workspaceId),
    queryFn: () =>
      apiClient.get<PageResponse<ProjectSummary>>(
        `/api/v1/projects?size=50${workspaceId ? `&workspaceId=${workspaceId}` : ''}`,
      ),
  });
}

export function useProject(projectId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.project(projectId ?? 'none'),
    queryFn: () => apiClient.get<Project>(`/api/v1/projects/${projectId}`),
    enabled: Boolean(projectId),
  });
}

export function useProjectHealth(projectId: string | undefined, live = true) {
  return useQuery({
    queryKey: queryKeys.projectHealth(projectId ?? 'none'),
    queryFn: () => apiClient.get<ProjectHealth>(`/api/v1/projects/${projectId}/health`),
    enabled: Boolean(projectId),
    // Matches the backend's 30s sampling interval; polling faster only reads the same row again.
    refetchInterval: live ? 30_000 : false,
  });
}

export function useImportProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ImportProjectRequest) => apiClient.post<Project>('/api/v1/projects', input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useUpdateProject(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: Record<string, unknown>) =>
      apiClient.patch<Project>(`/api/v1/projects/${projectId}`, input),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.project(projectId), updated);
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useDeleteProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (projectId: string) => apiClient.delete<void>(`/api/v1/projects/${projectId}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

/* ----------------------------------------------------------------- environments */

export function useEnvironments(projectId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.environments(projectId ?? 'none'),
    queryFn: () =>
      apiClient.get<EnvironmentResponse[]>(`/api/v1/projects/${projectId}/environments`),
    enabled: Boolean(projectId),
  });
}

export function useUpdateEnvironment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { environmentId: string; branch?: string; autoDeployEnabled?: boolean }) =>
      apiClient.patch<EnvironmentResponse>(`/api/v1/environments/${input.environmentId}`, {
        branch: input.branch,
        autoDeployEnabled: input.autoDeployEnabled,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.environments(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.project(projectId) });
    },
  });
}

/* ----------------------------------------------------------------- variables */

export function useEnvironmentVariables(environmentId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.variables(environmentId ?? 'none'),
    queryFn: () =>
      apiClient.get<{ environmentId: string; variables: EnvironmentVariable[]; count: number }>(
        `/api/v1/environments/${environmentId}/variables`,
      ),
    enabled: Boolean(environmentId),
  });
}

export function useCreateVariable(environmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { key: string; value: string }) =>
      apiClient.post<EnvironmentVariable>(
        `/api/v1/environments/${environmentId}/variables`,
        input,
      ),
    onSuccess: () => invalidateVariables(queryClient, environmentId),
  });
}

export function useUpdateVariable(environmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { variableId: string; value: string }) =>
      apiClient.patch<EnvironmentVariable>(`/api/v1/environment-variables/${input.variableId}`, {
        value: input.value,
      }),
    onSuccess: () => invalidateVariables(queryClient, environmentId),
  });
}

export function useDeleteVariable(environmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variableId: string) =>
      apiClient.delete<void>(`/api/v1/environment-variables/${variableId}`),
    onSuccess: () => invalidateVariables(queryClient, environmentId),
  });
}

export function useBulkVariables(environmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { content: string; replaceExisting: boolean }) =>
      apiClient.post<BulkVariablesResponse>(
        `/api/v1/environments/${environmentId}/variables/bulk`,
        input,
      ),
    onSuccess: () => invalidateVariables(queryClient, environmentId),
  });
}

function invalidateVariables(
  queryClient: ReturnType<typeof useQueryClient>,
  environmentId: string,
): void {
  void queryClient.invalidateQueries({ queryKey: queryKeys.variables(environmentId) });
  void queryClient.invalidateQueries({ queryKey: ['projects'] });
}
