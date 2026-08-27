import { GithubIcon } from '@/components/icons/GithubIcon';
import { Shield, UserMinus, UserPlus, Users } from 'lucide-react';
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  useAddWorkspaceMember,
  useRemoveMember,
  useUpdateMemberRole,
  useWorkspaceMembers,
  useWorkspaces,
} from '@/api/platform';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader, MetaItem } from '@/components/ui/card';
import { EmptyState, InlineNotice, SkeletonRows } from '@/components/ui/feedback';
import { Input, Select } from '@/components/ui/form';
import { PageHeader } from '@/components/ui/page';
import { useToast } from '@/components/ui/toast';
import { absoluteTime, humanizeEnum, relativeTime } from '@/lib/utils';
import type { WorkspaceRole } from '@/types/api';

const ROLE_DESCRIPTIONS: Record<WorkspaceRole, string> = {
  OWNER: 'Full control, including workspace settings and membership.',
  ADMIN: 'Manages projects, environments, variables, deployments and rollbacks.',
  DEVELOPER: 'Deploys, redeploys, cancels and reads everything. Cannot change configuration.',
  VIEWER: 'Read only.',
};

export function WorkspaceMembersPage() {
  const { workspaceSlug } = useParams<{ workspaceSlug: string }>();
  const { data: workspaces } = useWorkspaces();
  const workspace = workspaces?.find((item) => item.slug === workspaceSlug) ?? workspaces?.[0];
  const { data: members, isLoading } = useWorkspaceMembers(workspace?.id);
  const addMember = useAddWorkspaceMember(workspace?.id ?? '');
  const updateRole = useUpdateMemberRole(workspace?.id ?? '');
  const removeMember = useRemoveMember(workspace?.id ?? '');
  const toast = useToast();

  const [githubUsername, setGithubUsername] = useState('');
  const [role, setRole] = useState<WorkspaceRole>('DEVELOPER');

  const canManage = workspace?.permissions.includes('MANAGE_MEMBERS') ?? false;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Members"
        description="Roles are enforced on the server for every request. The UI only hides what your role cannot do."
      />

      <Card>
        <CardHeader
          title={workspace?.name ?? 'Workspace'}
          description={`${members?.length ?? 0} members`}
          icon={<Users className="h-4 w-4" />}
        />
        <CardBody className="space-y-4">
          {isLoading ? (
            <SkeletonRows rows={3} />
          ) : !members || members.length === 0 ? (
            <EmptyState icon={Users} title="No members" className="py-8" />
          ) : (
            <ul className="divide-y divide-border-subtle overflow-hidden rounded-lg border border-border-subtle">
              {members.map((member) => (
                <li key={member.id} className="flex items-center gap-3 px-3 py-2.5">
                  {member.user.avatarUrl ? (
                    <img
                      src={member.user.avatarUrl}
                      alt=""
                      className="h-7 w-7 rounded-full border border-border-subtle"
                    />
                  ) : (
                    <span className="flex h-7 w-7 items-center justify-center rounded-full bg-accent-soft text-[11px] font-semibold">
                      {member.user.name.charAt(0).toUpperCase()}
                    </span>
                  )}
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[13px] text-content-primary">{member.user.name}</p>
                    <p className="truncate text-[11px] text-content-muted">
                      @{member.user.githubUsername} · joined {relativeTime(member.joinedAt)}
                    </p>
                  </div>

                  {canManage && member.role !== 'OWNER' ? (
                    <Select
                      value={member.role}
                      aria-label={`Role for ${member.user.name}`}
                      className="h-8 w-32 text-[12px]"
                      onChange={(event) =>
                        updateRole.mutate(
                          { memberId: member.id, role: event.target.value },
                          {
                            onSuccess: () => toast.success(`${member.user.name} is now ${event.target.value}`),
                            onError: (error) => toast.error('Could not change the role', error),
                          },
                        )
                      }
                    >
                      <option value="ADMIN">Admin</option>
                      <option value="DEVELOPER">Developer</option>
                      <option value="VIEWER">Viewer</option>
                    </Select>
                  ) : (
                    <Badge tone={member.role === 'OWNER' ? 'accent' : 'neutral'}>
                      {humanizeEnum(member.role)}
                    </Badge>
                  )}

                  {canManage && member.role !== 'OWNER' ? (
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Remove ${member.user.name}`}
                      onClick={() =>
                        removeMember.mutate(member.id, {
                          onSuccess: () => toast.success(`${member.user.name} removed`),
                          onError: (error) => toast.error('Could not remove the member', error),
                        })
                      }
                    >
                      <UserMinus className="h-3.5 w-3.5" />
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}

          {canManage ? (
            <div className="border-t border-border-subtle mt-5 pt-7">
              <div className="grid grid-cols-1 gap-x-4 gap-y-3 lg:grid-cols-[minmax(0,1fr)_240px_120px] lg:items-end">
                {/* Username field */}
                <div className="min-w-0">
                  <label htmlFor="invite-username" className="mb-2 block text-[12px] font-medium text-content-secondary">
                    GitHub username
                  </label>
                  <Input
                    id="invite-username"
                    value={githubUsername}
                    onChange={(event) => setGithubUsername(event.target.value)}
                    placeholder="octocat"
                    className="h-[48px] font-mono"
                  />
                </div>

                {/* Role field */}
                <div>
                  <label htmlFor="invite-role" className="mb-2 block text-[12px] font-medium text-content-secondary">
                    Role
                  </label>
                  <Select
                    id="invite-role"
                    value={role}
                    onChange={(event) => setRole(event.target.value as WorkspaceRole)}
                    className="h-[48px]"
                  >
                    <option value="ADMIN">Admin</option>
                    <option value="DEVELOPER">Developer</option>
                    <option value="VIEWER">Viewer</option>
                  </Select>
                </div>

                {/* Add button — self-aligns to end via grid items-end */}
                <Button
                  variant="primary"
                  loading={addMember.isPending}
                  disabled={!githubUsername.trim()}
                  className="h-[48px] w-full"
                  onClick={() =>
                    addMember.mutate(
                      { githubUsername: githubUsername.trim(), role },
                      {
                        onSuccess: (member) => {
                          toast.success(`${member.user.name} added as ${role}`);
                          setGithubUsername('');
                        },
                        onError: (error) => toast.error('Could not add the member', error),
                      },
                    )
                  }
                >
                  <UserPlus className="h-3.5 w-3.5" aria-hidden="true" />
                  Add
                </Button>

                {/* Helper text — separate grid row, pinned under username column */}
                <p className="text-[12px] leading-relaxed text-content-muted lg:col-start-1 lg:col-end-2">
                  The person must have signed in to DeployLane at least once.
                </p>
              </div>
            </div>
          ) : (
            <InlineNotice>Only the workspace owner can change membership.</InlineNotice>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="What each role can do" icon={<Shield className="h-4 w-4" />} />
        <CardBody className="space-y-4">
          {(Object.keys(ROLE_DESCRIPTIONS) as WorkspaceRole[]).map((key) => (
            <div key={key} className="grid grid-cols-1 gap-x-4 gap-y-1 sm:grid-cols-[96px_minmax(0,1fr)] sm:items-start">
              <div>
                <Badge tone={key === 'OWNER' ? 'accent' : 'neutral'}>
                  {humanizeEnum(key)}
                </Badge>
              </div>
              <p className="text-[13px] leading-[1.5] text-content-secondary">
                {ROLE_DESCRIPTIONS[key]}
              </p>
            </div>
          ))}
        </CardBody>
      </Card>
    </div>
  );
}

export function WorkspaceSettingsPage() {
  const { workspaceSlug } = useParams<{ workspaceSlug: string }>();
  const { data: workspaces, isLoading } = useWorkspaces();
  const workspace = workspaces?.find((item) => item.slug === workspaceSlug) ?? workspaces?.[0];

  if (isLoading) {
    return <SkeletonRows rows={3} />;
  }

  if (!workspace) {
    return (
      <Card>
        <EmptyState icon={GithubIcon} title="No workspace found" />
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Workspace settings" description={workspace.name} />

      <Card>
        <CardHeader title="Details" />
        <CardBody>
          <dl className="grid gap-4 sm:grid-cols-2">
            <MetaItem label="Name" value={workspace.name} />
            <MetaItem label="Slug" value={workspace.slug} mono />
            <MetaItem label="Your role" value={humanizeEnum(workspace.role)} />
            <MetaItem label="Members" value={workspace.memberCount} />
            <MetaItem label="Created" value={absoluteTime(workspace.createdAt)} />
          </dl>
        </CardBody>
      </Card>

      <Card>
        <CardHeader
          title="Your permissions"
          description="Resolved from your role on every request, never cached in a token."
        />
        <CardBody>
          <div className="flex flex-wrap gap-1.5">
            {workspace.permissions.map((permission) => (
              <Badge key={permission} className="font-mono text-[10px]">
                {permission}
              </Badge>
            ))}
          </div>
        </CardBody>
      </Card>
    </div>
  );
}
