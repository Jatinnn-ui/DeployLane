import { GithubIcon } from '@/components/icons/GithubIcon';
import {
  ArrowLeft,
  ArrowRight,
  Check,
  GitBranch,
  Lock,
  Plus,
  Search,
  Sparkles,
  Trash2,
  TriangleAlert,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useBranches, useDetectFramework, useRepositories } from '@/api/github';
import { useImportProject } from '@/api/projects';
import { useWorkspaces } from '@/api/platform';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { EmptyState, ErrorState, InlineNotice, Skeleton, Spinner } from '@/components/ui/feedback';
import { Field, Input, Select, Switch } from '@/components/ui/form';
import { useToast } from '@/components/ui/toast';
import { cn, relativeTime } from '@/lib/utils';
import type { DetectionResult, RepositorySummary } from '@/types/api';

type Step = 'repository' | 'configure';

/**
 * Repository import: pick a repository, pick a branch, confirm what DeployLane detected, add variables.
 *
 * Detection runs against the real branch through the GitHub API before anything is created, and every field
 * it fills in stays editable. The confidence and the evidence are shown rather than hidden, because a user
 * should be able to tell the difference between "we know" and "we guessed".
 */
export function ImportProjectPage() {
  const [step, setStep] = useState<Step>('repository');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [repository, setRepository] = useState<RepositorySummary | null>(null);
  const [branch, setBranch] = useState<string>('');
  const [detection, setDetection] = useState<DetectionResult | null>(null);

  const { data: workspaces } = useWorkspaces();
  const { data: repositories, isLoading, error, refetch } = useRepositories(debouncedSearch, 1);
  const { data: branches, isLoading: branchesLoading } = useBranches(
    repository?.owner,
    repository?.name,
  );
  const detect = useDetectFramework();

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [search]);

  const selectRepository = (candidate: RepositorySummary) => {
    setRepository(candidate);
    setBranch(candidate.defaultBranch);
    setDetection(null);
  };

  const runDetection = (targetBranch: string) => {
    if (!repository) return;
    detect.mutate(
      { owner: repository.owner, repo: repository.name, ref: targetBranch },
      { onSuccess: setDetection },
    );
  };

  const proceed = () => {
    if (!repository || !branch) return;
    setStep('configure');
    runDetection(branch);
  };

  if (step === 'configure' && repository) {
    return (
      <ConfigureStep
        repository={repository}
        branch={branch}
        branches={branches?.map((item) => item.name) ?? [repository.defaultBranch]}
        detection={detection}
        detecting={detect.isPending}
        detectionError={detect.error}
        workspaceId={workspaces?.[0]?.id ?? null}
        onBack={() => setStep('repository')}
        onBranchChange={(next) => {
          setBranch(next);
          setDetection(null);
          runDetection(next);
        }}
        onRetryDetection={() => runDetection(branch)}
      />
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <Link
          to="/dashboard"
          className="inline-flex items-center gap-1.5 text-[12px] text-content-secondary transition-colors hover:text-content-primary"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          Back to overview
        </Link>
        <h1 className="mt-3 text-xl font-semibold tracking-tight text-content-primary">
          Import a GitHub repository
        </h1>
        <p className="mt-1 text-[13px] text-content-secondary">
          DeployLane reads the repository to work out how to build it. Nothing is deployed yet.
        </p>
      </div>

      <Card>
        <CardHeader
          title="Select a repository"
          description={
            repositories
              ? `${repositories.total} accessible ${repositories.total === 1 ? 'repository' : 'repositories'}`
              : undefined
          }
          icon={<GithubIcon className="h-4 w-4" />}
        />
        <CardBody className="space-y-4">
          <div className="relative">
            <Search
              className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-content-muted"
              aria-hidden="true"
            />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search by name, description or language"
              className="pl-9"
              aria-label="Search repositories"
            />
          </div>

          {isLoading ? (
            <div className="space-y-2">
              {Array.from({ length: 5 }).map((_, index) => (
                <Skeleton key={index} className="h-14 w-full" />
              ))}
            </div>
          ) : error ? (
            <ErrorState error={error} onRetry={() => void refetch()} />
          ) : !repositories || repositories.repositories.length === 0 ? (
            <EmptyState
              icon={GithubIcon}
              title={debouncedSearch ? 'No repositories match that search' : 'No repositories found'}
              description={
                debouncedSearch
                  ? 'Try a different term. Search covers the repository name, description and language.'
                  : 'DeployLane can only see repositories your GitHub grant covers. Reconnect GitHub if a repository is missing.'
              }
            />
          ) : (
            <ul className="divide-y divide-border-subtle overflow-hidden rounded-lg border border-border-subtle">
              {repositories.repositories.map((candidate) => {
                const selected = repository?.id === candidate.id;
                return (
                  <li key={candidate.id}>
                    <button
                      type="button"
                      onClick={() => selectRepository(candidate)}
                      className={cn(
                        'flex w-full items-center gap-3 px-3 py-2.5 text-left transition-colors',
                        selected ? 'bg-accent-soft' : 'hover:bg-surface-hover',
                      )}
                      aria-pressed={selected}
                    >
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="truncate text-[13px] font-medium text-content-primary">
                            {candidate.fullName}
                          </span>
                          {candidate.isPrivate ? (
                            <Lock className="h-3 w-3 shrink-0 text-content-muted" aria-label="private" />
                          ) : null}
                          {candidate.archived ? <Badge tone="warning">archived</Badge> : null}
                        </div>
                        <p className="mt-0.5 truncate text-[12px] text-content-muted">
                          {candidate.description ?? 'No description'}
                        </p>
                      </div>
                      <div className="hidden shrink-0 items-center gap-3 text-[11px] text-content-muted sm:flex">
                        {candidate.language ? <span>{candidate.language}</span> : null}
                        <span>{relativeTime(candidate.pushedAt ?? candidate.updatedAt)}</span>
                      </div>
                      {selected ? (
                        <Check className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
                      ) : null}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </CardBody>
      </Card>

      {repository ? (
        <Card>
          <CardHeader
            title="Select a branch"
            description={`Deployments will track this branch of ${repository.fullName}`}
            icon={<GitBranch className="h-4 w-4" />}
          />
          <CardBody className="space-y-4">
            {branchesLoading ? (
              <div className="flex items-center gap-2 text-[13px] text-content-secondary">
                <Spinner />
                Loading branches...
              </div>
            ) : (
              <Field label="Branch" htmlFor="branch">
                <Select
                  id="branch"
                  value={branch}
                  onChange={(event) => setBranch(event.target.value)}
                >
                  {(branches ?? []).map((item) => (
                    <option key={item.name} value={item.name}>
                      {item.name}
                      {item.name === repository.defaultBranch ? ' (default)' : ''}
                    </option>
                  ))}
                </Select>
              </Field>
            )}

            {repository.archived ? (
              <InlineNotice tone="warning">
                This repository is archived on GitHub. Unarchive it before importing.
              </InlineNotice>
            ) : null}

            <div className="flex justify-end">
              <Button
                variant="primary"
                onClick={proceed}
                disabled={!branch || repository.archived}
              >
                Continue
                <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
              </Button>
            </div>
          </CardBody>
        </Card>
      ) : null}
    </div>
  );
}

interface VariableDraft {
  id: number;
  key: string;
  value: string;
}

function ConfigureStep({
  repository,
  branch,
  branches,
  detection,
  detecting,
  detectionError,
  workspaceId,
  onBack,
  onBranchChange,
  onRetryDetection,
}: {
  repository: RepositorySummary;
  branch: string;
  branches: string[];
  detection: DetectionResult | null;
  detecting: boolean;
  detectionError: unknown;
  workspaceId: string | null;
  onBack: () => void;
  onBranchChange: (branch: string) => void;
  onRetryDetection: () => void;
}) {
  const navigate = useNavigate();
  const toast = useToast();
  const importProject = useImportProject();

  const [name, setName] = useState(repository.name);
  const [rootDirectory, setRootDirectory] = useState('');
  const [installCommand, setInstallCommand] = useState('');
  const [buildCommand, setBuildCommand] = useState('');
  const [startCommand, setStartCommand] = useState('');
  const [port, setPort] = useState<string>('');
  const [healthCheckPath, setHealthCheckPath] = useState('/');
  const [autoDeploy, setAutoDeploy] = useState(false);
  const [variables, setVariables] = useState<VariableDraft[]>([]);
  const [bulkText, setBulkText] = useState('');

  // Detection fills the form once; anything the user then types wins.
  useEffect(() => {
    if (!detection) return;
    setInstallCommand(detection.installCommand ?? '');
    setBuildCommand(detection.buildCommand ?? '');
    setStartCommand(detection.startCommand ?? '');
    setPort(String(detection.port));
  }, [detection]);

  const confidencePercent = detection ? Math.round(detection.confidence * 100) : 0;
  const parsedVariables = useMemo(() => parseBulk(bulkText), [bulkText]);

  const submit = () => {
    const allVariables = [
      ...variables.filter((variable) => variable.key.trim() && variable.value.trim()),
      ...parsedVariables,
    ];

    importProject.mutate(
      {
        workspaceId,
        repositoryOwner: repository.owner,
        repositoryName: repository.name,
        branch,
        name: name.trim() || repository.name,
        framework: detection?.framework ?? null,
        autoDeployEnabled: autoDeploy,
        buildConfig: {
          rootDirectory: rootDirectory.trim() || null,
          installCommand: installCommand.trim() || null,
          buildCommand: buildCommand.trim() || null,
          startCommand: startCommand.trim() || null,
          port: port ? Number(port) : null,
          healthCheckPath: healthCheckPath.trim() || '/',
        },
        environmentVariables: allVariables.map(({ key, value }) => ({ key, value })),
      },
      {
        onSuccess: (project) => {
          toast.success(`${project.name} imported`, 'Deploy it whenever you are ready.');
          navigate(`/projects/${project.id}`);
        },
        onError: (error) => toast.error('Import failed', error),
      },
    );
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-1.5 text-[12px] text-content-secondary transition-colors hover:text-content-primary"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          Choose a different repository
        </button>
        <h1 className="mt-3 text-xl font-semibold tracking-tight text-content-primary">
          Configure {repository.name}
        </h1>
        <p className="mt-1 flex flex-wrap items-center gap-2 text-[13px] text-content-secondary">
          <span className="font-mono text-[12px]">{repository.fullName}</span>
          <span aria-hidden="true">·</span>
          <span className="font-mono text-[12px]">{branch}</span>
        </p>
      </div>

      <Card>
        <CardHeader
          title="Detected configuration"
          description="Everything below is editable. DeployLane only suggests."
          icon={<Sparkles className="h-4 w-4" />}
          actions={
            detection ? (
              <Badge tone={confidencePercent >= 80 ? 'success' : confidencePercent >= 60 ? 'warning' : 'danger'}>
                {confidencePercent}% confidence
              </Badge>
            ) : null
          }
        />
        <CardBody className="space-y-4">
          {detecting ? (
            <div className="flex items-center gap-2 text-[13px] text-content-secondary">
              <Spinner />
              Inspecting {repository.fullName}@{branch}...
            </div>
          ) : detectionError ? (
            <ErrorState error={detectionError} onRetry={onRetryDetection} />
          ) : detection ? (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone="accent">{detection.framework.replace(/_/g, ' ')}</Badge>
                <Badge>{detection.runtime}</Badge>
                {detection.packageManager !== 'NONE' ? (
                  <Badge>{detection.packageManager.toLowerCase()}</Badge>
                ) : null}
                {detection.dockerfilePath ? <Badge tone="info">Dockerfile</Badge> : null}
              </div>

              {detection.evidence.length > 0 ? (
                <details className="rounded-lg border border-border-subtle bg-canvas px-3 py-2">
                  <summary className="cursor-pointer text-[12px] text-content-secondary">
                    Why DeployLane thinks this ({detection.evidence.length} signals)
                  </summary>
                  <ul className="mt-2 space-y-1 text-[12px] text-content-muted">
                    {detection.evidence.map((item, index) => (
                      <li key={index} className="font-mono">
                        · {item}
                      </li>
                    ))}
                  </ul>
                </details>
              ) : null}

              {detection.warnings.map((warning, index) => (
                <InlineNotice key={index} tone="warning">
                  <span className="flex items-start gap-2">
                    <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                    {warning}
                  </span>
                </InlineNotice>
              ))}
            </>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Project name" htmlFor="name">
              <Input id="name" value={name} onChange={(event) => setName(event.target.value)} />
            </Field>
            <Field label="Branch" htmlFor="configure-branch">
              <Select
                id="configure-branch"
                value={branch}
                onChange={(event) => onBranchChange(event.target.value)}
              >
                {branches.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </Select>
            </Field>
            <Field
              label="Root directory"
              htmlFor="rootDirectory"
              hint="For monorepos, e.g. apps/web. Leave empty for the repository root."
            >
              <Input
                id="rootDirectory"
                value={rootDirectory}
                onChange={(event) => setRootDirectory(event.target.value)}
                placeholder="apps/web"
              />
            </Field>
            <Field label="Port" htmlFor="port" hint="The port your application listens on inside the container.">
              <Input
                id="port"
                type="number"
                min={1}
                max={65535}
                value={port}
                onChange={(event) => setPort(event.target.value)}
              />
            </Field>
            <Field label="Install command" htmlFor="installCommand" className="sm:col-span-2">
              <Input
                id="installCommand"
                value={installCommand}
                onChange={(event) => setInstallCommand(event.target.value)}
                className="font-mono"
                placeholder="npm ci"
              />
            </Field>
            <Field label="Build command" htmlFor="buildCommand" className="sm:col-span-2">
              <Input
                id="buildCommand"
                value={buildCommand}
                onChange={(event) => setBuildCommand(event.target.value)}
                className="font-mono"
                placeholder="npm run build"
              />
            </Field>
            <Field
              label="Start command"
              htmlFor="startCommand"
              className="sm:col-span-2"
              hint="Leave empty for static sites - the build output is served by nginx."
            >
              <Input
                id="startCommand"
                value={startCommand}
                onChange={(event) => setStartCommand(event.target.value)}
                className="font-mono"
                placeholder="npm start"
              />
            </Field>
            <Field label="Health check path" htmlFor="healthCheckPath" className="sm:col-span-2">
              <Input
                id="healthCheckPath"
                value={healthCheckPath}
                onChange={(event) => setHealthCheckPath(event.target.value)}
                className="font-mono"
                placeholder="/"
              />
            </Field>
          </div>

          <div className="border-t border-border-subtle pt-4">
            <Switch
              id="autoDeploy"
              checked={autoDeploy}
              onCheckedChange={setAutoDeploy}
              label="Deploy automatically on push"
              description="Requires a GitHub webhook pointing at this server. See docs/local-development.md."
            />
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader
          title="Environment variables"
          description="Added before the first deployment so it can succeed straight away. Values are encrypted before storage."
        />
        <CardBody className="space-y-4">
          {variables.map((variable, index) => (
            <div key={variable.id} className="flex items-end gap-2">
              <Field label={index === 0 ? 'Name' : undefined} className="flex-1">
                <Input
                  value={variable.key}
                  onChange={(event) =>
                    setVariables((current) =>
                      current.map((item) =>
                        item.id === variable.id ? { ...item, key: event.target.value } : item,
                      ),
                    )
                  }
                  placeholder="DATABASE_URL"
                  className="font-mono"
                  aria-label="Variable name"
                />
              </Field>
              <Field label={index === 0 ? 'Value' : undefined} className="flex-1">
                <Input
                  type="password"
                  value={variable.value}
                  onChange={(event) =>
                    setVariables((current) =>
                      current.map((item) =>
                        item.id === variable.id ? { ...item, value: event.target.value } : item,
                      ),
                    )
                  }
                  placeholder="postgres://..."
                  className="font-mono"
                  aria-label="Variable value"
                />
              </Field>
              <Button
                variant="ghost"
                size="icon"
                onClick={() =>
                  setVariables((current) => current.filter((item) => item.id !== variable.id))
                }
                aria-label="Remove variable"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          ))}

          <Button
            variant="secondary"
            size="sm"
            onClick={() =>
              setVariables((current) => [...current, { id: Date.now(), key: '', value: '' }])
            }
          >
            <Plus className="h-3.5 w-3.5" aria-hidden="true" />
            Add variable
          </Button>

          <Field
            label="Or paste a .env block"
            htmlFor="bulk"
            hint={
              parsedVariables.length > 0
                ? `${parsedVariables.length} variable${parsedVariables.length === 1 ? '' : 's'} will be created`
                : 'KEY=value per line. Comments and quotes are handled.'
            }
          >
            <textarea
              id="bulk"
              value={bulkText}
              onChange={(event) => setBulkText(event.target.value)}
              rows={4}
              spellCheck={false}
              className="w-full rounded-lg border border-border-strong bg-canvas px-3 py-2 font-mono text-[12px] text-content-primary placeholder:text-content-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              placeholder={'DATABASE_URL=postgres://...\nJWT_SECRET=...'}
            />
          </Field>
        </CardBody>
      </Card>

      <div className="flex items-center justify-between gap-3">
        <Button variant="ghost" onClick={onBack}>
          Back
        </Button>
        <Button
          variant="primary"
          onClick={submit}
          loading={importProject.isPending}
          disabled={detecting || !port}
        >
          Import project
        </Button>
      </div>
    </div>
  );
}

/** Mirrors the backend's tolerant .env parsing so the preview count matches what will be created. */
function parseBulk(text: string): { key: string; value: string }[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => (line.startsWith('export ') ? line.slice(7).trim() : line))
    .map((line) => {
      const separator = line.indexOf('=');
      if (separator <= 0) return null;
      const key = line.slice(0, separator).trim();
      let value = line.slice(separator + 1).trim();
      if (
        value.length >= 2 &&
        ((value.startsWith('"') && value.endsWith('"')) ||
          (value.startsWith("'") && value.endsWith("'")))
      ) {
        value = value.slice(1, -1);
      }
      return /^[A-Za-z_][A-Za-z0-9_]*$/.test(key) ? { key, value } : null;
    })
    .filter((entry): entry is { key: string; value: string } => entry !== null);
}
