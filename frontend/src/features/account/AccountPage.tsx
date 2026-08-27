import { GithubIcon } from '@/components/icons/GithubIcon';
import { LogOut, ShieldCheck, Unplug } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAccount, useDisconnectGithub, useGithubConnection } from '@/api/platform';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader, MetaItem } from '@/components/ui/card';
import { InlineNotice, SkeletonRows } from '@/components/ui/feedback';
import { Dialog, DialogClose, DialogContent, DialogTrigger } from '@/components/ui/overlay';
import { PageHeader } from '@/components/ui/page';
import { useToast } from '@/components/ui/toast';
import { useAuthStore } from '@/stores/auth-store';
import { absoluteTime } from '@/lib/utils';

/** Account details, GitHub grant state and session control. */
export function AccountPage() {
  const { data: account, isLoading } = useAccount();
  const { data: github } = useGithubConnection();
  const disconnect = useDisconnectGithub();
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  const toast = useToast();

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader
        title="Account"
        description="Your GitHub identity and the grant DeployLane uses on your behalf."
      />

      <Card>
        <CardHeader title="Profile" />
        <CardBody>
          {isLoading ? (
            <SkeletonRows rows={2} />
          ) : (
            <div className="flex items-start gap-4">
              {account?.avatarUrl ? (
                <img
                  src={account.avatarUrl}
                  alt=""
                  className="h-14 w-14 rounded-xl border border-border-subtle"
                />
              ) : null}
              <dl className="grid flex-1 gap-4 sm:grid-cols-2">
                <MetaItem label="Name" value={account?.name} />
                <MetaItem label="GitHub" value={`@${account?.githubUsername ?? ''}`} mono />
                <MetaItem label="Email" value={account?.email ?? 'not shared by GitHub'} />
                <MetaItem label="Member since" value={absoluteTime(account?.createdAt)} />
              </dl>
            </div>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader
          title="GitHub connection"
          icon={<GithubIcon className="h-4 w-4" />}
          actions={
            github?.connected ? <Badge tone="success">connected</Badge> : <Badge tone="warning">not connected</Badge>
          }
        />
        <CardBody className="space-y-4">
          <dl className="grid gap-4 sm:grid-cols-2">
            <MetaItem label="Granted scopes" value={github?.scopes ?? '-'} mono />
            <MetaItem
              label="Private repositories"
              value={github?.hasRepoScope ? 'accessible' : 'not accessible'}
            />
            <MetaItem label="Last updated" value={absoluteTime(github?.connectedAt)} />
          </dl>

          {!github?.hasRepoScope ? (
            <InlineNotice tone="warning">
              The <code className="font-mono">repo</code> scope was not granted, so private repositories
              cannot be cloned. Sign out and back in to grant it.
            </InlineNotice>
          ) : (
            <InlineNotice>
              <span className="flex items-start gap-2">
                <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                The access token is encrypted with AES-256-GCM before it is stored and is never sent to
                your browser.
              </span>
            </InlineNotice>
          )}

          <div className="flex flex-wrap gap-2 border-t border-border-subtle pt-4">
            <Dialog>
              <DialogTrigger asChild>
                <Button variant="secondary" size="sm" disabled={!github?.connected}>
                  <Unplug className="h-3.5 w-3.5" aria-hidden="true" />
                  Disconnect GitHub
                </Button>
              </DialogTrigger>
              <DialogContent
                title="Disconnect GitHub?"
                description="The stored token is deleted and every session is revoked, because DeployLane cannot clone or import anything without a grant. Existing deployments keep running."
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
                      loading={disconnect.isPending}
                      onClick={() =>
                        disconnect.mutate(undefined, {
                          onSuccess: async () => {
                            toast.info('GitHub disconnected', 'Sign in again to reconnect.');
                            await logout();
                            navigate('/login', { replace: true });
                          },
                          onError: (error) => toast.error('Could not disconnect', error),
                        })
                      }
                    >
                      Disconnect
                    </Button>
                  </>
                }
              />
            </Dialog>

            <Button
              variant="ghost"
              size="sm"
              onClick={async () => {
                await logout();
                navigate('/login', { replace: true });
              }}
            >
              <LogOut className="h-3.5 w-3.5" aria-hidden="true" />
              Sign out
            </Button>
          </div>
        </CardBody>
      </Card>
    </div>
  );
}
