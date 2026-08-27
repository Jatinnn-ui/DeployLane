import { Activity, Cpu, HardDrive, RefreshCw, RotateCcw, Timer } from 'lucide-react';
import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useDeploymentMetrics } from '@/api/deployments';
import { useProjectHealth } from '@/api/projects';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { EmptyState, InlineNotice, Skeleton } from '@/components/ui/feedback';
import { Select } from '@/components/ui/form';
import { MetricCard } from '@/components/ui/page';
import { HealthIndicator } from '@/components/DeploymentStatusBadge';
import { useChartTheme } from '@/lib/chart-theme';
import { clockTime, formatBytes, formatPercent, formatUptime } from '@/lib/utils';
import type { Project } from '@/types/api';

const WINDOWS = [
  { value: 30, label: 'Last 30 minutes' },
  { value: 60, label: 'Last hour' },
  { value: 360, label: 'Last 6 hours' },
  { value: 1440, label: 'Last 24 hours' },
];

/**
 * Container monitoring.
 *
 * Only metrics DeployLane actually measures are shown - CPU, memory, uptime, restarts and container state,
 * sampled from the Docker engine every 30 seconds. There is deliberately no request-rate chart: nothing in
 * a single node install sits in the request path, so that number would be invented.
 */
export function MonitoringPage() {
  const { project } = useOutletContext<{ project: Project }>();
  const [windowMinutes, setWindowMinutes] = useState(60);
  const chart = useChartTheme();
  const { data: health, isLoading: healthLoading, refetch: refetchHealth } = useProjectHealth(project.id);
  const {
    data: metrics,
    isLoading: metricsLoading,
    refetch: refetchMetrics,
  } = useDeploymentMetrics(health?.deploymentId ?? undefined, windowMinutes);

  const chartData = (metrics?.history ?? []).map((sample) => ({
    time: clockTime(sample.sampledAt),
    cpu: sample.cpuPercent ?? 0,
    memoryMb: sample.memoryBytes ? Math.round(sample.memoryBytes / (1024 * 1024)) : 0,
  }));

  const memoryLimitMb = metrics?.memoryLimitBytes
    ? Math.round(metrics.memoryLimitBytes / (1024 * 1024))
    : null;

  if (!healthLoading && !health?.deploymentId) {
    return (
      <Card>
        <EmptyState
          icon={Activity}
          title="Nothing to monitor yet"
          description={health?.detail ?? 'Deploy this project to start collecting container metrics.'}
        />
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          {healthLoading ? <Skeleton className="h-5 w-32" /> : <HealthIndicator status={health?.status} />}
          {metrics ? (
            <Badge tone={metrics.live ? 'success' : 'neutral'}>
              {metrics.live ? 'live sample' : 'last known sample'}
            </Badge>
          ) : null}
        </div>
        <div className="flex items-center gap-2">
          <Select
            value={String(windowMinutes)}
            onChange={(event) => setWindowMinutes(Number(event.target.value))}
            aria-label="Time window"
            className="h-8 w-40 text-[12px]"
          >
            {WINDOWS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              void refetchHealth();
              void refetchMetrics();
            }}
          >
            <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
            Refresh
          </Button>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          icon={Cpu}
          label="CPU"
          value={formatPercent(metrics?.cpuPercent, 1)}
          hint="of one allocated core"
          loading={metricsLoading}
          valueClassName="text-lg"
        />
        <MetricCard
          icon={HardDrive}
          label="Memory"
          value={formatBytes(metrics?.memoryBytes)}
          hint={memoryLimitMb ? `limit ${memoryLimitMb} MB` : undefined}
          loading={metricsLoading}
          valueClassName="text-lg"
        />
        <MetricCard
          icon={Timer}
          label="Uptime"
          value={formatUptime(metrics?.uptimeSeconds)}
          hint={metrics?.containerStatus ?? undefined}
          loading={metricsLoading}
          valueClassName="text-lg"
        />
        <MetricCard
          icon={RotateCcw}
          label="Restarts"
          value={String(metrics?.restartCount ?? 0)}
          hint={metrics?.restartCount ? 'container restarted' : 'stable'}
          loading={metricsLoading}
          valueClassName="text-lg"
        />
      </div>

      <Card>
        <CardHeader
          title="Resource usage"
          description={`Sampled every 30 seconds · ${chartData.length} samples in this window`}
        />
        <CardBody>
          {metricsLoading ? (
            <Skeleton className="h-64 w-full" />
          ) : chartData.length < 2 ? (
            <InlineNotice>
              Not enough samples yet. The first chart appears about a minute after a deployment goes live.
            </InlineNotice>
          ) : (
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: -12 }}>
                  <defs>
                    <linearGradient id="cpuGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor={chart.primary} stopOpacity={0.16} />
                      <stop offset="100%" stopColor={chart.primary} stopOpacity={0.01} />
                    </linearGradient>
                    <linearGradient id="memoryGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor={chart.accent} stopOpacity={0.28} />
                      <stop offset="100%" stopColor={chart.accent} stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke={chart.grid} vertical={false} />
                  <XAxis
                    dataKey="time"
                    tick={{ fill: chart.tick, fontSize: 11 }}
                    stroke={chart.grid}
                    minTickGap={40}
                  />
                  <YAxis
                    yAxisId="cpu"
                    tick={{ fill: chart.tick, fontSize: 11 }}
                    stroke={chart.grid}
                    unit="%"
                  />
                  <YAxis
                    yAxisId="memory"
                    orientation="right"
                    tick={{ fill: chart.tick, fontSize: 11 }}
                    stroke={chart.grid}
                    unit="MB"
                  />
                  <ChartTooltip
                    contentStyle={{
                      background: chart.tooltipBg,
                      border: `1px solid ${chart.tooltipBorder}`,
                      borderRadius: 12,
                      fontSize: 12,
                    }}
                    labelStyle={{ color: chart.label }}
                  />
                  <Legend wrapperStyle={{ fontSize: 11, color: chart.label }} />
                  <Area
                    yAxisId="cpu"
                    type="monotone"
                    dataKey="cpu"
                    name="CPU %"
                    stroke={chart.primary}
                    strokeWidth={1.5}
                    fill="url(#cpuGradient)"
                  />
                  <Area
                    yAxisId="memory"
                    type="monotone"
                    dataKey="memoryMb"
                    name="Memory MB"
                    stroke={chart.accent}
                    strokeWidth={1.75}
                    fill="url(#memoryGradient)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Live deployment" />
        <CardBody>
          <dl className="grid gap-4 sm:grid-cols-3">
            <div>
              <dt className="text-[11px] uppercase tracking-wider text-content-muted">Deployment</dt>
              <dd className="mt-1 text-[13px] tabular-nums text-content-primary">
                #{health?.deploymentNumber ?? '-'}
              </dd>
            </div>
            <div>
              <dt className="text-[11px] uppercase tracking-wider text-content-muted">Environment</dt>
              <dd className="mt-1 text-[13px] text-content-primary">{health?.environmentName ?? '-'}</dd>
            </div>
            <div>
              <dt className="text-[11px] uppercase tracking-wider text-content-muted">URL</dt>
              <dd className="mt-1 truncate font-mono text-[12px] text-accent">
                {health?.url ? (
                  <a href={health.url} target="_blank" rel="noreferrer" className="hover:underline">
                    {health.url.replace(/^https?:\/\//, '')}
                  </a>
                ) : (
                  '-'
                )}
              </dd>
            </div>
          </dl>
        </CardBody>
      </Card>
    </div>
  );
}
