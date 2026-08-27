import {
  Activity,
  Bell,
  Boxes,
  ChevronDown,
  LayoutDashboard,
  LogOut,
  Menu,
  Rocket,
  Settings,
  User as UserIcon,
  Users,
  X,
} from 'lucide-react';
import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  useMarkNotificationsRead,
  useNotifications,
  usePlatformHealth,
  useUnreadNotificationCount,
  useWorkspaces,
} from '@/api/platform';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownLabel,
  DropdownSeparator,
  DropdownTrigger,
} from '@/components/ui/overlay';
import { EmptyState } from '@/components/ui/feedback';
import { BrandLogo } from '@/components/BrandLogo';
import { ThemeToggle } from '@/components/ui/theme-toggle';
import { PlatformStatusBanner } from '@/components/layout/PlatformStatusBanner';
import { useAuthStore } from '@/stores/auth-store';
import { cn, relativeTime } from '@/lib/utils';

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Overview', icon: LayoutDashboard },
  { to: '/projects', label: 'Projects', icon: Boxes },
  { to: '/deployments', label: 'Deployments', icon: Rocket },
  { to: '/activity', label: 'Activity', icon: Activity },
];

function navLinkClass(isActive: boolean) {
  return cn(
    'flex items-center gap-2.5 rounded-md px-3 py-2.5 text-[14px] font-medium transition-colors',
    isActive
      ? 'bg-accent-soft text-content-primary'
      : 'text-content-secondary hover:bg-surface-hover hover:text-content-primary',
  );
}

export function AppShell() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  return (
    <div className="min-h-screen bg-canvas text-content-primary">
      <div className="flex">
        {/* Desktop sidebar */}
        <aside className="sticky top-0 hidden h-screen w-[280px] shrink-0 flex-col border-r border-border-subtle bg-surface lg:flex">
          <SidebarContent />
        </aside>

        {/* Mobile drawer */}
        {mobileNavOpen ? (
          <div className="fixed inset-0 z-40 lg:hidden">
            <div
              className="absolute inset-0 bg-ink-deep/30 backdrop-blur-[2px]"
              onClick={() => setMobileNavOpen(false)}
              aria-hidden="true"
            />
            <aside className="absolute left-0 top-0 flex h-full w-[280px] flex-col border-r border-border-subtle bg-surface shadow-xl">
              <SidebarContent onNavigate={() => setMobileNavOpen(false)} />
            </aside>
          </div>
        ) : null}

        <div className="flex min-w-0 flex-1 flex-col">
          <TopBar onToggleNav={() => setMobileNavOpen((open) => !open)} navOpen={mobileNavOpen} />
          <PlatformStatusBanner />
          <main className="min-w-0 flex-1 px-6 py-8 md:px-12">
            <div className="workspace-view">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { data: workspaces } = useWorkspaces();
  const primaryWorkspace = workspaces?.[0];

  return (
    <>
      <div className="flex h-[72px] items-center px-6">
        <BrandLogo className="h-9 max-w-[165px]" />
      </div>

      <nav className="flex-1 space-y-6 overflow-y-auto px-3 py-4">
        <div className="space-y-0.5">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={onNavigate}
              className={({ isActive }) => navLinkClass(isActive)}
            >
              <item.icon className="h-4 w-4" aria-hidden="true" />
              {item.label}
            </NavLink>
          ))}
        </div>

        {primaryWorkspace ? (
          <div>
            <p className="px-3 pb-2 text-[11px] font-medium uppercase tracking-wider text-content-muted">
              Workspace
            </p>
            <div className="space-y-0.5">
              <NavLink
                to={`/workspaces/${primaryWorkspace.slug}/members`}
                onClick={onNavigate}
                className={({ isActive }) => navLinkClass(isActive)}
              >
                <Users className="h-4 w-4" aria-hidden="true" />
                Members
                <Badge className="ml-auto">{primaryWorkspace.memberCount}</Badge>
              </NavLink>
              <NavLink
                to={`/workspaces/${primaryWorkspace.slug}/settings`}
                onClick={onNavigate}
                className={({ isActive }) => navLinkClass(isActive)}
              >
                <Settings className="h-4 w-4" aria-hidden="true" />
                Settings
              </NavLink>
            </div>
          </div>
        ) : null}
      </nav>

      <div className="border-t border-border-subtle p-3">
        <NavLink
          to="/account"
          onClick={onNavigate}
          className={({ isActive }) => navLinkClass(isActive)}
        >
          <UserIcon className="h-4 w-4" aria-hidden="true" />
          Account
        </NavLink>
      </div>
    </>
  );
}

function TopBar({ onToggleNav, navOpen }: { onToggleNav: () => void; navOpen: boolean }) {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  const { data: health } = usePlatformHealth();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="sticky top-0 z-30 flex h-[59px] items-center gap-3 border-b border-border-subtle bg-surface-glass px-6 backdrop-blur-sm md:px-12">
      <Button
        variant="ghost"
        size="icon"
        className="lg:hidden"
        onClick={onToggleNav}
        aria-label={navOpen ? 'Close navigation' : 'Open navigation'}
        aria-expanded={navOpen}
      >
        {navOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
      </Button>

      <div className="ml-auto flex items-center gap-2">
        {health ? (
          <Badge tone={health.status === 'UP' ? 'success' : 'warning'} className="hidden sm:inline-flex">
            {health.status === 'UP' ? 'Healthy' : 'Degraded'}
          </Badge>
        ) : null}

        <ThemeToggle />

        <NotificationMenu />

        <Dropdown>
          <DropdownTrigger asChild>
            <button
              type="button"
              className="flex items-center gap-2 rounded-md px-2 py-1.5 text-[13px] text-content-secondary transition-colors hover:bg-surface-hover hover:text-content-primary"
            >
              {user?.avatarUrl ? (
                <img
                  src={user.avatarUrl}
                  alt=""
                  className="h-6 w-6 rounded-full border border-border-subtle"
                />
              ) : (
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-accent-soft text-[11px] font-semibold text-content-primary">
                  {(user?.name ?? '?').charAt(0).toUpperCase()}
                </span>
              )}
              <span className="hidden max-w-32 truncate sm:inline">{user?.name}</span>
              <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </DropdownTrigger>
          <DropdownContent>
            <DropdownLabel>{user?.githubUsername}</DropdownLabel>
            <DropdownSeparator />
            <DropdownItem onSelect={() => navigate('/account')}>
              <UserIcon className="h-3.5 w-3.5" />
              Account
            </DropdownItem>
            <DropdownSeparator />
            <DropdownItem destructive onSelect={handleLogout}>
              <LogOut className="h-3.5 w-3.5" />
              Sign out
            </DropdownItem>
          </DropdownContent>
        </Dropdown>
      </div>
    </header>
  );
}

function NotificationMenu() {
  const { data: unread } = useUnreadNotificationCount();
  const { data: notifications } = useNotifications(0);
  const markAllRead = useMarkNotificationsRead();
  const navigate = useNavigate();
  const count = unread?.count ?? 0;

  return (
    <Dropdown>
      <DropdownTrigger asChild>
        <button
          type="button"
          className="relative rounded-md p-2 text-content-secondary transition-colors hover:bg-surface-hover hover:text-content-primary"
          aria-label={count > 0 ? `${count} unread notifications` : 'Notifications'}
        >
          <Bell className="h-4 w-4" />
          {count > 0 ? (
            <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-accent-deep px-1 text-[10px] font-semibold text-ink-deep">
              {count > 9 ? '9+' : count}
            </span>
          ) : null}
        </button>
      </DropdownTrigger>
      <DropdownContent className="w-80 p-0">
        <div className="flex items-center justify-between border-b border-border-subtle px-4 py-3">
          <p className="text-[14px] font-medium text-content-primary">Notifications</p>
          {count > 0 ? (
            <Button variant="link" size="sm" onClick={() => markAllRead.mutate()}>
              Mark all read
            </Button>
          ) : null}
        </div>
        <div className="max-h-80 overflow-y-auto">
          {!notifications || notifications.items.length === 0 ? (
            <EmptyState
              title="Nothing yet"
              description="Deployment results and health alerts appear here."
              className="py-8"
            />
          ) : (
            notifications.items.map((notification) => (
              <button
                key={notification.id}
                type="button"
                onClick={() =>
                  notification.deploymentId
                    ? navigate(`/deployments/${notification.deploymentId}`)
                    : undefined
                }
                className={cn(
                  'block w-full border-b border-border-subtle px-4 py-3 text-left transition-colors last:border-0 hover:bg-surface-hover',
                  !notification.read && 'bg-accent-soft',
                )}
              >
                <p className="text-[13px] font-medium text-content-primary">{notification.title}</p>
                {notification.message ? (
                  <p className="mt-0.5 line-clamp-2 text-[12px] text-content-secondary">
                    {notification.message}
                  </p>
                ) : null}
                <p className="mt-1 text-[11px] text-content-muted">
                  {relativeTime(notification.createdAt)}
                </p>
              </button>
            ))
          )}
        </div>
      </DropdownContent>
    </Dropdown>
  );
}
