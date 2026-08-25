import { ScrollText } from 'lucide-react';
import { useOutletContext } from 'react-router-dom';
import { downloadDeploymentLogs, useDeployments } from '@/api/deployments';
import { Card, CardHeader } from '@/components/ui/card';
import { EmptyState, Skeleton } from '@/components/ui/feedback';
import { DeploymentStatusBadge } from '@/components/DeploymentStatusBadge';
import { LogTerminal } from '@/features/logs/LogTerminal';
import { useDeploymentStream } from '@/features/deployments/useDeploymentStream';
import { relativeTime } from '@/lib/utils';
import type { Project } from '@/types/api';

/**
 * Project level log view: the most recent deployment's output.
 *
 * A shortcut for the common case ("what is happening right now") without navigating into a specific
 * deployment first.
 */
export function ProjectLogsPage() {
  const { project } = useOutletContext<{ project: Project }>();
  const { data: deployments, isLoading } = useDeployments(project.id);
  const latest = deployments?.items[0];
  const stream = useDeploymentStream(latest?.id);

  if (isLoading) {
    return <Skeleton className="h-96 w-full" />;
  }

  if (!latest) {
    return (
      <Card>
        <EmptyState
          icon={ScrollText}
          title="No logs yet"
          description="Logs appear as soon as the first deployment starts."
        />
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader
          title={`Deployment #${latest.deploymentNumber}`}
          description={`${latest.branch} · ${relativeTime(latest.createdAt)}`}
          icon={<ScrollText className="h-4 w-4" />}
          actions={<DeploymentStatusBadge status={latest.status} />}
        />
      </Card>

      <LogTerminal
        entries={stream.logs}
        socketState={stream.socketState}
        onClear={stream.clear}
        onDownload={() => downloadDeploymentLogs(latest.id)}
      />
    </div>
  );
}
