/**
 * Types mirroring the backend DTOs.
 *
 * Hand written on purpose: the surface is small enough that generated clients would add build tooling
 * for little gain, and keeping these by hand documents the contract the UI actually relies on.
 */

export type DeploymentStatus =
  | 'QUEUED'
  | 'CLONING'
  | 'DETECTING'
  | 'BUILDING'
  | 'IMAGE_BUILDING'
  | 'STARTING'
  | 'HEALTH_CHECKING'
  | 'READY'
  | 'FAILED'
  | 'CANCELLED'
  | 'STOPPED';

export type DeploymentTriggerType =
  | 'MANUAL'
  | 'GIT_PUSH'
  | 'PULL_REQUEST'
  | 'ROLLBACK'
  | 'REDEPLOY';

export type ProjectStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type EnvironmentType = 'PRODUCTION' | 'PREVIEW' | 'DEVELOPMENT';
export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'DEVELOPER' | 'VIEWER';
export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
export type LogSource = 'SYSTEM' | 'GIT' | 'BUILD' | 'DOCKER' | 'APPLICATION' | 'HEALTH' | 'AI';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type StepStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED' | 'CANCELLED';

export type Permission =
  | 'VIEW_PROJECT'
  | 'VIEW_LOGS'
  | 'VIEW_MONITORING'
  | 'VIEW_ANALYSIS'
  | 'DEPLOY'
  | 'CANCEL_DEPLOYMENT'
  | 'ROLLBACK'
  | 'MANAGE_PROJECT'
  | 'DELETE_PROJECT'
  | 'MANAGE_ENVIRONMENT'
  | 'MANAGE_VARIABLES'
  | 'USE_AI'
  | 'MANAGE_MEMBERS'
  | 'MANAGE_WORKSPACE'
  | 'DELETE_WORKSPACE';

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  requestId?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface CursorPageResponse<T> {
  items: T[];
  nextCursor: number | null;
  hasMore: boolean;
  limit: number;
}

/* ----------------------------------------------------------------- health */

export interface ComponentHealth {
  up: boolean;
  status: string;
  detail: string;
  latencyMs: number;
}

export interface PlatformHealth {
  status: 'UP' | 'DEGRADED';
  timestamp: string;
  uptimeSeconds: number;
  components: Record<string, ComponentHealth>;
}

/* ----------------------------------------------------------------- auth */

export interface User {
  id: string;
  name: string;
  email?: string | null;
  avatarUrl?: string | null;
  githubUsername: string;
  createdAt: string;
}

export interface GitHubConnectionStatus {
  connected: boolean;
  githubUsername?: string | null;
  scopes?: string | null;
  hasRepoScope: boolean;
  connectedAt?: string | null;
}

export interface SessionResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: User;
  github: GitHubConnectionStatus;
}

export interface AuthConfig {
  githubOauthEnabled: boolean;
  loginUrl: string;
}

/* ----------------------------------------------------------------- workspaces */

export interface Workspace {
  id: string;
  name: string;
  slug: string;
  ownerId: string;
  role: WorkspaceRole;
  permissions: Permission[];
  memberCount: number;
  createdAt: string;
}

export interface WorkspaceMember {
  id: string;
  user: User;
  role: WorkspaceRole;
  joinedAt: string;
}

/* ----------------------------------------------------------------- github */

export interface RepositorySummary {
  id: number;
  name: string;
  fullName: string;
  owner: string;
  description?: string | null;
  language?: string | null;
  isPrivate: boolean;
  defaultBranch: string;
  htmlUrl: string;
  cloneUrl: string;
  updatedAt?: string | null;
  pushedAt?: string | null;
  stars?: number | null;
  archived: boolean;
  fork: boolean;
  canAdmin: boolean;
}

export interface RepositoryListResponse {
  repositories: RepositorySummary[];
  page: number;
  perPage: number;
  hasMore: boolean;
  total: number;
}

export interface BranchSummary {
  name: string;
  commitSha?: string | null;
  isProtected: boolean;
}

export interface CommitSummary {
  sha: string;
  shortSha: string;
  message: string;
  authorName?: string | null;
  authorAvatarUrl?: string | null;
  committedAt?: string | null;
  htmlUrl?: string | null;
}

/* ----------------------------------------------------------------- detection */

export interface DetectionResult {
  framework: string;
  runtime: string;
  installCommand?: string | null;
  buildCommand?: string | null;
  startCommand?: string | null;
  port: number;
  dockerfilePath?: string | null;
  packageManager: string;
  confidence: number;
  evidence: string[];
  warnings: string[];
}

/* ----------------------------------------------------------------- projects */

export interface BuildConfig {
  rootDirectory?: string | null;
  installCommand?: string | null;
  buildCommand?: string | null;
  startCommand?: string | null;
  dockerfilePath?: string | null;
  port?: number | null;
  healthCheckPath?: string | null;
}

export interface RepositoryRef {
  owner: string;
  name: string;
  fullName: string;
  url: string;
  isPrivate: boolean;
  githubId?: number | null;
}

export interface EnvironmentResponse {
  id: string;
  projectId: string;
  name: string;
  type: EnvironmentType;
  branch: string;
  autoDeployEnabled: boolean;
  variableCount: number;
  createdAt: string;
}

export interface DeploymentSnapshot {
  deploymentId: string;
  deploymentNumber: number;
  status: DeploymentStatus;
  branch?: string | null;
  commitSha?: string | null;
  commitMessage?: string | null;
  deploymentUrl?: string | null;
  createdAt: string;
  finishedAt?: string | null;
  durationMs?: number | null;
}

export interface Project {
  id: string;
  workspaceId: string;
  name: string;
  slug: string;
  description?: string | null;
  repository: RepositoryRef;
  defaultBranch: string;
  framework?: string | null;
  frameworkLabel?: string | null;
  runtime?: string | null;
  status: ProjectStatus;
  buildConfig: BuildConfig;
  environments: EnvironmentResponse[];
  latestDeployment?: DeploymentSnapshot | null;
  permissions: Permission[];
  createdAt: string;
  updatedAt: string;
}

export interface ProjectSummary {
  id: string;
  workspaceId: string;
  name: string;
  slug: string;
  repository: RepositoryRef;
  framework?: string | null;
  frameworkLabel?: string | null;
  status: ProjectStatus;
  productionBranch?: string | null;
  latestDeployment?: DeploymentSnapshot | null;
  createdAt: string;
}

export interface ImportProjectRequest {
  workspaceId?: string | null;
  repositoryOwner: string;
  repositoryName: string;
  branch: string;
  name?: string;
  slug?: string;
  description?: string;
  framework?: string | null;
  buildConfig?: BuildConfig;
  autoDeployEnabled: boolean;
  environmentVariables?: { key: string; value: string }[];
}

/* ----------------------------------------------------------------- deployments */

export interface CommitInfo {
  sha?: string | null;
  shortSha?: string | null;
  message?: string | null;
  author?: string | null;
  url?: string | null;
}

export interface DeploymentStep {
  step: string;
  label: string;
  status: StepStatus;
  sequence: number;
  startedAt?: string | null;
  finishedAt?: string | null;
  durationMs?: number | null;
  detail?: string | null;
}

export interface Deployment {
  id: string;
  projectId: string;
  projectName?: string | null;
  projectSlug?: string | null;
  environmentId: string;
  environmentName?: string | null;
  deploymentNumber: number;
  status: DeploymentStatus;
  statusLabel: string;
  triggerType: DeploymentTriggerType;
  branch: string;
  commit: CommitInfo;
  build: {
    framework?: string | null;
    frameworkLabel?: string | null;
    runtime?: string | null;
    rootDirectory?: string | null;
    installCommand?: string | null;
    buildCommand?: string | null;
    startCommand?: string | null;
    dockerfilePath?: string | null;
    containerPort?: number | null;
  };
  container: {
    containerId?: string | null;
    hostPort?: number | null;
    containerPort?: number | null;
    imageTag?: string | null;
  };
  url?: string | null;
  promoted: boolean;
  cancellationRequested: boolean;
  failure?: { stage?: string | null; message?: string | null; exitCode?: number | null } | null;
  timing: {
    queuedAt?: string | null;
    startedAt?: string | null;
    finishedAt?: string | null;
    durationMs?: number | null;
  };
  createdBy?: { id: string; name: string; avatarUrl?: string | null } | null;
  steps: DeploymentStep[];
  createdAt: string;
}

export interface DeploymentSummary {
  id: string;
  projectId: string;
  deploymentNumber: number;
  status: DeploymentStatus;
  statusLabel: string;
  triggerType: DeploymentTriggerType;
  environmentName?: string | null;
  branch: string;
  commitSha?: string | null;
  shortCommitSha?: string | null;
  commitMessage?: string | null;
  url?: string | null;
  promoted: boolean;
  failureMessage?: string | null;
  durationMs?: number | null;
  createdAt: string;
  finishedAt?: string | null;
}

export interface RollbackCandidate {
  deploymentId: string;
  deploymentNumber: number;
  commitSha?: string | null;
  shortCommitSha?: string | null;
  commitMessage?: string | null;
  createdAt: string;
  imageAvailable: boolean;
}

/* ----------------------------------------------------------------- logs */

export interface LogEntry {
  sequence: number;
  timestamp: string;
  level: LogLevel;
  source: LogSource;
  message: string;
}

/* ----------------------------------------------------------------- env vars */

export interface EnvironmentVariable {
  id: string;
  environmentId: string;
  key: string;
  createdAt: string;
  updatedAt: string;
}

export interface BulkVariablesResponse {
  created: number;
  updated: number;
  deleted: number;
  skipped: string[];
}

/* ----------------------------------------------------------------- monitoring */

export interface ProjectHealth {
  projectId: string;
  status: 'HEALTHY' | 'UNHEALTHY' | 'NOT_LIVE' | 'FAILED' | 'NO_DEPLOYMENT';
  environmentId?: string | null;
  environmentName?: string | null;
  deploymentId?: string | null;
  deploymentNumber?: number | null;
  deploymentStatus?: string | null;
  url?: string | null;
  commitSha?: string | null;
  deployedAt?: string | null;
  uptimeSeconds?: number | null;
  containerStatus?: string | null;
  restartCount?: number | null;
  cpuPercent?: number | null;
  memoryBytes?: number | null;
  memoryLimitBytes?: number | null;
  lastSampledAt?: string | null;
  detail?: string | null;
}

export interface MetricSample {
  sampledAt: string;
  cpuPercent?: number | null;
  memoryBytes?: number | null;
  memoryLimitBytes?: number | null;
  restartCount?: number | null;
  containerStatus?: string | null;
}

export interface DeploymentMetrics {
  deploymentId: string;
  containerStatus: string;
  cpuPercent?: number | null;
  memoryBytes?: number | null;
  memoryLimitBytes?: number | null;
  restartCount?: number | null;
  startedAt?: string | null;
  uptimeSeconds?: number | null;
  history: MetricSample[];
  live: boolean;
}

/* ----------------------------------------------------------------- ai */

export interface SuggestedFix {
  title: string;
  description?: string | null;
  command?: string | null;
}

export interface DeploymentAnalysis {
  deploymentId: string;
  status: 'PENDING' | 'COMPLETED' | 'UNAVAILABLE';
  summary?: string | null;
  rootCause?: string | null;
  evidence: string[];
  suggestedFixes: SuggestedFix[];
  severity?: Severity | null;
  confidence?: number | null;
  provider?: string | null;
  model?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AiStatus {
  configuredProvider: string;
  activeProvider: string;
  externalProviderActive: boolean;
}

export interface ChatResponse {
  answer: string;
  provider: string;
  model: string;
  usedTools: string[];
  externalProvider: boolean;
  answeredAt: string;
}

/* ----------------------------------------------------------------- notifications & activity */

export interface Notification {
  id: string;
  type: string;
  title: string;
  message?: string | null;
  read: boolean;
  projectId?: string | null;
  deploymentId?: string | null;
  createdAt: string;
}

export interface ActivityEntry {
  id: string;
  workspaceId?: string | null;
  projectId?: string | null;
  actorId?: string | null;
  actorName?: string | null;
  action: string;
  resourceType: string;
  resourceId?: string | null;
  metadata: Record<string, unknown>;
  createdAt: string;
}

/* ----------------------------------------------------------------- websocket events */

export type DeploymentEvent =
  | { type: 'CONNECTED'; deploymentId: string; timestamp: string }
  | { type: 'PONG'; timestamp: string }
  | {
      type: 'LOG';
      deploymentId: string;
      timestamp: string;
      sequence: number;
      level: LogLevel;
      source: LogSource;
      message: string;
    }
  | {
      type: 'STATUS_CHANGED';
      deploymentId: string;
      timestamp: string;
      status: DeploymentStatus;
      statusLabel: string;
      deploymentNumber?: number;
    }
  | {
      type: 'STEP_UPDATED';
      deploymentId: string;
      timestamp: string;
      step: string;
      stepLabel: string;
      status: StepStatus;
      durationMs?: number | null;
      detail?: string | null;
    }
  | {
      type: 'DEPLOYMENT_READY';
      deploymentId: string;
      timestamp: string;
      url: string;
      durationMs?: number | null;
    }
  | {
      type: 'DEPLOYMENT_FAILED';
      deploymentId: string;
      timestamp: string;
      stage: string;
      message: string;
      exitCode?: number | null;
    }
  | {
      type: 'ANALYSIS_READY';
      deploymentId: string;
      timestamp: string;
      severity?: string | null;
      confidence?: number | null;
    }
  | {
      type: 'METRICS';
      deploymentId: string;
      timestamp: string;
      cpuPercent?: number | null;
      memoryBytes?: number | null;
      memoryLimitBytes?: number | null;
      restartCount?: number | null;
      containerStatus?: string | null;
    };
