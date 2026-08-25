import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { queryKeys } from '@/api/keys';
import { useDeploymentLogs } from '@/api/deployments';
import { subscribeToDeployment, type SocketState } from '@/lib/deployment-socket';
import type { Deployment, DeploymentEvent, LogEntry } from '@/types/api';

interface StreamResult {
  logs: LogEntry[];
  socketState: SocketState;
  liveStatus: Deployment['status'] | null;
  clear: () => void;
  initialLoading: boolean;
}

/**
 * Combines the historical log page with the live WebSocket stream.
 *
 * Two mechanisms are needed because neither alone is sufficient: a page load must show what already
 * happened, and an in-flight deployment must show what happens next. Merging them by sequence number makes
 * the boundary invisible and duplicate-free even if a line arrives over both paths.
 *
 * Status and step events also invalidate the deployment query, so the header, timeline and analysis card
 * update from the same stream that feeds the log view.
 */
export function useDeploymentStream(
  deploymentId: string | undefined,
  options: { enabled?: boolean } = {},
): StreamResult {
  const enabled = options.enabled ?? true;
  const queryClient = useQueryClient();
  const { data: initialLogs, isLoading } = useDeploymentLogs(deploymentId);

  const [liveLogs, setLiveLogs] = useState<LogEntry[]>([]);
  const [socketState, setSocketState] = useState<SocketState>('closed');
  const [liveStatus, setLiveStatus] = useState<Deployment['status'] | null>(null);
  const [cleared, setCleared] = useState(false);
  const seenSequences = useRef(new Set<number>());

  useEffect(() => {
    setLiveLogs([]);
    setLiveStatus(null);
    setCleared(false);
    seenSequences.current = new Set();
  }, [deploymentId]);

  useEffect(() => {
    if (!deploymentId || !enabled) {
      return;
    }

    const unsubscribe = subscribeToDeployment(deploymentId, {
      onStateChange: setSocketState,
      onEvent: (event: DeploymentEvent) => {
        switch (event.type) {
          case 'LOG': {
            if (seenSequences.current.has(event.sequence)) {
              return;
            }
            seenSequences.current.add(event.sequence);
            setLiveLogs((current) => [
              ...current,
              {
                sequence: event.sequence,
                timestamp: event.timestamp,
                level: event.level,
                source: event.source,
                message: event.message,
              },
            ]);
            break;
          }
          case 'STATUS_CHANGED': {
            setLiveStatus(event.status);
            void queryClient.invalidateQueries({ queryKey: queryKeys.deployment(deploymentId) });
            break;
          }
          case 'STEP_UPDATED': {
            void queryClient.invalidateQueries({ queryKey: queryKeys.deployment(deploymentId) });
            break;
          }
          case 'DEPLOYMENT_READY': {
            setLiveStatus('READY');
            void queryClient.invalidateQueries({ queryKey: queryKeys.deployment(deploymentId) });
            void queryClient.invalidateQueries({ queryKey: ['projects'] });
            break;
          }
          case 'DEPLOYMENT_FAILED': {
            setLiveStatus('FAILED');
            void queryClient.invalidateQueries({ queryKey: queryKeys.deployment(deploymentId) });
            void queryClient.invalidateQueries({ queryKey: ['projects'] });
            break;
          }
          case 'ANALYSIS_READY': {
            void queryClient.invalidateQueries({
              queryKey: queryKeys.deploymentAnalysis(deploymentId),
            });
            break;
          }
          case 'METRICS': {
            void queryClient.invalidateQueries({
              queryKey: ['deployments', deploymentId, 'metrics'],
            });
            break;
          }
          default:
            break;
        }
      },
    });

    return unsubscribe;
  }, [deploymentId, enabled, queryClient]);

  const logs = useMemo(() => {
    if (cleared) {
      return liveLogs;
    }
    const merged = new Map<number, LogEntry>();
    (initialLogs?.items ?? []).forEach((entry) => merged.set(entry.sequence, entry));
    liveLogs.forEach((entry) => merged.set(entry.sequence, entry));
    return Array.from(merged.values()).sort((a, b) => a.sequence - b.sequence);
  }, [initialLogs, liveLogs, cleared]);

  const clear = useCallback(() => {
    setCleared(true);
    setLiveLogs([]);
  }, []);

  return { logs, socketState, liveStatus, clear, initialLoading: isLoading };
}
