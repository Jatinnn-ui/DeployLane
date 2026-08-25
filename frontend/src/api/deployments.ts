import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { queryKeys } from '@/api/keys';
import type {
  ChatResponse,
  CursorPageResponse,
  Deployment,
  DeploymentAnalysis,
  DeploymentMetrics,
  DeploymentSummary,
  LogEntry,
  PageResponse,
  RollbackCandidate,
} from '@/types/api';

export function useDeployments(
  projectId: string | undefined,
  environmentId?: string | null,
  page = 0,
) {
  return useQuery({
    queryKey: queryKeys.deployments(projectId ?? 'none', environmentId, page),
    queryFn: () =>
      apiClient.get<PageResponse<DeploymentSummary>>(
        `/api/v1/projects/${projectId}/deployments?page=${page}&size=20${
          environmentId ? `&environmentId=${environmentId}` : ''
        }`,
      ),
    enabled: Boolean(projectId),
    placeholderData: (previous) => previous,
  });
}

/**
 * A single deployment.
 *
 * Polling is a safety net, not the primary mechanism: while a deployment is in flight the WebSocket pushes
 * status and step updates. Polling every 5s covers the case where the socket could not connect at all.
 */
export function useDeployment(deploymentId: string | undefined, live = false) {
  return useQuery({
    queryKey: queryKeys.deployment(deploymentId ?? 'none'),
    queryFn: () => apiClient.get<Deployment>(`/api/v1/deployments/${deploymentId}`),
    enabled: Boolean(deploymentId),
    refetchInterval: live ? 5_000 : false,
  });
}

export function useCreateDeployment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input?: { environmentId?: string; branch?: string }) =>
      apiClient.post<Deployment>(`/api/v1/projects/${projectId}/deployments`, input ?? {}),
    onSuccess: (deployment) => {
      queryClient.setQueryData(queryKeys.deployment(deployment.id), deployment);
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'deployments'] });
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useCancelDeployment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (deploymentId: string) =>
      apiClient.post<void>(`/api/v1/deployments/${deploymentId}/cancel`),
    onSuccess: (_result, deploymentId) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.deployment(deploymentId) });
    },
  });
}

export function useRedeploy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (deploymentId: string) =>
      apiClient.post<Deployment>(`/api/v1/deployments/${deploymentId}/redeploy`),
    onSuccess: (deployment) => {
      queryClient.setQueryData(queryKeys.deployment(deployment.id), deployment);
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useRollback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (targetDeploymentId: string) =>
      apiClient.post<Deployment>(`/api/v1/deployments/${targetDeploymentId}/rollback`),
    onSuccess: (deployment) => {
      queryClient.setQueryData(queryKeys.deployment(deployment.id), deployment);
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useRollbackCandidates(deploymentId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.rollbackCandidates(deploymentId ?? 'none'),
    queryFn: () =>
      apiClient.get<RollbackCandidate[]>(
        `/api/v1/deployments/${deploymentId}/rollback-candidates`,
      ),
    enabled: Boolean(deploymentId),
  });
}

/* ----------------------------------------------------------------- logs */

/** Initial page of logs; live lines arrive over the WebSocket afterwards. */
export function useDeploymentLogs(deploymentId: string | undefined, limit = 400) {
  return useQuery({
    queryKey: queryKeys.deploymentLogs(deploymentId ?? 'none'),
    queryFn: () =>
      apiClient.get<CursorPageResponse<LogEntry>>(
        `/api/v1/deployments/${deploymentId}/logs?limit=${limit}`,
      ),
    enabled: Boolean(deploymentId),
    // Logs are append only and streamed; refetching on focus would duplicate work for nothing.
    refetchOnWindowFocus: false,
    staleTime: Number.POSITIVE_INFINITY,
  });
}

export async function fetchMoreLogs(
  deploymentId: string,
  afterSequence: number,
  limit = 400,
): Promise<CursorPageResponse<LogEntry>> {
  return apiClient.get<CursorPageResponse<LogEntry>>(
    `/api/v1/deployments/${deploymentId}/logs?afterSequence=${afterSequence}&limit=${limit}`,
  );
}

export async function downloadDeploymentLogs(deploymentId: string): Promise<string> {
  return apiClient.getText(`/api/v1/deployments/${deploymentId}/logs/download`);
}

/* ----------------------------------------------------------------- metrics */

export function useDeploymentMetrics(
  deploymentId: string | undefined,
  windowMinutes = 60,
  live = true,
) {
  return useQuery({
    queryKey: queryKeys.deploymentMetrics(deploymentId ?? 'none', windowMinutes),
    queryFn: () =>
      apiClient.get<DeploymentMetrics>(
        `/api/v1/deployments/${deploymentId}/metrics?windowMinutes=${windowMinutes}`,
      ),
    enabled: Boolean(deploymentId),
    refetchInterval: live ? 30_000 : false,
  });
}

/* ----------------------------------------------------------------- ai */

export function useDeploymentAnalysis(deploymentId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: queryKeys.deploymentAnalysis(deploymentId ?? 'none'),
    queryFn: () =>
      apiClient.get<DeploymentAnalysis>(`/api/v1/deployments/${deploymentId}/analysis`),
    enabled: Boolean(deploymentId) && enabled,
  });
}

export function useAnalyzeDeployment(deploymentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (force: boolean) =>
      apiClient.post<DeploymentAnalysis>(
        `/api/v1/deployments/${deploymentId}/analysis?force=${force}`,
      ),
    onSuccess: (analysis) => {
      queryClient.setQueryData(queryKeys.deploymentAnalysis(deploymentId), analysis);
    },
  });
}

export function useAiChat(projectId: string) {
  return useMutation({
    mutationFn: (input: {
      message: string;
      history?: { role: string; content: string }[];
      deploymentId?: string | null;
    }) => apiClient.post<ChatResponse>(`/api/v1/projects/${projectId}/ai/chat`, input),
  });
}
