import { AlertTriangle, Save, Settings2, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router-dom';
import { useDeleteProject, useUpdateEnvironment, useUpdateProject } from '@/api/projects';
import { useBranches } from '@/api/github';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { InlineNotice } from '@/components/ui/feedback';
import { Field, Input, Select } from '@/components/ui/form';
import { Dialog, DialogClose, DialogContent, DialogTrigger } from '@/components/ui/overlay';
import { useToast } from '@/components/ui/toast';
import type { Project, ProjectStatus } from '@/types/api';

/** Build configuration, lifecycle and deletion. Every field maps to something the pipeline reads. */
export function ProjectSettingsPage() {
  const { project } = useOutletContext<{ project: Project }>();
  const navigate = useNavigate();
  const toast = useToast();
  const updateProject = useUpdateProject(project.id);
  const updateEnvironment = useUpdateEnvironment(project.id);
  const deleteProject = useDeleteProject();
  const { data: branches } = useBranches(project.repository.owner, project.repository.name);

  const canManage = project.permissions.includes('MANAGE_PROJECT');
  const canDelete = project.permissions.includes('DELETE_PROJECT');
  const productionEnvironment =
    project.environments.find((environment) => environment.type === 'PRODUCTION') ??
    project.environments[0];

  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? '');
  const [status, setStatus] = useState<ProjectStatus>(project.status);
  const [branch, setBranch] = useState(productionEnvironment?.branch ?? project.defaultBranch);
  const [rootDirectory, setRootDirectory] = useState(project.buildConfig.rootDirectory ?? '');
  const [installCommand, setInstallCommand] = useState(project.buildConfig.installCommand ?? '');
  const [buildCommand, setBuildCommand] = useState(project.buildConfig.buildCommand ?? '');
  const [startCommand, setStartCommand] = useState(project.buildConfig.startCommand ?? '');
  const [dockerfilePath, setDockerfilePath] = useState(project.buildConfig.dockerfilePath ?? '');
  const [port, setPort] = useState(String(project.buildConfig.port ?? ''));
  const [healthCheckPath, setHealthCheckPath] = useState(project.buildConfig.healthCheckPath ?? '/');
  const [confirmName, setConfirmName] = useState('');

  const save = () => {
    updateProject.mutate(
      {
        name: name.trim(),
        description: description.trim(),
        status,
        buildConfig: {
          rootDirectory: rootDirectory.trim(),
          installCommand: installCommand.trim(),
          buildCommand: buildCommand.trim(),
          startCommand: startCommand.trim(),
          dockerfilePath: dockerfilePath.trim(),
          port: port ? Number(port) : null,
          healthCheckPath: healthCheckPath.trim() || '/',
        },
      },
      {
        onSuccess: () => {
          if (productionEnvironment && branch !== productionEnvironment.branch) {
            updateEnvironment.mutate({ environmentId: productionEnvironment.id, branch });
          }
          toast.success('Settings saved', 'Applied on the next deployment.');
        },
        onError: (error) => toast.error('Could not save settings', error),
      },
    );
  };

  return (
    <div className="space-y-6">
      {!canManage ? (
        <InlineNotice tone="warning">
          Your workspace role allows viewing these settings but not changing them.
        </InlineNotice>
      ) : null}

      <Card>
        <CardHeader title="General" icon={<Settings2 className="h-4 w-4" />} />
        <CardBody className="grid gap-4 sm:grid-cols-2">
          <Field label="Project name" htmlFor="project-name">
            <Input
              id="project-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              disabled={!canManage}
            />
          </Field>
          <Field label="Status" htmlFor="project-status" hint="Paused and archived projects cannot deploy.">
            <Select
              id="project-status"
              value={status}
              onChange={(event) => setStatus(event.target.value as ProjectStatus)}
              disabled={!canManage}
            >
              <option value="ACTIVE">Active</option>
              <option value="PAUSED">Paused</option>
              <option value="ARCHIVED">Archived</option>
            </Select>
          </Field>
          <Field label="Description" htmlFor="project-description" className="sm:col-span-2">
            <Input
              id="project-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              disabled={!canManage}
            />
          </Field>
          <Field label="Tracked branch" htmlFor="project-branch" className="sm:col-span-2">
            <Select
              id="project-branch"
              value={branch}
              onChange={(event) => setBranch(event.target.value)}
              disabled={!canManage}
            >
              {(branches ?? [{ name: branch, commitSha: null, isProtected: false }]).map((item) => (
                <option key={item.name} value={item.name}>
                  {item.name}
                </option>
              ))}
            </Select>
          </Field>
        </CardBody>
      </Card>

      <Card>
        <CardHeader
          title="Build & runtime"
          description="Leave a command empty to use the framework default. Shell control characters are rejected - commit a Dockerfile for advanced builds."
        />
        <CardBody className="grid gap-4 sm:grid-cols-2">
          <Field label="Root directory" htmlFor="root-directory" hint="For monorepos, e.g. apps/web">
            <Input
              id="root-directory"
              value={rootDirectory}
              onChange={(event) => setRootDirectory(event.target.value)}
              disabled={!canManage}
              className="font-mono"
            />
          </Field>
          <Field label="Port" htmlFor="container-port">
            <Input
              id="container-port"
              type="number"
              min={1}
              max={65535}
              value={port}
              onChange={(event) => setPort(event.target.value)}
              disabled={!canManage}
            />
          </Field>
          <Field label="Install command" htmlFor="install-command" className="sm:col-span-2">
            <Input
              id="install-command"
              value={installCommand}
              onChange={(event) => setInstallCommand(event.target.value)}
              disabled={!canManage}
              className="font-mono"
            />
          </Field>
          <Field label="Build command" htmlFor="build-command" className="sm:col-span-2">
            <Input
              id="build-command"
              value={buildCommand}
              onChange={(event) => setBuildCommand(event.target.value)}
              disabled={!canManage}
              className="font-mono"
            />
          </Field>
          <Field label="Start command" htmlFor="start-command" className="sm:col-span-2">
            <Input
              id="start-command"
              value={startCommand}
              onChange={(event) => setStartCommand(event.target.value)}
              disabled={!canManage}
              className="font-mono"
            />
          </Field>
          <Field
            label="Dockerfile path"
            htmlFor="dockerfile-path"
            hint="Set this to use your own Dockerfile instead of a generated one."
          >
            <Input
              id="dockerfile-path"
              value={dockerfilePath}
              onChange={(event) => setDockerfilePath(event.target.value)}
              disabled={!canManage}
              className="font-mono"
              placeholder="Dockerfile"
            />
          </Field>
          <Field label="Health check path" htmlFor="health-path">
            <Input
              id="health-path"
              value={healthCheckPath}
              onChange={(event) => setHealthCheckPath(event.target.value)}
              disabled={!canManage}
              className="font-mono"
            />
          </Field>
        </CardBody>
      </Card>

      {canManage ? (
        <div className="flex justify-end">
          <Button variant="primary" onClick={save} loading={updateProject.isPending}>
            <Save className="h-3.5 w-3.5" aria-hidden="true" />
            Save changes
          </Button>
        </div>
      ) : null}

      {canDelete ? (
        <Card className="border-danger-border">
          <CardHeader
            title="Delete this project"
            description="Stops and removes every container, image and build directory it owns, then deletes its deployment history. This cannot be undone."
            icon={<AlertTriangle className="h-4 w-4 text-danger" />}
          />
          <CardBody>
            <Dialog>
              <DialogTrigger asChild>
                <Button variant="danger" size="sm">
                  <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                  Delete project
                </Button>
              </DialogTrigger>
              <DialogContent
                title={`Delete ${project.name}?`}
                description="Containers are stopped, images and build directories are removed, and all deployment history and logs are deleted. Your GitHub repository is not touched."
                footer={
                  <>
                    <DialogClose asChild>
                      <Button variant="ghost" size="sm">
                        Cancel
                      </Button>
                    </DialogClose>
                    <Button
                      variant="danger"
                      size="sm"
                      disabled={confirmName !== project.name}
                      loading={deleteProject.isPending}
                      onClick={() =>
                        deleteProject.mutate(project.id, {
                          onSuccess: () => {
                            toast.success(`${project.name} deleted`);
                            navigate('/dashboard');
                          },
                          onError: (error) => toast.error('Delete failed', error),
                        })
                      }
                    >
                      Delete permanently
                    </Button>
                  </>
                }
              >
                <Field
                  label={`Type "${project.name}" to confirm`}
                  htmlFor="confirm-name"
                >
                  <Input
                    id="confirm-name"
                    value={confirmName}
                    onChange={(event) => setConfirmName(event.target.value)}
                    autoFocus
                  />
                </Field>
              </DialogContent>
            </Dialog>
          </CardBody>
        </Card>
      ) : null}
    </div>
  );
}
