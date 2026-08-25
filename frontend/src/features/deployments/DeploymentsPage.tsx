import { Rocket } from 'lucide-react';
import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { useDeployments } from '@/api/deployments';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardFooter, CardHeader } from '@/components/ui/card';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/ui/feedback';
import { Select } from '@/components/ui/form';
import { DeploymentRow } from '@/features/deployments/DeploymentRow';
import type { Project } from '@/types/api';

/** Full deployment history for a project, filterable by environment and paginated. */
export function DeploymentsPage() {
  const { project } = useOutletContext<{ project: Project }>();
  const [environmentId, setEnvironmentId] = useState<string>('');
  const [page, setPage] = useState(0);
  const { data, isLoading, error, refetch, isPlaceholderData } = useDeployments(
    project.id,
    environmentId || null,
    page,
  );

  return (
    <Card>
      <CardHeader
        title="Deployment history"
        description={data ? `${data.totalItems} deployments` : undefined}
        icon={<Rocket className="h-4 w-4" />}
        actions={
          project.environments.length > 1 ? (
            <Select
              value={environmentId}
              onChange={(event) => {
                setEnvironmentId(event.target.value);
                setPage(0);
              }}
              aria-label="Filter by environment"
              className="h-8 w-40 text-[12px]"
            >
              <option value="">All environments</option>
              {project.environments.map((environment) => (
                <option key={environment.id} value={environment.id}>
                  {environment.name}
                </option>
              ))}
            </Select>
          ) : null
        }
      />

      <CardBody className="p-0">
        {isLoading ? (
          <SkeletonRows rows={6} className="p-4" />
        ) : error ? (
          <div className="p-4">
            <ErrorState error={error} onRetry={() => void refetch()} />
          </div>
        ) : !data || data.items.length === 0 ? (
          <EmptyState
            icon={Rocket}
            title="No deployments yet"
            description="Deploy the selected branch to get started."
          />
        ) : (
          <ul className={isPlaceholderData ? 'divide-y divide-border-subtle opacity-60' : 'divide-y divide-border-subtle'}>
            {data.items.map((deployment) => (
              <DeploymentRow key={deployment.id} deployment={deployment} />
            ))}
          </ul>
        )}
      </CardBody>

      {data && data.totalPages > 1 ? (
        <CardFooter className="flex items-center justify-between">
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              disabled={!data.hasPrevious}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
            >
              Previous
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={!data.hasNext}
              onClick={() => setPage((current) => current + 1)}
            >
              Next
            </Button>
          </div>
        </CardFooter>
      ) : null}
    </Card>
  );
}
