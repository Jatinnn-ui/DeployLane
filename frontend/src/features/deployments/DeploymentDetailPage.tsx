import {
  ArrowLeft,
  Ban,
  Container,
  ExternalLink,
  GitBranch,
  GitCommitHorizontal,
  RefreshCw,
  RotateCcw,
  Settings2,
  User,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  downloadDeploymentLogs,
  useCancelDeployment,
  useDeployment,
  useRedeploy,
  useRollback,
  useRollbackCandidates,
} from '@/api/deployments';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader, MetaItem } from '@/components/ui/card';
import { ErrorState, Skeleton } from '@/components/ui/feedback';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/overlay';
import { useToast } from '@/components/ui/toast';
import { DeploymentStatusBadge, isDeploymentInProgress } from '@/components/DeploymentStatusBadge';
import { AnalysisCard } from '@/features/deployments/AnalysisCard';
import { DeploymentTimeline } from '@/features/deployments/DeploymentTimeline';
import { LogTerminal } from '@/features/logs/LogTerminal';
import { useDeploymentStream } from '@/features/deployments/useDeploymentStream';
import { absoluteTime, formatDuration, humanizeEnum, relativeTime } from '@/lib/utils';
import type { Deployment } from '@/types/api';

/**
 * The screen a developer stares at while shipping.
 *
 * Live by default: while the deployment is in flight the WebSocket drives the status badge, the timeline and
 * the log terminal, and a slower poll acts as a fallback if the socket cannot connect at all.
 */
export function DeploymentDetailPage() {
  const { deploymentId } = useParams<{ deploymentId: string }>();
  const stream = useDeploymentStream(deploymentId);
  // Derived, not stored: polling switches itself off the moment the deployment reaches a terminal state.
  const [knownStatus, setKnownStatus] = useState<Deployment['status'] | null>(null);
  const inProgress = isDeploymentInProgress(stream.liveStatus ?? knownStatus ?? 'QUEUED');
  const { data: deployment, isLoading, error, refetch } = useDeployment(deploymentId, inProgress);

  useEffect(() => {
    if (deployment && deployment.status !== knownStatus) {
      setKnownStatus(deployment.status);
    }
  }, [deployment, knownStatus]);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  if (error || !deployment) {
    return <ErrorState error={error} onRetry={() => void refetch()} />;
  }

  return (
    <div className="space-y-6">
      <Header deployment={deployment} onChanged={() => void refetch()} />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-w-0 space-y-6">
          <LogTerminal
            entries={stream.logs}
            socketState={stream.socketState}
            onClear={stream.clear}
            onDownload={() => downloadDeploymentLogs(deployment.id)}
            emptyMessage={
              inProgress ? 'Waiting for the first line of output...' : 'This deployment produced no logs.'
            }
          />

          <AnalysisCard deployment={deployment} />

          {deployment.failure?.message ? (
            <Card>
              <CardHeader title="Failure" description={humanizeEnum(deployment.failure.stage)} />
              <CardBody>
                <p className="break-words font-mono text-[12px] leading-relaxed text-danger-foreground">
                  {deployment.failure.message}
                </p>
                {deployment.failure.exitCode !== null && deployment.failure.exitCode !== undefined ? (
                  <p className="mt-2 text-[12px] text-content-muted">
                    Exit code {deployment.failure.exitCode}
                  </p>
                ) : null}
              </CardBody>
            </Card>
          ) : null}
        </div>

        <aside className="space-y-6">
          <Card>
            <CardHeader title="Timeline" />
            <CardBody>
              <DeploymentTimeline steps={deployment.steps} />
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Commit" icon={<GitCommitHorizontal className="h-4 w-4" />} />
            <CardBody>
              <dl className="space-y-3">
                <MetaItem label="Branch" value={deployment.branch} mono />
                <MetaItem
                  label="Commit"
                  value={
                    deployment.commit.url ? (
                      <a
                        href={deployment.commit.url}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-accent hover:underline"
                      >
                        {deployment.commit.shortSha ?? '-'}
                        <ExternalLink className="h-3 w-3" aria-hidden="true" />
                      </a>
                    ) : (
                      (deployment.commit.shortSha ?? '-')
                    )
                  }
                  mono
                />
                <MetaItem label="Message" value={deployment.commit.message ?? '-'} />
                <MetaItem label="Author" value={deployment.commit.author ?? '-'} />
              </dl>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Configuration" icon={<Settings2 className="h-4 w-4" />} />
            <CardBody>
              <dl className="space-y-3">
                <MetaItem label="Framework" value={deployment.build.frameworkLabel ?? '-'} />
                <MetaItem label="Install" value={deployment.build.installCommand ?? '-'} mono />
                <MetaItem label="Build" value={deployment.build.buildCommand ?? '-'} mono />
                <MetaItem label="Start" value={deployment.build.startCommand ?? '(static)'} mono />
                <MetaItem label="Root directory" value={deployment.build.rootDirectory ?? '.'} mono />
                <MetaItem label="Dockerfile" value={deployment.build.dockerfilePath ?? 'generated'} mono />
              </dl>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Runtime" icon={<Container className="h-4 w-4" />} />
            <CardBody>
              <dl className="space-y-3">
                <MetaItem label="Image" value={deployment.container.imageTag ?? '-'} mono />
                <MetaItem label="Container" value={deployment.container.containerId ?? '-'} mono />
                <MetaItem
                  label="Ports"
                  value={
                    deployment.container.hostPort
                      ? `${deployment.container.hostPort} → ${deployment.container.containerPort}`
                      : '-'
                  }
                  mono
                />
                <MetaItem label="Environment" value={deployment.environmentName ?? '-'} />
                <MetaItem
                  label="Duration"
                  value={formatDuration(deployment.timing.durationMs)}
                  mono
                />
                <MetaItem label="Created" value={absoluteTime(deployment.createdAt)} />
              </dl>
            </CardBody>
          </Card>
        </aside>
      </div>
    </div>
  );
}

function Header({ deployment, onChanged }: { deployment: Deployment; onChanged: () => void }) {
  const navigate = useNavigate();
  const toast = useToast();
  const cancel = useCancelDeployment();
  const redeploy = useRedeploy();
  const rollback = useRollback();
  const { data: candidates } = useRollbackCandidates(deployment.id);

  const inProgress = isDeploymentInProgress(deployment.status);

  return (
    <div className="space-y-4">
      <Link
        to={`/projects/${deployment.projectId}/deployments`}
        className="inline-flex items-center gap-1.5 text-[12px] text-content-secondary transition-colors hover:text-content-primary"
      >
        <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
        {deployment.projectName ?? 'Project'} deployments
      </Link>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-xl font-semibold tracking-tight text-content-primary">
              Deployment #{deployment.deploymentNumber}
            </h1>
            <DeploymentStatusBadge status={deployment.status} />
            {deployment.promoted ? <Badge tone="success">live</Badge> : null}
            <Badge>{humanizeEnum(deployment.triggerType)}</Badge>
          </div>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-content-secondary">
            <span className="inline-flex items-center gap-1.5">
              <GitBranch className="h-3.5 w-3.5" aria-hidden="true" />
              {deployment.branch}
            </span>
            {deployment.commit.shortSha ? (
              <span className="inline-flex items-center gap-1.5 font-mono text-[11px]">
                <GitCommitHorizontal className="h-3.5 w-3.5" aria-hidden="true" />
                {deployment.commit.shortSha}
              </span>
            ) : null}
            {deployment.createdBy ? (
              <span className="inline-flex items-center gap-1.5">
                <User className="h-3.5 w-3.5" aria-hidden="true" />
                {deployment.createdBy.name}
              </span>
            ) : null}
            <span>{relativeTime(deployment.createdAt)}</span>
            {deployment.timing.durationMs ? (
              <span className="tabular-nums">{formatDuration(deployment.timing.durationMs)}</span>
            ) : null}
          </div>
        </div>

        <div className="flex shrink-0 flex-wrap items-center gap-2">
          {deployment.url ? (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => window.open(deployment.url ?? '', '_blank', 'noopener')}
            >
              <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
              Open application
            </Button>
          ) : null}

          {inProgress ? (
            <Button
              variant="secondary"
              size="sm"
              loading={cancel.isPending}
              onClick={() =>
                cancel.mutate(deployment.id, {
                  onSuccess: () => {
                    toast.info('Cancellation requested', 'The pipeline stops at its next checkpoint.');
                    onChanged();
                  },
                  onError: (error) => toast.error('Could not cancel', error),
                })
              }
            >
              <Ban className="h-3.5 w-3.5" aria-hidden="true" />
              Cancel
            </Button>
          ) : (
            <Button
              variant="secondary"
              size="sm"
              loading={redeploy.isPending}
              onClick={() =>
                redeploy.mutate(deployment.id, {
                  onSuccess: (created) => {
                    toast.success(`Deployment #${created.deploymentNumber} queued`);
                    navigate(`/deployments/${created.id}`);
                  },
                  onError: (error) => toast.error('Redeploy failed', error),
                })
              }
            >
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
              Redeploy
            </Button>
          )}

          <Dialog>
            <DialogTrigger asChild>
              <Button variant="secondary" size="sm" disabled={!candidates || candidates.length === 0}>
                <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                Rollback
              </Button>
            </DialogTrigger>
            <DialogContent
              title="Roll back to a previous deployment"
              description="A new deployment is created that reuses the selected image. History is never rewritten, and the current container keeps serving until the new one is healthy."
            >
              <ul className="space-y-2">
                {(candidates ?? []).map((candidate) => (
                  <li
                    key={candidate.deploymentId}
                    className="flex items-center justify-between gap-3 rounded-lg border border-border-subtle px-3 py-2"
                  >
                    <div className="min-w-0">
                      <p className="text-[13px] text-content-primary">
                        #{candidate.deploymentNumber}
                        <span className="ml-2 font-mono text-[11px] text-content-muted">
                          {candidate.shortCommitSha ?? '-'}
                        </span>
                      </p>
                      <p className="truncate text-[12px] text-content-muted">
                        {candidate.commitMessage ?? 'No commit message'} · {relativeTime(candidate.createdAt)}
                      </p>
                    </div>
                    <Button
                      variant="primary"
                      size="sm"
                      disabled={!candidate.imageAvailable}
                      loading={rollback.isPending}
                      onClick={() =>
                        rollback.mutate(candidate.deploymentId, {
                          onSuccess: (created) => {
                            toast.success(`Rollback queued as #${created.deploymentNumber}`);
                            navigate(`/deployments/${created.id}`);
                          },
                          onError: (error) => toast.error('Rollback failed', error),
                        })
                      }
                    >
                      Roll back
                    </Button>
                  </li>
                ))}
              </ul>
            </DialogContent>
          </Dialog>
        </div>
      </div>
    </div>
  );
}
