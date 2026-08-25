import { Activity } from 'lucide-react';
import { useState } from 'react';
import { useActivityFeed } from '@/api/platform';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardFooter, CardHeader } from '@/components/ui/card';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/ui/feedback';
import { ActivityList } from '@/features/activity/ActivityList';

/** Workspace-wide audit trail. Append only on the backend, so this is a faithful history. */
export function ActivityPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading, error, refetch } = useActivityFeed({ kind: 'global' }, page);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold tracking-tight text-content-primary">Activity</h1>
        <p className="mt-1 text-[13px] text-content-secondary">
          Imports, deployments, rollbacks and configuration changes across your workspaces.
        </p>
      </div>

      <Card>
        <CardHeader
          title="Recent events"
          description={data ? `${data.totalItems} recorded` : undefined}
          icon={<Activity className="h-4 w-4" />}
        />
        <CardBody className="p-0">
          {isLoading ? (
            <SkeletonRows rows={8} className="p-4" />
          ) : error ? (
            <div className="p-4">
              <ErrorState error={error} onRetry={() => void refetch()} />
            </div>
          ) : !data || data.items.length === 0 ? (
            <EmptyState
              icon={Activity}
              title="No activity yet"
              description="Import a repository or deploy something to start the trail."
            />
          ) : (
            <ActivityList entries={data.items} />
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
    </div>
  );
}
