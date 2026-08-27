import { GithubIcon } from '@/components/icons/GithubIcon';
import { Plus, Search } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useProjects } from '@/api/projects';
import { buttonVariants } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { Input } from '@/components/ui/form';
import { PageHeader } from '@/components/ui/page';
import { ProjectCard } from '@/features/dashboard/ProjectCard';

/** Every project the caller can see, with client side filtering over the loaded page. */
export function ProjectsListPage() {
  const { data, isLoading, error, refetch } = useProjects();
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    const items = data?.items ?? [];
    if (!needle) return items;
    return items.filter(
      (project) =>
        project.name.toLowerCase().includes(needle) ||
        project.repository.fullName.toLowerCase().includes(needle) ||
        (project.frameworkLabel ?? '').toLowerCase().includes(needle),
    );
  }, [data, search]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Projects"
        description={
          data ? `${data.totalItems} imported ${data.totalItems === 1 ? 'repository' : 'repositories'}` : ' '
        }
        actions={
          <div className="flex w-full items-center gap-2 sm:w-auto">
          <div className="relative">
            <Search
              className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-content-muted"
              aria-hidden="true"
            />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Filter projects"
              className="w-full pl-9 sm:w-56"
              aria-label="Filter projects"
            />
          </div>
          <Link to="/projects/import" className={buttonVariants({ variant: 'primary' })}>
            <Plus className="h-3.5 w-3.5" aria-hidden="true" />
            Import
          </Link>
          </div>
        }
      />

      {isLoading ? (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={index} className="h-40 w-full rounded-xl" />
          ))}
        </div>
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : filtered.length === 0 ? (
        <Card>
          <EmptyState
            icon={GithubIcon}
            title={search ? 'No projects match that filter' : 'No projects yet'}
            description={
              search
                ? 'Try a different term.'
                : 'Import a GitHub repository and DeployLane will build, deploy and monitor it.'
            }
            action={
              search ? null : (
                <Link to="/projects/import" className={buttonVariants({ variant: 'primary' })}>
                  <Plus className="h-4 w-4" aria-hidden="true" />
                  Import repository
                </Link>
              )
            }
          />
        </Card>
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </div>
      )}
    </div>
  );
}
