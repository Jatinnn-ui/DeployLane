import { Eye, KeyRound, Lock, Plus, RotateCw, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import {
  useBulkVariables,
  useCreateVariable,
  useDeleteVariable,
  useEnvironmentVariables,
  useUpdateEnvironment,
  useUpdateVariable,
} from '@/api/projects';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { EmptyState, InlineNotice, SkeletonRows } from '@/components/ui/feedback';
import { Field, Input, Switch, Textarea } from '@/components/ui/form';
import { Dialog, DialogClose, DialogContent, DialogTrigger, Tooltip } from '@/components/ui/overlay';
import { useToast } from '@/components/ui/toast';
import { absoluteTime, relativeTime } from '@/lib/utils';
import type { EnvironmentResponse, Project } from '@/types/api';

/**
 * Environment variable management.
 *
 * The masking here is not a UI trick: the API genuinely never returns a value. There is no "reveal"
 * button because there is nothing to reveal - a value can only be replaced. That is the whole point of
 * encrypting at rest with a key the database does not have.
 */
export function EnvironmentPage() {
  const { project } = useOutletContext<{ project: Project }>();
  const [selectedId, setSelectedId] = useState(project.environments[0]?.id ?? '');
  const environment = project.environments.find((item) => item.id === selectedId);

  if (!environment) {
    return (
      <Card>
        <EmptyState icon={KeyRound} title="This project has no environments" />
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      {project.environments.length > 1 ? (
        <div className="flex flex-wrap gap-2">
          {project.environments.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setSelectedId(item.id)}
              className={
                item.id === selectedId
                  ? 'rounded-lg border border-accent-border bg-accent-soft px-3 py-1.5 text-[13px] text-content-primary'
                  : 'rounded-lg border border-border-subtle px-3 py-1.5 text-[13px] text-content-secondary transition-colors hover:bg-surface-hover'
              }
            >
              {item.name}
            </button>
          ))}
        </div>
      ) : null}

      <VariablesCard environment={environment} canManage={project.permissions.includes('MANAGE_VARIABLES')} />
      <AutoDeployCard
        project={project}
        environment={environment}
        canManage={project.permissions.includes('MANAGE_ENVIRONMENT')}
      />
    </div>
  );
}

function VariablesCard({
  environment,
  canManage,
}: {
  environment: EnvironmentResponse;
  canManage: boolean;
}) {
  const { data, isLoading } = useEnvironmentVariables(environment.id);
  const createVariable = useCreateVariable(environment.id);
  const updateVariable = useUpdateVariable(environment.id);
  const deleteVariable = useDeleteVariable(environment.id);
  const bulk = useBulkVariables(environment.id);
  const toast = useToast();

  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');
  const [bulkText, setBulkText] = useState('');
  const [replaceExisting, setReplaceExisting] = useState(false);
  const [rotating, setRotating] = useState<{ id: string; key: string } | null>(null);
  const [rotateValue, setRotateValue] = useState('');

  const variables = data?.variables ?? [];

  const submitNew = () => {
    createVariable.mutate(
      { key: newKey.trim(), value: newValue },
      {
        onSuccess: () => {
          toast.success(`${newKey.trim()} saved`, 'Encrypted before storage. Redeploy to apply it.');
          setNewKey('');
          setNewValue('');
        },
        onError: (error) => toast.error('Could not save the variable', error),
      },
    );
  };

  return (
    <Card>
      <CardHeader
        title="Environment variables"
        description={`${variables.length} configured for ${environment.name}. Values are encrypted with AES-256-GCM and never returned by the API.`}
        icon={<KeyRound className="h-4 w-4" />}
        actions={
          canManage ? (
            <Dialog>
              <DialogTrigger asChild>
                <Button variant="secondary" size="sm">
                  Bulk edit
                </Button>
              </DialogTrigger>
              <DialogContent
                title="Paste a .env block"
                description="Comments, blank lines, 'export ' prefixes and quoted values are all accepted. Invalid lines are reported instead of failing the whole paste."
                footer={
                  <>
                    <DialogClose asChild>
                      <Button variant="ghost" size="sm">
                        Cancel
                      </Button>
                    </DialogClose>
                    <Button
                      variant="primary"
                      size="sm"
                      loading={bulk.isPending}
                      onClick={() =>
                        bulk.mutate(
                          { content: bulkText, replaceExisting },
                          {
                            onSuccess: (result) => {
                              toast.success(
                                `${result.created} created, ${result.updated} updated`,
                                result.skipped.length > 0
                                  ? `Skipped: ${result.skipped.join('; ')}`
                                  : 'Redeploy to apply the changes.',
                              );
                              setBulkText('');
                            },
                            onError: (error) => toast.error('Bulk update failed', error),
                          },
                        )
                      }
                    >
                      Apply
                    </Button>
                  </>
                }
              >
                <div className="space-y-4">
                  <Textarea
                    value={bulkText}
                    onChange={(event) => setBulkText(event.target.value)}
                    rows={10}
                    spellCheck={false}
                    placeholder={'DATABASE_URL=postgres://...\nJWT_SECRET=...\n# comments are ignored'}
                    aria-label="Environment variables"
                  />
                  <Switch
                    checked={replaceExisting}
                    onCheckedChange={setReplaceExisting}
                    label="Replace existing"
                    description="Delete variables that are not present in the pasted block."
                  />
                </div>
              </DialogContent>
            </Dialog>
          ) : null
        }
      />

      <CardBody className="space-y-4">
        {isLoading ? (
          <SkeletonRows rows={3} />
        ) : variables.length === 0 ? (
          <EmptyState
            icon={Lock}
            title="No variables yet"
            description="Add the configuration your application reads at runtime. DATABASE_URL, API keys, feature flags."
            className="py-8"
          />
        ) : (
          <ul className="divide-y divide-border-subtle overflow-hidden rounded-lg border border-border-subtle">
            {variables.map((variable) => (
              <li key={variable.id} className="flex items-center gap-3 px-3 py-2.5">
                <span className="w-56 shrink-0 truncate font-mono text-[12px] text-content-primary">
                  {variable.key}
                </span>
                <Tooltip content="Values are never returned by the API - only replaced">
                  <span className="flex min-w-0 flex-1 items-center gap-2 font-mono text-[13px] tracking-widest text-content-muted">
                    ••••••••••••
                    <Eye className="h-3 w-3 opacity-40" aria-hidden="true" />
                  </span>
                </Tooltip>
                <span className="hidden shrink-0 text-[11px] text-content-muted sm:block">
                  updated {relativeTime(variable.updatedAt)}
                </span>
                {canManage ? (
                  <div className="flex shrink-0 items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Rotate ${variable.key}`}
                      onClick={() => {
                        setRotating({ id: variable.id, key: variable.key });
                        setRotateValue('');
                      }}
                    >
                      <RotateCw className="h-3.5 w-3.5" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Delete ${variable.key}`}
                      onClick={() =>
                        deleteVariable.mutate(variable.id, {
                          onSuccess: () => toast.success(`${variable.key} deleted`),
                          onError: (error) => toast.error('Could not delete the variable', error),
                        })
                      }
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}

        {canManage ? (
          <div className="grid gap-3 border-t border-border-subtle pt-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
            <Field label="Name" htmlFor="new-key">
              <Input
                id="new-key"
                value={newKey}
                onChange={(event) => setNewKey(event.target.value.toUpperCase())}
                placeholder="DATABASE_URL"
                className="font-mono"
              />
            </Field>
            <Field label="Value" htmlFor="new-value">
              <Input
                id="new-value"
                type="password"
                value={newValue}
                onChange={(event) => setNewValue(event.target.value)}
                placeholder="postgres://..."
                className="font-mono"
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && newKey && newValue) submitNew();
                }}
              />
            </Field>
            <Button
              variant="primary"
              onClick={submitNew}
              loading={createVariable.isPending}
              disabled={!newKey.trim() || !newValue}
            >
              <Plus className="h-3.5 w-3.5" aria-hidden="true" />
              Add
            </Button>
          </div>
        ) : (
          <InlineNotice>Your workspace role does not allow changing variables.</InlineNotice>
        )}

        <p className="text-[11px] leading-relaxed text-content-muted">
          Variables are injected into the container at start time. Changing one does not affect a running
          container - redeploy to apply it.
        </p>
      </CardBody>

      <Dialog open={rotating !== null} onOpenChange={(open) => !open && setRotating(null)}>
        <DialogContent
          title={`Rotate ${rotating?.key ?? ''}`}
          description="The previous value cannot be recovered. It is replaced immediately, and applied on the next deployment."
          footer={
            <>
              <Button variant="ghost" size="sm" onClick={() => setRotating(null)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                size="sm"
                loading={updateVariable.isPending}
                disabled={!rotateValue}
                onClick={() =>
                  rotating &&
                  updateVariable.mutate(
                    { variableId: rotating.id, value: rotateValue },
                    {
                      onSuccess: () => {
                        toast.success(`${rotating.key} rotated`);
                        setRotating(null);
                      },
                      onError: (error) => toast.error('Rotation failed', error),
                    },
                  )
                }
              >
                Rotate
              </Button>
            </>
          }
        >
          <Field label="New value" htmlFor="rotate-value">
            <Input
              id="rotate-value"
              type="password"
              value={rotateValue}
              onChange={(event) => setRotateValue(event.target.value)}
              className="font-mono"
              autoFocus
            />
          </Field>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

function AutoDeployCard({
  project,
  environment,
  canManage,
}: {
  project: Project;
  environment: EnvironmentResponse;
  canManage: boolean;
}) {
  const updateEnvironment = useUpdateEnvironment(project.id);
  const toast = useToast();

  return (
    <Card>
      <CardHeader
        title="Automatic deployments"
        description="Deploy whenever a commit is pushed to the tracked branch."
      />
      <CardBody className="space-y-4">
        <Switch
          id="auto-deploy"
          checked={environment.autoDeployEnabled}
          disabled={!canManage || updateEnvironment.isPending}
          onCheckedChange={(checked) =>
            updateEnvironment.mutate(
              { environmentId: environment.id, autoDeployEnabled: checked },
              {
                onSuccess: () =>
                  toast.success(checked ? 'Auto deploy enabled' : 'Auto deploy disabled'),
                onError: (error) => toast.error('Could not update the environment', error),
              },
            )
          }
          label={
            <span className="flex items-center gap-2">
              Deploy on push to <Badge className="font-mono">{environment.branch}</Badge>
            </span>
          }
          description="Requires a GitHub webhook pointing at this server with a matching secret."
        />

        <InlineNotice>
          Add a webhook in your repository settings: payload URL{' '}
          <code className="font-mono">{'{PUBLIC_BACKEND_URL}'}/api/v1/webhooks/github</code>, content type{' '}
          <code className="font-mono">application/json</code>, secret equal to{' '}
          <code className="font-mono">GITHUB_WEBHOOK_SECRET</code>, events: push and pull request.
          Unsigned webhooks are rejected.
        </InlineNotice>

        <dl className="grid gap-4 border-t border-border-subtle pt-4 sm:grid-cols-3">
          <div>
            <dt className="text-[11px] uppercase tracking-wider text-content-muted">Environment</dt>
            <dd className="mt-1 text-[13px] text-content-primary">
              {environment.name} ({environment.type.toLowerCase()})
            </dd>
          </div>
          <div>
            <dt className="text-[11px] uppercase tracking-wider text-content-muted">Branch</dt>
            <dd className="mt-1 font-mono text-[12px] text-content-primary">{environment.branch}</dd>
          </div>
          <div>
            <dt className="text-[11px] uppercase tracking-wider text-content-muted">Created</dt>
            <dd className="mt-1 text-[13px] text-content-primary">
              {absoluteTime(environment.createdAt)}
            </dd>
          </div>
        </dl>
      </CardBody>
    </Card>
  );
}
