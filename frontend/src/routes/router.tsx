import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { ProjectLayout } from '@/components/layout/ProjectLayout';
import { Spinner } from '@/components/ui/feedback';
import { AccountPage } from '@/features/account/AccountPage';
import { ActivityPage } from '@/features/activity/ActivityPage';
import { AiPage } from '@/features/ai/AiPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { AllDeploymentsPage } from '@/features/deployments/AllDeploymentsPage';
import { DeploymentDetailPage } from '@/features/deployments/DeploymentDetailPage';
import { DeploymentsPage } from '@/features/deployments/DeploymentsPage';
import { EnvironmentPage } from '@/features/environment/EnvironmentPage';
import { ProjectLogsPage } from '@/features/logs/ProjectLogsPage';
import { MonitoringPage } from '@/features/monitoring/MonitoringPage';
import { ImportProjectPage } from '@/features/projects/ImportProjectPage';
import { ProjectOverviewPage } from '@/features/projects/ProjectOverviewPage';
import { ProjectsListPage } from '@/features/projects/ProjectsListPage';
import { ProjectSettingsPage } from '@/features/settings/ProjectSettingsPage';
import { WorkspaceMembersPage, WorkspaceSettingsPage } from '@/features/workspace/WorkspacePages';
import { useAuthStore } from '@/stores/auth-store';

/**
 * Route guard.
 *
 * Waits for the boot-time session restore before deciding, so a page refresh on a protected route does not
 * flash the login screen. The intended destination is preserved and returned to after sign-in.
 */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const status = useAuthStore((state) => state.status);
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spinner className="h-5 w-5" />
      </div>
    );
  }

  if (status === 'anonymous') {
    const returnTo = `${location.pathname}${location.search}`;
    return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />;
  }

  return <>{children}</>;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
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

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  );
}
