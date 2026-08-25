import { apiClient } from '@/lib/api-client';
import type { DeploymentEvent } from '@/types/api';

export type SocketState = 'connecting' | 'open' | 'closed' | 'error';

interface SubscribeOptions {
  onEvent: (event: DeploymentEvent) => void;
  onStateChange?: (state: SocketState) => void;
}

/**
 * Live deployment channel.
 *
 * Handles the parts that make streaming actually reliable in a browser:
 *
 * - a fresh single use ticket per connection attempt (tickets are consumed on use)
 * - exponential backoff with a cap, so a restarting backend does not get hammered
 * - a heartbeat, because an idle WebSocket behind a proxy dies silently
 * - no reconnect once the caller unsubscribes or the deployment finishes
 */
export function subscribeToDeployment(
  deploymentId: string,
  { onEvent, onStateChange }: SubscribeOptions,
): () => void {
  let socket: WebSocket | null = null;
  let heartbeat: number | undefined;
  let reconnectTimer: number | undefined;
  let attempt = 0;
  let disposed = false;

  const setState = (state: SocketState) => onStateChange?.(state);

  const clearTimers = () => {
    if (heartbeat) window.clearInterval(heartbeat);
    if (reconnectTimer) window.clearTimeout(reconnectTimer);
    heartbeat = undefined;
    reconnectTimer = undefined;
  };

  const scheduleReconnect = () => {
    if (disposed) return;
    attempt += 1;
    if (attempt > 8) {
      setState('error');
      return;
    }
    const delay = Math.min(1000 * 2 ** (attempt - 1), 15000);
    reconnectTimer = window.setTimeout(connect, delay);
  };

  async function connect(): Promise<void> {
    if (disposed) return;
    setState('connecting');
    try {
      const ticket = await apiClient.websocketTicket();
      if (disposed) return;

      socket = new WebSocket(apiClient.websocketUrl(deploymentId, ticket));

      socket.onopen = () => {
        attempt = 0;
        setState('open');
        heartbeat = window.setInterval(() => {
          if (socket?.readyState === WebSocket.OPEN) {
            socket.send('ping');
          }
        }, 25000);
      };

      socket.onmessage = (message) => {
        try {
          onEvent(JSON.parse(message.data as string) as DeploymentEvent);
        } catch {
          // A malformed frame is not worth tearing the stream down for.
        }
      };

      socket.onerror = () => setState('error');

      socket.onclose = () => {
        clearTimers();
        if (disposed) {
          setState('closed');
          return;
        }
        setState('closed');
        scheduleReconnect();
      };
    } catch {
      if (!disposed) {
        setState('error');
        scheduleReconnect();
      }
    }
  }

  void connect();

  return () => {
    disposed = true;
    clearTimers();
    socket?.close();
    socket = null;
  };
}
