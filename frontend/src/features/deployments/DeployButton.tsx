import { Rocket } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useCreateDeployment } from '@/api/deployments';
import { Button } from '@/components/ui/button';
import { Tooltip } from '@/components/ui/overlay';
import { useToast } from '@/components/ui/toast';
import type { Project } from '@/types/api';

/**
 * The primary action of the whole product.
 *
 * Enforces the same rules the backend does - permission, project status, configuration completeness - but
 * as a disabled button with an explanation rather than letting the user discover a 409 the hard way. The
 * backend remains the authority; this is purely so the UI does not lie about what is possible.
 */
export function DeployButton({
  project,
  environmentId,
  size = 'sm',
}: {
  project: Project;
  environmentId?: string;
  size?: 'sm' | 'md';
}) {
  const deploy = useCreateDeployment(project.id);
  const navigate = useNavigate();
  const toast = useToast();

  const canDeploy = project.permissions.includes('DEPLOY');
  const blockedReason = !canDeploy
    ? 'Your workspace role does not allow deploying'
    : project.status === 'ARCHIVED'
      ? 'This project is archived'
      : project.status === 'PAUSED'
        ? 'This project is paused'
        : !project.framework
          ? 'Set a framework in project settings first'
          : !project.buildConfig.port
            ? 'Set the application port in project settings first'
            : project.environments.length === 0
              ? 'This project has no environment to deploy to'
              : null;

  const handleDeploy = () => {
    deploy.mutate(environmentId ? { environmentId } : undefined, {
      onSuccess: (deployment) => {
        toast.success(
          `Deployment #${deployment.deploymentNumber} queued`,
          'Following the build live.',
        );
        navigate(`/deployments/${deployment.id}`);
      },
      onError: (error) => toast.error('Could not start the deployment', error),
    });
  };

  return (
    <Tooltip content={blockedReason}>
      <span>
        <Button
          variant="primary"
          size={size}
          onClick={handleDeploy}
          loading={deploy.isPending}
          disabled={Boolean(blockedReason)}
        >
          {!deploy.isPending ? <Rocket className="h-3.5 w-3.5" aria-hidden="true" /> : null}
          Deploy
        </Button>
      </span>
    </Tooltip>
  );
}
