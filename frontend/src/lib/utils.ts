import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/** "3 minutes ago", "just now", "in 5 minutes". Compact by design for dense tables. */
export function relativeTime(value?: string | null): string {
  if (!value) return '-';
  const target = new Date(value).getTime();
  if (Number.isNaN(target)) return '-';

  const diffSeconds = Math.round((Date.now() - target) / 1000);
  const future = diffSeconds < 0;
  const seconds = Math.abs(diffSeconds);

  const units: [number, string][] = [
    [60, 'second'],
    [60, 'minute'],
    [24, 'hour'],
    [7, 'day'],
    [4.34524, 'week'],
    [12, 'month'],
    [Number.POSITIVE_INFINITY, 'year'],
  ];

  if (seconds < 10) return 'just now';

  let amount = seconds;
  let unit = 'second';
  for (const [factor, name] of units) {
    if (amount < factor) {
      unit = name;
      break;
    }
    amount = amount / factor;
    unit = name;
  }
  const rounded = Math.max(1, Math.round(amount));
  const plural = rounded === 1 ? unit : `${unit}s`;
  return future ? `in ${rounded} ${plural}` : `${rounded} ${plural} ago`;
}

export function absoluteTime(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

/** HH:MM:SS, used by the log terminal where alignment matters more than locale. */
export function clockTime(value?: string | null): string {
  if (!value) return '--:--:--';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '--:--:--';
  return [date.getHours(), date.getMinutes(), date.getSeconds()]
    .map((part) => String(part).padStart(2, '0'))
    .join(':');
}

/** "42s", "3m 12s", "1h 04m". Null-safe because durations are absent until a deployment finishes. */
export function formatDuration(ms?: number | null): string {
  if (ms === null || ms === undefined) return '-';
  if (ms < 1000) return `${ms}ms`;
  const totalSeconds = Math.floor(ms / 1000);
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes < 60) return `${minutes}m ${String(seconds).padStart(2, '0')}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${String(minutes % 60).padStart(2, '0')}m`;
}

export function formatUptime(seconds?: number | null): string {
  if (seconds === null || seconds === undefined) return '-';
  return formatDuration(seconds * 1000);
}

export function formatBytes(bytes?: number | null, decimals = 0): string {
  if (bytes === null || bytes === undefined) return '-';
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, exponent);
  return `${value.toFixed(exponent === 0 ? 0 : decimals)} ${units[exponent]}`;
}

export function formatPercent(value?: number | null, decimals = 0): string {
  if (value === null || value === undefined) return '-';
  return `${value.toFixed(decimals)}%`;
}

export function truncate(value: string | null | undefined, max: number): string {
  if (!value) return '';
  return value.length <= max ? value : `${value.slice(0, max - 1)}…`;
}

/** Title-cases an enum constant: DEPLOYMENT_FAILED -> "Deployment failed". */
export function humanizeEnum(value?: string | null): string {
  if (!value) return '';
  const lower = value.toLowerCase().replace(/_/g, ' ');
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

export function downloadTextFile(filename: string, contents: string): void {
  const blob = new Blob([contents], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
