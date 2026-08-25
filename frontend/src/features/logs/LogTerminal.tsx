import {
  ArrowDownToLine,
  Copy,
  Download,
  Pause,
  Play,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input, Select } from '@/components/ui/form';
import { Tooltip } from '@/components/ui/overlay';
import { useToast } from '@/components/ui/toast';
import { clockTime, cn, copyToClipboard, downloadTextFile } from '@/lib/utils';
import type { LogEntry, LogLevel, LogSource, SocketState } from '@/features/logs/types';

const LEVEL_STYLES: Record<LogLevel, string> = {
  DEBUG: 'text-content-muted',
  INFO: 'text-content-secondary',
  WARN: 'text-warning-foreground',
  ERROR: 'text-danger-foreground',
};

const SOURCE_STYLES: Record<LogSource, string> = {
  SYSTEM: 'text-terminal-system',
  GIT: 'text-terminal-git',
  BUILD: 'text-terminal-build',
  DOCKER: 'text-terminal-docker',
  APPLICATION: 'text-content-primary',
  HEALTH: 'text-terminal-health',
  AI: 'text-terminal-ai',
};

const LEVELS: LogLevel[] = ['DEBUG', 'INFO', 'WARN', 'ERROR'];
const SOURCES: LogSource[] = ['SYSTEM', 'GIT', 'BUILD', 'DOCKER', 'APPLICATION', 'HEALTH', 'AI'];

/**
 * Terminal style log viewer.
 *
 * The details that make it usable while a build is running:
 *
 * - **Auto scroll that gets out of the way.** Scrolling up pauses following automatically; a button
 *   returns you to the tail. Nothing is more annoying than a log that yanks you away mid-read.
 * - **Filtering that does not lose data.** Level and source filters and the search box only affect what is
 *   rendered; the full buffer is retained, so clearing a filter brings everything back.
 * - **Rendering stays bounded.** Only the most recent slice is in the DOM, because a Docker build can emit
 *   tens of thousands of lines and mounting all of them freezes the tab.
 */
export function LogTerminal({
  entries,
  socketState,
  onClear,
  onDownload,
  emptyMessage = 'Waiting for output...',
  className,
  maxRenderedLines = 3000,
}: {
  entries: LogEntry[];
  socketState?: SocketState;
  onClear?: () => void;
  onDownload?: () => Promise<string> | string;
  emptyMessage?: string;
  className?: string;
  maxRenderedLines?: number;
}) {
  const toast = useToast();
  const scrollRef = useRef<HTMLDivElement>(null);
  const [following, setFollowing] = useState(true);
  const [search, setSearch] = useState('');
  const [minLevel, setMinLevel] = useState<LogLevel | 'ALL'>('ALL');
  const [sourceFilter, setSourceFilter] = useState<LogSource | 'ALL'>('ALL');
  const [showTimestamps, setShowTimestamps] = useState(true);

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    const minIndex = minLevel === 'ALL' ? 0 : LEVELS.indexOf(minLevel);
    const result = entries.filter((entry) => {
      if (sourceFilter !== 'ALL' && entry.source !== sourceFilter) return false;
      if (LEVELS.indexOf(entry.level) < minIndex) return false;
      if (needle && !entry.message.toLowerCase().includes(needle)) return false;
      return true;
    });
    return result.length > maxRenderedLines ? result.slice(-maxRenderedLines) : result;
  }, [entries, search, minLevel, sourceFilter, maxRenderedLines]);

  const hiddenCount = entries.length - filtered.length;

  const scrollToBottom = useCallback(() => {
    const element = scrollRef.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }, []);

  useEffect(() => {
    if (following) {
      scrollToBottom();
    }
  }, [filtered.length, following, scrollToBottom]);

  // Leaving the bottom pauses following; returning to it resumes.
  const handleScroll = () => {
    const element = scrollRef.current;
    if (!element) return;
    const atBottom = element.scrollHeight - element.scrollTop - element.clientHeight < 40;
    if (atBottom !== following) {
      setFollowing(atBottom);
    }
  };

  const copyVisible = async () => {
    const text = filtered.map(formatLine).join('\n');
    const copied = await copyToClipboard(text);
    if (copied) {
      toast.success(`Copied ${filtered.length} lines`);
    } else {
      toast.error('Could not copy to the clipboard');
    }
  };

  const download = async () => {
    try {
      const contents = onDownload ? await onDownload() : filtered.map(formatLine).join('\n');
      downloadTextFile('deployment.log', contents);
    } catch (error) {
      toast.error('Download failed', error);
    }
  };

  const errorCount = entries.filter((entry) => entry.level === 'ERROR').length;

  return (
    <div className={cn('overflow-hidden rounded-xl border border-border-subtle bg-canvas', className)}>
      <div className="flex flex-wrap items-center gap-2 border-b border-border-subtle bg-surface px-3 py-2">
        <div className="flex items-center gap-2">
          <SocketIndicator state={socketState} />
          <span className="text-[11px] tabular-nums text-content-muted">
            {entries.length} lines
            {errorCount > 0 ? (
              <span className="ml-1.5 text-danger-foreground">· {errorCount} errors</span>
            ) : null}
          </span>
        </div>

        <div className="ml-auto flex flex-wrap items-center gap-2">
          <div className="relative">
            <Search
              className="absolute left-2.5 top-1/2 h-3 w-3 -translate-y-1/2 text-content-muted"
              aria-hidden="true"
            />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Filter"
              aria-label="Search logs"
              className="h-7 w-32 pl-7 text-[12px] sm:w-40"
            />
            {search ? (
              <button
                type="button"
                onClick={() => setSearch('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-content-muted hover:text-content-primary"
                aria-label="Clear search"
              >
                <X className="h-3 w-3" />
              </button>
            ) : null}
          </div>

          <Select
            value={minLevel}
            onChange={(event) => setMinLevel(event.target.value as LogLevel | 'ALL')}
            aria-label="Minimum level"
            className="h-7 w-24 text-[12px]"
          >
            <option value="ALL">All levels</option>
            {LEVELS.map((level) => (
              <option key={level} value={level}>
                {level}+
              </option>
            ))}
          </Select>

          <Select
            value={sourceFilter}
            onChange={(event) => setSourceFilter(event.target.value as LogSource | 'ALL')}
            aria-label="Source"
            className="h-7 w-28 text-[12px]"
          >
            <option value="ALL">All sources</option>
            {SOURCES.map((source) => (
              <option key={source} value={source}>
                {source}
              </option>
            ))}
          </Select>

          <Tooltip content={following ? 'Pause auto scroll' : 'Resume auto scroll'}>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => {
                const next = !following;
                setFollowing(next);
                if (next) scrollToBottom();
              }}
              aria-pressed={following}
              aria-label={following ? 'Pause auto scroll' : 'Resume auto scroll'}
            >
              {following ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
            </Button>
          </Tooltip>

          <Tooltip content={showTimestamps ? 'Hide timestamps' : 'Show timestamps'}>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setShowTimestamps((value) => !value)}
              aria-pressed={showTimestamps}
              aria-label="Toggle timestamps"
            >
              <span className="text-[10px] font-mono">TS</span>
            </Button>
          </Tooltip>

          <Tooltip content="Copy visible lines">
            <Button variant="ghost" size="icon" onClick={copyVisible} aria-label="Copy logs">
              <Copy className="h-3.5 w-3.5" />
            </Button>
          </Tooltip>

          <Tooltip content="Download log file">
            <Button variant="ghost" size="icon" onClick={download} aria-label="Download logs">
              <Download className="h-3.5 w-3.5" />
            </Button>
          </Tooltip>

          {onClear ? (
            <Tooltip content="Clear the view (does not delete stored logs)">
              <Button variant="ghost" size="icon" onClick={onClear} aria-label="Clear view">
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </Tooltip>
          ) : null}
        </div>
      </div>

      <div className="relative">
        <div
          ref={scrollRef}
          onScroll={handleScroll}
          className="h-[28rem] overflow-auto px-3 py-2 font-mono text-[12px] leading-[1.65]"
          role="log"
          aria-live="polite"
          aria-label="Deployment logs"
          tabIndex={0}
        >
          {filtered.length === 0 ? (
            <p className="px-1 py-8 text-center text-content-muted">
              {entries.length === 0 ? emptyMessage : 'No lines match the current filters.'}
            </p>
          ) : (
            <table className="w-full border-collapse">
              <tbody>
                {filtered.map((entry) => (
                  <tr
                    key={`${entry.sequence}-${entry.timestamp}`}
                    className={cn(
                      'align-baseline',
                      entry.level === 'ERROR' && 'bg-danger-soft/40',
                      entry.level === 'WARN' && 'bg-warning-soft/20',
                    )}
                  >
                    {showTimestamps ? (
                      <td className="w-20 select-none whitespace-nowrap pr-3 text-content-muted">
                        {clockTime(entry.timestamp)}
                      </td>
                    ) : null}
                    <td className="w-24 select-none whitespace-nowrap pr-3">
                      <span className={SOURCE_STYLES[entry.source]}>{entry.source}</span>
                    </td>
                    <td className={cn('whitespace-pre-wrap break-all', LEVEL_STYLES[entry.level])}>
                      {entry.message}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {!following ? (
          <button
            type="button"
            onClick={() => {
              setFollowing(true);
              scrollToBottom();
            }}
            className="absolute bottom-3 right-4 inline-flex items-center gap-1.5 rounded-full border border-border-strong bg-surface-raised px-3 py-1.5 text-[11px] text-content-primary shadow-lg transition-colors hover:bg-surface-hover"
          >
            <ArrowDownToLine className="h-3 w-3" aria-hidden="true" />
            Jump to latest
          </button>
        ) : null}
      </div>

      {hiddenCount > 0 ? (
        <div className="border-t border-border-subtle bg-surface px-3 py-1.5 text-[11px] text-content-muted">
          {hiddenCount} line{hiddenCount === 1 ? '' : 's'} hidden by filters
        </div>
      ) : null}
    </div>
  );
}

function SocketIndicator({ state }: { state?: SocketState }) {
  if (!state) return null;
  const tone =
    state === 'open' ? 'success' : state === 'connecting' ? 'warning' : state === 'error' ? 'danger' : 'neutral';
  const label =
    state === 'open'
      ? 'Live'
      : state === 'connecting'
        ? 'Connecting'
        : state === 'error'
          ? 'Stream unavailable'
          : 'Disconnected';
  return <Badge tone={tone}>{label}</Badge>;
}

function formatLine(entry: LogEntry): string {
  return `${entry.timestamp} ${entry.level.padEnd(5)} ${entry.source.padEnd(11)} ${entry.message}`;
}
