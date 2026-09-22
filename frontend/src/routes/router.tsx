import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { ProjectLayout } from '@/components/layout/ProjectLayout';
import { AppLoader } from '@/components/ui/app-loader';
import { Spinner } from '@/components/ui/feedback';
import { LandingPage } from '@/features/landing/LandingPage';
import { useAuthStore } from '@/stores/auth-store';

/**
 * Route-level code splitting.
 *
 * Every authenticated page and the heavy marketing sub-pages are loaded on demand with
 * {@link lazy}, so the initial bundle only carries the landing page and the shell. The
 * monitoring page pulls in Recharts, which is large — keeping it lazy means it is never
 * downloaded until someone actually opens a chart. LandingPage stays eager because it is
 * the first paint for most visitors.
 */
const LoginPage = lazy(() => import('@/features/auth/LoginPage').then((m) => ({ default: m.LoginPage })));
const PricingPage = lazy(() => import('@/features/landing/PricingPage').then((m) => ({ default: m.PricingPage })));
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const ProjectsListPage = lazy(() => import('@/features/projects/ProjectsListPage').then((m) => ({ default: m.ProjectsListPage })));
const ImportProjectPage = lazy(() => import('@/features/projects/ImportProjectPage').then((m) => ({ default: m.ImportProjectPage })));
const AllDeploymentsPage = lazy(() => import('@/features/deployments/AllDeploymentsPage').then((m) => ({ default: m.AllDeploymentsPage })));
const DeploymentDetailPage = lazy(() => import('@/features/deployments/DeploymentDetailPage').then((m) => ({ default: m.DeploymentDetailPage })));
const DeploymentsPage = lazy(() => import('@/features/deployments/DeploymentsPage').then((m) => ({ default: m.DeploymentsPage })));
const ActivityPage = lazy(() => import('@/features/activity/ActivityPage').then((m) => ({ default: m.ActivityPage })));
const AccountPage = lazy(() => import('@/features/account/AccountPage').then((m) => ({ default: m.AccountPage })));
const EnvironmentPage = lazy(() => import('@/features/environment/EnvironmentPage').then((m) => ({ default: m.EnvironmentPage })));
const MonitoringPage = lazy(() => import('@/features/monitoring/MonitoringPage').then((m) => ({ default: m.MonitoringPage })));
const ProjectLogsPage = lazy(() => import('@/features/logs/ProjectLogsPage').then((m) => ({ default: m.ProjectLogsPage })));
const ProjectOverviewPage = lazy(() => import('@/features/projects/ProjectOverviewPage').then((m) => ({ default: m.ProjectOverviewPage })));
const ProjectSettingsPage = lazy(() => import('@/features/settings/ProjectSettingsPage').then((m) => ({ default: m.ProjectSettingsPage })));
const AiPage = lazy(() => import('@/features/ai/AiPage').then((m) => ({ default: m.AiPage })));
const WorkspaceMembersPage = lazy(() => import('@/features/workspace/WorkspacePages').then((m) => ({ default: m.WorkspaceMembersPage })));
const WorkspaceSettingsPage = lazy(() => import('@/features/workspace/WorkspacePages').then((m) => ({ default: m.WorkspaceSettingsPage })));
const NotFoundPage = lazy(() => import('@/features/errors/NotFoundPage').then((m) => ({ default: m.NotFoundPage })));

/** Lightweight fallback shown while a lazy route chunk downloads. */
function RouteFallback() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <Spinner className="h-5 w-5" />
    </div>
  );
}

/**
 * Route guard.
 *
 * Waits for the boot-time session restore before deciding, so a page refresh on a protected route does not
 * flash the login screen. The intended destination is preserved and returned to after sign-in.
 */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const status = useAuthStore((state) => state.status);

  if (status === 'loading') {
    return <AppLoader />;
  }

  // Auth bypassed for local development — all routes are accessible without login.
  return <>{children}</>;
}

export function AppRoutes() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        {/* Public marketing page. The product itself stays behind RequireAuth below. */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/pricing" element={<PricingPage />} />
        <Route path="/login" element={<LoginPage />} />

        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/projects" element={<ProjectsListPage />} />
          <Route path="/projects/import" element={<ImportProjectPage />} />
          <Route path="/deployments" element={<AllDeploymentsPage />} />
          <Route path="/deployments/:deploymentId" element={<DeploymentDetailPage />} />
          <Route path="/activity" element={<ActivityPage />} />
          <Route path="/account" element={<AccountPage />} />
          <Route path="/workspaces/:workspaceSlug/members" element={<WorkspaceMembersPage />} />
          <Route path="/workspaces/:workspaceSlug/settings" element={<WorkspaceSettingsPage />} />

          <Route path="/projects/:projectId" element={<ProjectLayout />}>
            <Route index element={<ProjectOverviewPage />} />
            <Route path="deployments" element={<DeploymentsPage />} />
            <Route path="logs" element={<ProjectLogsPage />} />
            <Route path="monitoring" element={<MonitoringPage />} />
            <Route path="environment" element={<EnvironmentPage />} />
            <Route path="ai" element={<AiPage />} />
            <Route path="settings" element={<ProjectSettingsPage />} />
          </Route>
        </Route>

        {/* Unknown paths show a proper 404 rather than silently redirecting. */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}
