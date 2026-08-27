import { Activity } from 'lucide-react';
import { useState } from 'react';
import { useActivityFeed } from '@/api/platform';
import { Card, CardBody, CardFooter, CardHeader } from '@/components/ui/card';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/ui/feedback';
import { PageHeader, Pagination } from '@/components/ui/page';
import { ActivityList } from '@/features/activity/ActivityList';

/** Workspace-wide audit trail. Append only on the backend, so this is a faithful history. */
export function ActivityPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading, error, refetch } = useActivityFeed({ kind: 'global' }, page);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Activity"
        description="Imports, deployments, rollbacks and configuration changes across your workspaces."
      />

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
          <CardFooter>
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              hasPrevious={data.hasPrevious}
              hasNext={data.hasNext}
              onPrevious={() => setPage((current) => Math.max(0, current - 1))}
              onNext={() => setPage((current) => current + 1)}
            />
          </CardFooter>
        ) : null}
      </Card>
    </div>
  );
}
