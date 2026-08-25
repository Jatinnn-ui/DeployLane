import { useMutation, useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { queryKeys } from '@/api/keys';
import type {
  BranchSummary,
  CommitSummary,
  DetectionResult,
  RepositoryListResponse,
} from '@/types/api';

/**
 * Repository search.
 *
 * The backend keeps a short lived snapshot per user, so typing in the picker is cheap and does not burn
 * GitHub rate limit. `placeholderData` keeps the previous page visible while a new query resolves, which
 * removes the flicker that makes search feel broken.
 */
export function useRepositories(query: string, page = 1, enabled = true) {
  return useQuery({
    queryKey: queryKeys.repositories(query, page),
    queryFn: () =>
      apiClient.get<RepositoryListResponse>(
        `/api/v1/github/repositories?query=${encodeURIComponent(query)}&page=${page}&perPage=20`,
      ),
    enabled,
    placeholderData: (previous) => previous,
    staleTime: 60_000,
  });
}

export function useBranches(owner?: string, repo?: string) {
  return useQuery({
    queryKey: queryKeys.branches(owner ?? '', repo ?? ''),
    queryFn: () =>
      apiClient.get<BranchSummary[]>(`/api/v1/github/repositories/${owner}/${repo}/branches`),
    enabled: Boolean(owner && repo),
    staleTime: 30_000,
  });
}

export function useLatestCommit(owner?: string, repo?: string, ref?: string) {
  return useQuery({
    queryKey: queryKeys.latestCommit(owner ?? '', repo ?? '', ref ?? ''),
    queryFn: () =>
      apiClient.get<CommitSummary>(
        `/api/v1/github/repositories/${owner}/${repo}/commits/latest?ref=${encodeURIComponent(ref ?? '')}`,
      ),
    enabled: Boolean(owner && repo && ref),
  });
}

/** Framework detection against a branch, run before the project exists. */
export function useDetectFramework() {
  return useMutation({
    mutationFn: (input: { owner: string; repo: string; ref: string; rootDirectory?: string }) =>
      apiClient.post<DetectionResult>('/api/v1/detection', input),
  });
}
