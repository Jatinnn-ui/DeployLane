/**
 * Central query key registry.
 *
 * Keys live in one place so that WebSocket events and mutations can invalidate precisely the right
 * caches. Scattering inline key arrays through components is how stale dashboards happen.
 */
export const queryKeys = {
  platformHealth: ['platform', 'health'] as const,
  authConfig: ['auth', 'config'] as const,
  account: ['account'] as const,
  githubConnection: ['github', 'connection'] as const,

  workspaces: ['workspaces'] as const,
  workspaceMembers: (workspaceId: string) => ['workspaces', workspaceId, 'members'] as const,

  repositories: (query: string, page: number) => ['github', 'repositories', query, page] as const,
  branches: (owner: string, repo: string) => ['github', 'branches', owner, repo] as const,
  latestCommit: (owner: string, repo: string, ref: string) =>
    ['github', 'commit', owner, repo, ref] as const,

  projects: (workspaceId?: string | null) => ['projects', { workspaceId: workspaceId ?? null }] as const,
  project: (projectId: string) => ['projects', projectId] as const,
  projectHealth: (projectId: string) => ['projects', projectId, 'health'] as const,
  environments: (projectId: string) => ['projects', projectId, 'environments'] as const,
  variables: (environmentId: string) => ['environments', environmentId, 'variables'] as const,

  deployments: (projectId: string, environmentId?: string | null, page = 0) =>
    ['projects', projectId, 'deployments', { environmentId: environmentId ?? null, page }] as const,
  deployment: (deploymentId: string) => ['deployments', deploymentId] as const,
  deploymentLogs: (deploymentId: string) => ['deployments', deploymentId, 'logs'] as const,
  deploymentMetrics: (deploymentId: string, windowMinutes: number) =>
    ['deployments', deploymentId, 'metrics', windowMinutes] as const,
  deploymentAnalysis: (deploymentId: string) => ['deployments', deploymentId, 'analysis'] as const,
  rollbackCandidates: (deploymentId: string) =>
    ['deployments', deploymentId, 'rollback-candidates'] as const,
  queueStatus: ['deployments', 'queue-status'] as const,

  notifications: (page: number) => ['notifications', page] as const,
  unreadCount: ['notifications', 'unread-count'] as const,
  activity: (scope: string, id: string | null, page: number) =>
    ['activity', scope, id, page] as const,

  aiStatus: ['ai', 'status'] as const,
  aiTools: ['ai', 'tools'] as const,
} as const;
